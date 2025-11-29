package com.speedlimit

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Provides speed limit data from OpenStreetMap via the Overpass API.
 * Uses smart caching to minimize API calls while ensuring accuracy.
 * 
 * Caching Strategy (TUNED FOR ACCURACY):
 * - Re-query every 150m (~10 seconds at 30mph)
 * - Detect direction changes >35° (indicates turning onto new road)
 * - Maximum 400m before forced re-query even on same OSM way
 * - 1-minute maximum cache age
 * - Balances API usage with real-time accuracy
 * 
 * Rate Limit Protection:
 * - Detects 429/503/504 responses
 * - Automatic exponential backoff
 * - Broadcasts alert for user notification
 */
class SpeedLimitProvider(private val context: Context) {

    companion object {
        const val TAG = "SpeedLimitProvider"
        
        // Overpass API endpoint (public server)
        const val OVERPASS_API_URL = "https://overpass-api.de/api/interpreter"
        
        // Search radius in meters
        const val SEARCH_RADIUS_METERS = 50
        
        // Smart caching parameters - TUNED FOR ACCURACY
        const val CACHE_DURATION_MS = 60_000L       // 1 minute max cache age
        const val REQUERY_DISTANCE_METERS = 150.0   // Re-query after 150m (~10s at 30mph)
        const val SAME_WAY_MAX_DISTANCE = 400.0     // Trust same way ID for max 400m
        const val BEARING_CHANGE_THRESHOLD = 35.0   // Degrees - force re-query if direction changes
        
        // Rate limit handling
        const val INITIAL_BACKOFF_MS = 30_000L      // 30 seconds initial backoff
        const val MAX_BACKOFF_MS = 300_000L         // 5 minutes max backoff
        const val BACKOFF_MULTIPLIER = 2.0
        
        // Broadcast action for rate limit alerts
        const val ACTION_RATE_LIMIT_ALERT = "com.speedlimit.RATE_LIMIT_ALERT"
        const val EXTRA_RATE_LIMIT_TYPE = "rate_limit_type"
        const val EXTRA_BACKOFF_SECONDS = "backoff_seconds"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Enhanced cache with way ID and bearing tracking
    private var cachedSpeedLimit: Int? = null
    private var cachedWayId: Long? = null
    private var cachedLat: Double = 0.0
    private var cachedLon: Double = 0.0
    private var cacheTimestamp: Long = 0
    private var lastBearing: Double = -1.0  // Track direction of travel
    private var totalQueriesThisSession: Int = 0
    private var cacheHitsThisSession: Int = 0
    
    // Rate limit state
    private var isRateLimited: Boolean = false
    private var rateLimitEndTime: Long = 0
    private var currentBackoffMs: Long = INITIAL_BACKOFF_MS
    private var rateLimitEventsThisSession: Int = 0
    private var consecutiveErrors: Int = 0
    
    // Current country (for display purposes)
    var currentCountryCode: String = "GB"
        private set
    
    // Current way ID (for crowdsourcing)
    val currentWayId: Long
        get() = cachedWayId ?: -1L

    /**
     * Get the speed limit for a given location.
     * Returns the speed limit in mph, or null if not found.
     * 
     * @param lat Latitude
     * @param lon Longitude  
     * @param bearing Current direction of travel in degrees (0-360), or -1 if unknown
     */
    suspend fun getSpeedLimit(lat: Double, lon: Double, bearing: Float = -1f): Int? = withContext(Dispatchers.IO) {
        // Detect country for this location
        currentCountryCode = SpeedUnitHelper.detectCountry(context, lat, lon)
        
        // Check if we're in backoff period
        if (isInBackoffPeriod()) {
            Log.w(TAG, "In backoff period, using cache (${getRemainingBackoffSeconds()}s remaining)")
            return@withContext cachedSpeedLimit
        }
        
        // Smart cache check with bearing
        val cacheResult = checkCache(lat, lon, bearing.toDouble())
        if (cacheResult != null) {
            cacheHitsThisSession++
            Log.d(TAG, "Cache HIT: $cachedSpeedLimit mph (way: $cachedWayId) " +
                    "[hits: $cacheHitsThisSession, queries: $totalQueriesThisSession]")
            return@withContext cacheResult
        }

        try {
            totalQueriesThisSession++
            val result = queryOverpassApi(lat, lon, currentCountryCode)
            
            // Reset error state on success
            consecutiveErrors = 0
            if (isRateLimited && System.currentTimeMillis() > rateLimitEndTime) {
                isRateLimited = false
                currentBackoffMs = INITIAL_BACKOFF_MS
                Log.i(TAG, "Rate limit cleared - API responding normally")
            }
            
            // Update cache with way ID and bearing
            if (result != null) {
                cachedSpeedLimit = result.speedLimit
                cachedWayId = result.wayId
                cachedLat = lat
                cachedLon = lon
                cacheTimestamp = System.currentTimeMillis()
                if (bearing >= 0) {
                    lastBearing = bearing.toDouble()
                }
            }
            
            val hitRate = if (totalQueriesThisSession > 0) 
                (cacheHitsThisSession * 100) / (cacheHitsThisSession + totalQueriesThisSession) else 0
            
            Log.d(TAG, "Cache MISS - Queried API: ${result?.speedLimit} mph (way: ${result?.wayId}) " +
                    "[hit rate: $hitRate%]")
            
            return@withContext result?.speedLimit
            
        } catch (e: RateLimitException) {
            handleRateLimit(e.responseCode)
            return@withContext cachedSpeedLimit
        } catch (e: Exception) {
            consecutiveErrors++
            Log.e(TAG, "Error fetching speed limit (consecutive: $consecutiveErrors)", e)
            
            // If we're getting repeated errors, might be soft rate limiting
            if (consecutiveErrors >= 3) {
                handleRateLimit(0) // Treat as rate limit
            }
            
            return@withContext cachedSpeedLimit // Return last known value if available
        }
    }

    /**
     * Check if we're currently in a backoff period.
     */
    private fun isInBackoffPeriod(): Boolean {
        return isRateLimited && System.currentTimeMillis() < rateLimitEndTime
    }
    
    /**
     * Get remaining seconds in backoff period.
     */
    private fun getRemainingBackoffSeconds(): Int {
        return if (isRateLimited) {
            ((rateLimitEndTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
        } else 0
    }
    
    /**
     * Handle rate limit response with exponential backoff.
     */
    private fun handleRateLimit(responseCode: Int) {
        rateLimitEventsThisSession++
        isRateLimited = true
        rateLimitEndTime = System.currentTimeMillis() + currentBackoffMs
        
        val backoffSeconds = (currentBackoffMs / 1000).toInt()
        val errorType = when (responseCode) {
            429 -> "Too Many Requests"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            else -> "Connection Error"
        }
        
        Log.w(TAG, "⚠️ RATE LIMITED! Code: $responseCode, Backing off for ${backoffSeconds}s " +
                "(events this session: $rateLimitEventsThisSession)")
        
        // Log to Firebase Analytics for developer monitoring
        AnalyticsHelper.logRateLimitHit(responseCode, errorType, backoffSeconds, currentCountryCode)
        
        // Broadcast alert for UI notification
        broadcastRateLimitAlert(responseCode, backoffSeconds)
        
        // Increase backoff for next time (exponential)
        currentBackoffMs = (currentBackoffMs * BACKOFF_MULTIPLIER).toLong()
            .coerceAtMost(MAX_BACKOFF_MS)
    }
    
    /**
     * Broadcast rate limit alert for UI notification.
     */
    private fun broadcastRateLimitAlert(responseCode: Int, backoffSeconds: Int) {
        val intent = Intent(ACTION_RATE_LIMIT_ALERT).apply {
            putExtra(EXTRA_RATE_LIMIT_TYPE, when (responseCode) {
                429 -> "Too Many Requests"
                503 -> "Service Unavailable"
                504 -> "Gateway Timeout"
                else -> "Connection Error"
            })
            putExtra(EXTRA_BACKOFF_SECONDS, backoffSeconds)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Smart cache validation with bearing detection.
     * Returns cached value if valid, null if we need to query.
     * 
     * Re-queries when:
     * - Cache expired (>1 minute)
     * - Traveled >150m from last query
     * - Direction changed significantly (>35°) - likely turned onto new road
     * - Traveled >400m even with same way ID
     */
    private fun checkCache(lat: Double, lon: Double, currentBearing: Double): Int? {
        // No cache exists
        if (cachedSpeedLimit == null) {
            Log.d(TAG, "Cache check: No cached value")
            return null
        }
        
        // Cache too old (1 minute)
        val cacheAge = System.currentTimeMillis() - cacheTimestamp
        if (cacheAge > CACHE_DURATION_MS) {
            Log.d(TAG, "Cache check: Expired (age: ${cacheAge/1000}s)")
            return null
        }
        
        val distance = calculateDistance(cachedLat, cachedLon, lat, lon)
        
        // Check for significant bearing change (indicates a turn)
        if (currentBearing >= 0 && lastBearing >= 0) {
            val bearingChange = calculateBearingDifference(lastBearing, currentBearing)
            if (bearingChange > BEARING_CHANGE_THRESHOLD) {
                Log.d(TAG, "Cache check: Direction changed ${bearingChange.toInt()}° - likely turned onto new road")
                return null
            }
        }
        
        // Hard distance limit - always re-query after 400m regardless of way ID
        if (distance >= SAME_WAY_MAX_DISTANCE) {
            Log.d(TAG, "Cache check: Max distance exceeded (${distance.toInt()}m)")
            return null
        }
        
        // Standard re-query distance (150m)
        if (distance >= REQUERY_DISTANCE_METERS) {
            // If we have same way ID, allow a bit more tolerance
            if (cachedWayId != null && distance < SAME_WAY_MAX_DISTANCE) {
                Log.d(TAG, "Cache check: Using way ID cache at ${distance.toInt()}m")
                return cachedSpeedLimit
            }
            Log.d(TAG, "Cache check: Re-query distance exceeded (${distance.toInt()}m)")
            return null
        }
        
        return cachedSpeedLimit
    }
    
    /**
     * Calculate the difference between two bearings (0-360°).
     * Returns absolute difference accounting for wrap-around.
     */
    private fun calculateBearingDifference(bearing1: Double, bearing2: Double): Double {
        var diff = Math.abs(bearing1 - bearing2)
        if (diff > 180) {
            diff = 360 - diff
        }
        return diff
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }

    /**
     * Custom exception for rate limit responses.
     */
    private class RateLimitException(val responseCode: Int) : Exception("Rate limited: $responseCode")

    /**
     * Result from Overpass query including way ID for smart caching.
     */
    private data class OverpassResult(
        val speedLimit: Int,
        val wayId: Long
    )

    private fun queryOverpassApi(lat: Double, lon: Double, countryCode: String): OverpassResult? {
        // Modified query to include way IDs (using 'out body' instead of 'out tags')
        val query = """
            [out:json][timeout:10];
            way(around:$SEARCH_RADIUS_METERS,$lat,$lon)["maxspeed"];
            out body;
        """.trimIndent()

        val url = "$OVERPASS_API_URL?data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        
        Log.d(TAG, "Querying Overpass API for location: $lat, $lon")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SpeedLimit/3.1 (Android App; https://github.com/itomicspaceman/speed-limit)")
            .build()

        val response = httpClient.newCall(request).execute()
        
        // Check for rate limit responses
        when (response.code) {
            429, 503, 504 -> {
                Log.e(TAG, "Overpass API rate limit/error: ${response.code}")
                throw RateLimitException(response.code)
            }
        }
        
        if (!response.isSuccessful) {
            Log.e(TAG, "Overpass API error: ${response.code}")
            return null
        }

        val responseBody = response.body?.string() ?: return null
        return parseOverpassResponse(responseBody, countryCode)
    }

    private fun parseOverpassResponse(jsonString: String, countryCode: String): OverpassResult? {
        try {
            val json = JSONObject(jsonString)
            val elements = json.optJSONArray("elements") ?: return null
            
            if (elements.length() == 0) {
                Log.d(TAG, "No roads with speed limits found nearby")
                return null
            }

            // Collect all speed limits with their way IDs
            data class WayLimit(val wayId: Long, val speedLimit: Int)
            val wayLimits = mutableListOf<WayLimit>()
            
            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                val wayId = element.optLong("id", -1)
                val tags = element.optJSONObject("tags") ?: continue
                val maxspeed = tags.optString("maxspeed", "") 
                
                // Use country-aware parsing
                val limit = SpeedUnitHelper.parseSpeedLimitToMph(maxspeed, countryCode)
                if (limit != null && wayId != -1L) {
                    wayLimits.add(WayLimit(wayId, limit))
                }
            }

            if (wayLimits.isEmpty()) return null

            // Return the most common speed limit with its way ID
            val mostCommon = wayLimits
                .groupBy { it.speedLimit }
                .maxByOrNull { it.value.size }
                ?.value
                ?.firstOrNull()
                
            return mostCommon?.let { 
                OverpassResult(it.speedLimit, it.wayId) 
            }
                
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Overpass response", e)
            return null
        }
    }
    
    /**
     * Get cache statistics for debugging/monitoring.
     */
    fun getCacheStats(): String {
        val total = cacheHitsThisSession + totalQueriesThisSession
        val hitRate = if (total > 0) (cacheHitsThisSession * 100) / total else 0
        return "Hits: $cacheHitsThisSession, Queries: $totalQueriesThisSession, Hit Rate: $hitRate%, Rate Limits: $rateLimitEventsThisSession"
    }
    
    /**
     * Check if currently rate limited.
     */
    fun isCurrentlyRateLimited(): Boolean = isInBackoffPeriod()
    
    /**
     * Get rate limit status for display.
     */
    fun getRateLimitStatus(): String? {
        return if (isInBackoffPeriod()) {
            "API paused (${getRemainingBackoffSeconds()}s)"
        } else null
    }
}
