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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Provides speed limit data from OpenStreetMap via the Overpass API.
 * Uses SMART CORRIDOR CACHING for efficiency and accuracy.
 * 
 * Strategy:
 * - Query a corridor ~500m AHEAD in direction of travel
 * - Cache ALL road segments with their geometries
 * - Match current position to cached segments (no API call)
 * - Only re-query when approaching edge of cached area
 * 
 * Benefits:
 * - ~70% fewer API calls
 * - Know speed limits BEFORE reaching them
 * - Instant updates (no latency when limit changes)
 * - Better for voice announcements
 */
class SpeedLimitProvider(private val context: Context) {

    companion object {
        const val TAG = "SpeedLimitProvider"
        
        // Overpass API endpoint
        const val OVERPASS_API_URL = "https://overpass-api.de/api/interpreter"
        
        // Corridor query parameters
        const val CORRIDOR_LENGTH_METERS = 500.0    // Look ahead distance
        const val CORRIDOR_WIDTH_METERS = 100.0     // Width of corridor
        const val REQUERY_THRESHOLD = 0.7           // Re-query when 70% through corridor
        const val MIN_REQUERY_DISTANCE = 100.0      // Don't re-query within 100m of last query
        
        // Fallback for when no bearing available
        const val POINT_SEARCH_RADIUS = 50
        
        // Segment matching tolerance
        const val SEGMENT_MATCH_DISTANCE = 30.0     // Must be within 30m of a road segment
        
        // Cache expiry
        const val CACHE_MAX_AGE_MS = 300_000L       // 5 minutes (longer since we cache more)
        const val MAX_CACHED_SEGMENTS = 100         // Limit memory usage
        
        // User submission cache - instant gratification before Overpass syncs
        const val USER_SUBMISSION_TTL_MS = 300_000L // 5 minutes - then defer to OSM
        
        // Rate limit handling
        const val INITIAL_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 300_000L
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

    /**
     * Cached road segment with geometry for position matching.
     */
    data class RoadSegment(
        val wayId: Long,
        val speedLimitMph: Int,
        val geometry: List<LatLon>,  // List of coordinates defining the road
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class LatLon(val lat: Double, val lon: Double)
    
    /**
     * User-submitted speed limit with expiry.
     * Provides instant gratification for 5 minutes before deferring to OSM.
     * Includes location so we can match even if road isn't in cache.
     */
    data class UserSubmission(
        val wayId: Long,
        val speedLimitMph: Int,
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > USER_SUBMISSION_TTL_MS
        
        /**
         * Check if a location is close enough to this submission (within 50m).
         */
        fun isNearby(lat: Double, lon: Double): Boolean {
            val R = 6371000.0 // Earth's radius in meters
            val dLat = Math.toRadians(lat - latitude)
            val dLon = Math.toRadians(lon - longitude)
            val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                    kotlin.math.cos(Math.toRadians(latitude)) * kotlin.math.cos(Math.toRadians(lat)) *
                    kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
            val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
            val distance = R * c
            return distance <= 50.0  // Within 50 meters
        }
    }
    
    /**
     * Bounding box for tracking queried areas.
     */
    data class BoundingBox(
        val south: Double,
        val west: Double, 
        val north: Double,
        val east: Double,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun contains(lat: Double, lon: Double): Boolean {
            return lat in south..north && lon in west..east
        }
        
        // Check if a point is in the "forward" portion of the box
        fun isNearEdge(lat: Double, lon: Double, threshold: Double = REQUERY_THRESHOLD): Boolean {
            val latProgress = (lat - south) / (north - south)
            val lonProgress = (lon - west) / (east - west)
            return latProgress > threshold || lonProgress > threshold || 
                   latProgress < (1 - threshold) || lonProgress < (1 - threshold)
        }
    }

    // Cached road segments with geometries
    private val cachedSegments = mutableListOf<RoadSegment>()
    private var queriedBoundingBox: BoundingBox? = null
    private var lastQueryLocation: LatLon? = null
    private var lastBearing: Double = -1.0
    
    // Current matched segment
    private var currentSegment: RoadSegment? = null
    
    // User submissions cache - instant gratification before Overpass syncs
    private val userSubmissions = mutableMapOf<Long, UserSubmission>()
    
    // Statistics
    private var totalQueriesThisSession: Int = 0
    private var cacheHitsThisSession: Int = 0
    private var segmentMatchesThisSession: Int = 0
    
    // Rate limit state
    private var isRateLimited: Boolean = false
    private var rateLimitEndTime: Long = 0
    private var currentBackoffMs: Long = INITIAL_BACKOFF_MS
    private var rateLimitEventsThisSession: Int = 0
    private var consecutiveErrors: Int = 0
    
    // Current country
    var currentCountryCode: String = "GB"
        private set
    
    // Current way ID (for crowdsourcing)
    // Note: This may be -1 if no road with maxspeed found - use findNearestWayId() for contributions
    val currentWayId: Long
        get() = currentSegment?.wayId ?: lastFoundWayId ?: -1L
    
    // Last found way ID from any query (including contribution lookups)
    private var lastFoundWayId: Long? = null
    private var lastFoundWayLocation: LatLon? = null
    private var lastFoundHighwayType: String? = null
    
    /**
     * Highway types that are appropriate for speed limits.
     * Based on OSM highway classification.
     */
    private val VALID_SPEED_LIMIT_HIGHWAYS = setOf(
        "motorway", "motorway_link",
        "trunk", "trunk_link",
        "primary", "primary_link",
        "secondary", "secondary_link",
        "tertiary", "tertiary_link",
        "unclassified",
        "residential",
        "living_street",
        "service"  // Parking lots etc - some have speed limits
    )
    
    /**
     * Highway types that should NOT have speed limits.
     */
    private val INVALID_SPEED_LIMIT_HIGHWAYS = setOf(
        "footway", "pedestrian", "path", "steps", "corridor",
        "cycleway", "bridleway", "track",
        "bus_guideway", "raceway",
        "proposed", "construction",
        "platform", "rest_area"
    )
    
    /**
     * Result from finding nearest road for contributions.
     */
    data class NearestRoadResult(
        val wayId: Long,
        val highwayType: String,
        val name: String? = null,
        val isValidForSpeedLimit: Boolean
    )

    /**
     * Get the speed limit for a given location.
     * Uses corridor caching for efficiency.
     */
    suspend fun getSpeedLimit(lat: Double, lon: Double, bearing: Float = -1f): Int? = withContext(Dispatchers.IO) {
        // Detect country
        currentCountryCode = SpeedUnitHelper.detectCountry(context, lat, lon)
        
        // FIRST: Check for nearby user submission (instant gratification)
        // This takes priority over everything - user's submission should show immediately
        val nearbyUserLimit = findNearbyUserSubmission(lat, lon)
        if (nearbyUserLimit != null) {
            Log.d(TAG, "Using NEARBY USER SUBMISSION: $nearbyUserLimit mph")
            return@withContext nearbyUserLimit
        }
        
        // Check rate limit
        if (isInBackoffPeriod()) {
            Log.w(TAG, "In backoff period, using cached data")
            return@withContext findMatchingSegment(lat, lon)?.speedLimitMph
        }
        
        // Try to match current position to cached segments
        val matchedSegment = findMatchingSegment(lat, lon)
        if (matchedSegment != null) {
            currentSegment = matchedSegment
            segmentMatchesThisSession++
            
            // Check if we need to pre-fetch more data (approaching edge of cached area)
            val shouldPrefetch = shouldQueryAhead(lat, lon, bearing.toDouble())
            if (shouldPrefetch && bearing >= 0) {
                Log.d(TAG, "Pre-fetching corridor ahead...")
                queryCorridorAsync(lat, lon, bearing.toDouble())
            }
            
            // Check for user submission override by wayId (instant gratification)
            val userLimit = getUserSubmittedLimit(matchedSegment.wayId)
            if (userLimit != null) {
                Log.d(TAG, "Using USER SUBMITTED limit: $userLimit mph (way: ${matchedSegment.wayId})")
                return@withContext userLimit
            }
            
            Log.d(TAG, "Segment MATCH: ${matchedSegment.speedLimitMph} mph (way: ${matchedSegment.wayId}) " +
                    "[matches: $segmentMatchesThisSession, queries: $totalQueriesThisSession]")
            return@withContext matchedSegment.speedLimitMph
        }
        
        // No cached segment matches - need to query
        Log.d(TAG, "No segment match, querying API...")
        
        try {
            val result = if (bearing >= 0) {
                queryCorridor(lat, lon, bearing.toDouble())
            } else {
                // No bearing available - fall back to point query
                queryPoint(lat, lon)
            }
            
            consecutiveErrors = 0
            clearRateLimitIfExpired()
            
            // Find matching segment from fresh data
            val newMatch = findMatchingSegment(lat, lon)
            currentSegment = newMatch
            
            // Check for user submission on the newly matched segment
            if (newMatch != null) {
                val userLimit = getUserSubmittedLimit(newMatch.wayId)
                if (userLimit != null) {
                    Log.d(TAG, "Using USER SUBMITTED limit after query: $userLimit mph (way: ${newMatch.wayId})")
                    return@withContext userLimit
                }
            }
            
            return@withContext newMatch?.speedLimitMph
            
        } catch (e: RateLimitException) {
            handleRateLimit(e.responseCode)
            return@withContext currentSegment?.speedLimitMph
        } catch (e: Exception) {
            handleError(e)
            return@withContext currentSegment?.speedLimitMph
        }
    }
    
    /**
     * Query a corridor ahead in the direction of travel.
     * This is the smart query that caches multiple road segments.
     */
    private fun queryCorridor(lat: Double, lon: Double, bearing: Double): Boolean {
        totalQueriesThisSession++
        
        // Calculate bounding box for corridor
        val bbox = calculateCorridorBbox(lat, lon, bearing)
        
        val query = """
            [out:json][timeout:15];
            way["maxspeed"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
            out body geom;
        """.trimIndent()
        
        Log.d(TAG, "Corridor query: ${CORRIDOR_LENGTH_METERS}m ahead, bearing: ${bearing.toInt()}°")
        
        val segments = executeQuery(query)
        if (segments.isNotEmpty()) {
            // Add new segments, avoiding duplicates
            addSegments(segments)
            queriedBoundingBox = bbox
            lastQueryLocation = LatLon(lat, lon)
            lastBearing = bearing
            Log.d(TAG, "Cached ${segments.size} road segments (total: ${cachedSegments.size})")
            return true
        }
        
        return false
    }
    
    /**
     * Fall back to point query when no bearing is available.
     */
    private fun queryPoint(lat: Double, lon: Double): Boolean {
        totalQueriesThisSession++
        
        val query = """
            [out:json][timeout:10];
            way(around:$POINT_SEARCH_RADIUS,$lat,$lon)["maxspeed"];
            out body geom;
        """.trimIndent()
        
        Log.d(TAG, "Point query at $lat, $lon (no bearing)")
        
        val segments = executeQuery(query)
        if (segments.isNotEmpty()) {
            addSegments(segments)
            lastQueryLocation = LatLon(lat, lon)
            return true
        }
        
        return false
    }
    
    /**
     * Async pre-fetch of corridor ahead (doesn't block current request).
     */
    private fun queryCorridorAsync(lat: Double, lon: Double, bearing: Double) {
        // Simple approach: just do a blocking query
        // In production, this could be truly async with a separate coroutine
        try {
            queryCorridor(lat, lon, bearing)
        } catch (e: Exception) {
            Log.w(TAG, "Pre-fetch failed: ${e.message}")
        }
    }
    
    /**
     * Execute Overpass query and parse road segments.
     */
    private fun executeQuery(query: String): List<RoadSegment> {
        val url = "$OVERPASS_API_URL?data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SpeedLimit/3.5 (Android App; https://github.com/itomicspaceman/speed-limit)")
            .build()

        val response = httpClient.newCall(request).execute()
        
        when (response.code) {
            429, 503, 504 -> throw RateLimitException(response.code)
        }
        
        if (!response.isSuccessful) {
            Log.e(TAG, "API error: ${response.code}")
            return emptyList()
        }

        val responseBody = response.body?.string() ?: return emptyList()
        return parseSegments(responseBody)
    }
    
    /**
     * Parse Overpass response into road segments with geometries.
     */
    private fun parseSegments(jsonString: String): List<RoadSegment> {
        val segments = mutableListOf<RoadSegment>()
        
        try {
            val json = JSONObject(jsonString)
            val elements = json.optJSONArray("elements") ?: return emptyList()
            
            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                val wayId = element.optLong("id", -1)
                if (wayId == -1L) continue
                
                // Get speed limit
                val tags = element.optJSONObject("tags") ?: continue
                val maxspeed = tags.optString("maxspeed", "")
                val speedLimitMph = SpeedUnitHelper.parseSpeedLimitToMph(maxspeed, currentCountryCode)
                    ?: continue
                
                // Get geometry (list of coordinates)
                val geometry = mutableListOf<LatLon>()
                val geomArray = element.optJSONArray("geometry")
                if (geomArray != null) {
                    for (j in 0 until geomArray.length()) {
                        val point = geomArray.getJSONObject(j)
                        geometry.add(LatLon(
                            point.getDouble("lat"),
                            point.getDouble("lon")
                        ))
                    }
                }
                
                if (geometry.isNotEmpty()) {
                    segments.add(RoadSegment(
                        wayId = wayId,
                        speedLimitMph = speedLimitMph,
                        geometry = geometry
                    ))
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing segments", e)
        }
        
        return segments
    }
    
    /**
     * Add segments to cache, avoiding duplicates and limiting size.
     */
    private fun addSegments(newSegments: List<RoadSegment>) {
        val existingWayIds = cachedSegments.map { it.wayId }.toSet()
        
        for (segment in newSegments) {
            if (segment.wayId !in existingWayIds) {
                cachedSegments.add(segment)
            }
        }
        
        // Remove old segments if cache is too large
        if (cachedSegments.size > MAX_CACHED_SEGMENTS) {
            cachedSegments.sortBy { it.timestamp }
            while (cachedSegments.size > MAX_CACHED_SEGMENTS * 0.7) {
                cachedSegments.removeAt(0)
            }
        }
        
        // Remove expired segments
        val now = System.currentTimeMillis()
        cachedSegments.removeAll { now - it.timestamp > CACHE_MAX_AGE_MS }
    }
    
    /**
     * Find which cached road segment the current position is on.
     */
    private fun findMatchingSegment(lat: Double, lon: Double): RoadSegment? {
        var bestMatch: RoadSegment? = null
        var bestDistance = Double.MAX_VALUE
        
        for (segment in cachedSegments) {
            val distance = distanceToSegment(lat, lon, segment.geometry)
            if (distance < bestDistance && distance <= SEGMENT_MATCH_DISTANCE) {
                bestDistance = distance
                bestMatch = segment
            }
        }
        
        return bestMatch
    }
    
    /**
     * Calculate minimum distance from a point to a road segment (polyline).
     */
    private fun distanceToSegment(lat: Double, lon: Double, geometry: List<LatLon>): Double {
        if (geometry.isEmpty()) return Double.MAX_VALUE
        if (geometry.size == 1) {
            return calculateDistance(lat, lon, geometry[0].lat, geometry[0].lon)
        }
        
        var minDistance = Double.MAX_VALUE
        
        for (i in 0 until geometry.size - 1) {
            val p1 = geometry[i]
            val p2 = geometry[i + 1]
            val dist = pointToLineSegmentDistance(lat, lon, p1.lat, p1.lon, p2.lat, p2.lon)
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        
        return minDistance
    }
    
    /**
     * Calculate distance from point to line segment.
     */
    private fun pointToLineSegmentDistance(
        px: Double, py: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double
    ): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        
        if (dx == 0.0 && dy == 0.0) {
            return calculateDistance(px, py, x1, y1)
        }
        
        val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
        val tClamped = t.coerceIn(0.0, 1.0)
        
        val nearestX = x1 + tClamped * dx
        val nearestY = y1 + tClamped * dy
        
        return calculateDistance(px, py, nearestX, nearestY)
    }
    
    /**
     * Check if we should query ahead (approaching edge of cached area).
     */
    private fun shouldQueryAhead(lat: Double, lon: Double, bearing: Double): Boolean {
        val lastQuery = lastQueryLocation ?: return true
        val lastBox = queriedBoundingBox
        
        // Don't query too frequently
        val distanceFromLastQuery = calculateDistance(lat, lon, lastQuery.lat, lastQuery.lon)
        if (distanceFromLastQuery < MIN_REQUERY_DISTANCE) {
            return false
        }
        
        // Check if we're approaching edge of cached area
        if (lastBox != null) {
            // Calculate how far we've traveled through the corridor
            val corridorProgress = distanceFromLastQuery / CORRIDOR_LENGTH_METERS
            if (corridorProgress > REQUERY_THRESHOLD) {
                return true
            }
        }
        
        // Check if bearing has changed significantly (might be on new road)
        if (lastBearing >= 0 && bearing >= 0) {
            val bearingChange = calculateBearingDifference(lastBearing, bearing)
            if (bearingChange > 45) {  // Turned significantly
                return true
            }
        }
        
        return false
    }
    
    /**
     * Calculate bounding box for corridor in direction of travel.
     */
    private fun calculateCorridorBbox(lat: Double, lon: Double, bearing: Double): BoundingBox {
        // Convert bearing to radians
        val bearingRad = Math.toRadians(bearing)
        
        // Calculate end point of corridor
        val endPoint = projectPoint(lat, lon, bearing, CORRIDOR_LENGTH_METERS)
        
        // Calculate perpendicular offset for corridor width
        val halfWidth = CORRIDOR_WIDTH_METERS / 2
        val perpBearing1 = (bearing + 90) % 360
        val perpBearing2 = (bearing - 90 + 360) % 360
        
        // Get corners of corridor
        val corner1 = projectPoint(lat, lon, perpBearing1, halfWidth)
        val corner2 = projectPoint(lat, lon, perpBearing2, halfWidth)
        val corner3 = projectPoint(endPoint.lat, endPoint.lon, perpBearing1, halfWidth)
        val corner4 = projectPoint(endPoint.lat, endPoint.lon, perpBearing2, halfWidth)
        
        // Find bounding box of all corners
        val lats = listOf(corner1.lat, corner2.lat, corner3.lat, corner4.lat, lat)
        val lons = listOf(corner1.lon, corner2.lon, corner3.lon, corner4.lon, lon)
        
        return BoundingBox(
            south = lats.minOrNull()!! - 0.0005,  // Small buffer
            west = lons.minOrNull()!! - 0.0005,
            north = lats.maxOrNull()!! + 0.0005,
            east = lons.maxOrNull()!! + 0.0005
        )
    }
    
    /**
     * Project a point forward by distance in a given bearing.
     */
    private fun projectPoint(lat: Double, lon: Double, bearing: Double, distanceMeters: Double): LatLon {
        val earthRadius = 6371000.0
        val bearingRad = Math.toRadians(bearing)
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        
        val angularDistance = distanceMeters / earthRadius
        
        val newLatRad = Math.asin(
            sin(latRad) * cos(angularDistance) +
            cos(latRad) * sin(angularDistance) * cos(bearingRad)
        )
        
        val newLonRad = lonRad + Math.atan2(
            sin(bearingRad) * sin(angularDistance) * cos(latRad),
            cos(angularDistance) - sin(latRad) * sin(newLatRad)
        )
        
        return LatLon(Math.toDegrees(newLatRad), Math.toDegrees(newLonRad))
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * Math.atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
    
    private fun calculateBearingDifference(bearing1: Double, bearing2: Double): Double {
        var diff = Math.abs(bearing1 - bearing2)
        if (diff > 180) diff = 360 - diff
        return diff
    }

    // Rate limit handling
    private class RateLimitException(val responseCode: Int) : Exception("Rate limited: $responseCode")
    
    private fun isInBackoffPeriod(): Boolean = isRateLimited && System.currentTimeMillis() < rateLimitEndTime
    
    private fun clearRateLimitIfExpired() {
        if (isRateLimited && System.currentTimeMillis() > rateLimitEndTime) {
            isRateLimited = false
            currentBackoffMs = INITIAL_BACKOFF_MS
        }
    }
    
    private fun handleRateLimit(responseCode: Int) {
        rateLimitEventsThisSession++
        isRateLimited = true
        rateLimitEndTime = System.currentTimeMillis() + currentBackoffMs
        
        val backoffSeconds = (currentBackoffMs / 1000).toInt()
        Log.w(TAG, "⚠️ RATE LIMITED! Code: $responseCode, Backing off ${backoffSeconds}s")
        
        AnalyticsHelper.logRateLimitHit(responseCode, "API Rate Limit", backoffSeconds, currentCountryCode)
        broadcastRateLimitAlert(responseCode, backoffSeconds)
        
        currentBackoffMs = (currentBackoffMs * BACKOFF_MULTIPLIER).toLong().coerceAtMost(MAX_BACKOFF_MS)
    }
    
    private fun handleError(e: Exception) {
        consecutiveErrors++
        Log.e(TAG, "Error (consecutive: $consecutiveErrors)", e)
        if (consecutiveErrors >= 3) {
            handleRateLimit(0)
        }
    }
    
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
    
    // ==================== USER SUBMISSION CACHE ====================
    
    /**
     * Record a user-submitted speed limit for instant gratification.
     * The submission will be used for 5 minutes before deferring to OSM.
     * 
     * @param wayId The OSM way ID
     * @param speedLimitMph The speed limit in mph
     * @param lat Latitude where submission was made
     * @param lon Longitude where submission was made
     */
    fun recordUserSubmission(wayId: Long, speedLimitMph: Int, lat: Double, lon: Double) {
        // Clean up expired submissions first
        cleanupExpiredSubmissions()
        
        userSubmissions[wayId] = UserSubmission(wayId, speedLimitMph, lat, lon)
        Log.d(TAG, "Recorded user submission: $speedLimitMph mph for way $wayId at ($lat, $lon) (valid for 5 min)")
    }
    
    /**
     * Get a user-submitted speed limit if it exists and hasn't expired.
     * 
     * @return Speed limit in mph, or null if no valid submission exists
     */
    private fun getUserSubmittedLimit(wayId: Long): Int? {
        val submission = userSubmissions[wayId] ?: return null
        
        if (submission.isExpired()) {
            userSubmissions.remove(wayId)
            Log.d(TAG, "User submission for way $wayId expired, deferring to OSM")
            return null
        }
        
        return submission.speedLimitMph
    }
    
    /**
     * Find a user submission that's nearby the given location.
     * This allows instant gratification even when the road isn't in cache
     * (e.g., when user added a speed limit to a road that had no maxspeed tag).
     * 
     * @return Speed limit in mph, or null if no nearby submission exists
     */
    private fun findNearbyUserSubmission(lat: Double, lon: Double): Int? {
        // Clean up expired first
        cleanupExpiredSubmissions()
        
        // Find any submission within 50m of current location
        for (submission in userSubmissions.values) {
            if (!submission.isExpired() && submission.isNearby(lat, lon)) {
                return submission.speedLimitMph
            }
        }
        return null
    }
    
    /**
     * Clean up expired user submissions.
     */
    private fun cleanupExpiredSubmissions() {
        val expiredKeys = userSubmissions.filter { it.value.isExpired() }.keys
        expiredKeys.forEach { userSubmissions.remove(it) }
        if (expiredKeys.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${expiredKeys.size} expired user submissions")
        }
    }
    
    // ==================== STATISTICS ====================
    
    /**
     * Get cache statistics.
     */
    fun getCacheStats(): String {
        val total = segmentMatchesThisSession + totalQueriesThisSession
        val hitRate = if (total > 0) (segmentMatchesThisSession * 100) / total else 0
        return "Segments: ${cachedSegments.size}, Matches: $segmentMatchesThisSession, " +
               "Queries: $totalQueriesThisSession, Hit Rate: $hitRate%"
    }
    
    fun isCurrentlyRateLimited(): Boolean = isInBackoffPeriod()
    
    fun getRateLimitStatus(): String? {
        return if (isInBackoffPeriod()) {
            val remaining = ((rateLimitEndTime - System.currentTimeMillis()) / 1000).toInt()
            "API paused (${remaining}s)"
        } else null
    }
    
    /**
     * Find the nearest road (way) at a location for crowdsourcing purposes.
     * This queries ANY road, not just ones with maxspeed tags.
     * Returns detailed info including highway type for validation.
     * 
     * @return NearestRoadResult with way ID and validation info, or null if none found
     */
    suspend fun findNearestRoad(lat: Double, lon: Double): NearestRoadResult? = withContext(Dispatchers.IO) {
        // First check if we're close to the last found location
        val lastLoc = lastFoundWayLocation
        val lastWay = lastFoundWayId
        val lastType = lastFoundHighwayType
        if (lastLoc != null && lastWay != null && lastType != null) {
            val dist = calculateDistance(lat, lon, lastLoc.lat, lastLoc.lon)
            if (dist < 50) {  // Within 50m of last lookup
                Log.d(TAG, "Using cached road: $lastWay ($lastType, ${dist.toInt()}m from last lookup)")
                return@withContext NearestRoadResult(
                    wayId = lastWay,
                    highwayType = lastType,
                    isValidForSpeedLimit = isValidHighwayForSpeedLimit(lastType)
                )
            }
        }
        
        // Query for ANY highway (road) near this location with tags
        val query = """
            [out:json][timeout:10];
            way(around:30,$lat,$lon)["highway"];
            out tags;
        """.trimIndent()
        
        Log.d(TAG, "Finding nearest road for contribution at $lat, $lon")
        
        try {
            val url = "$OVERPASS_API_URL?data=${java.net.URLEncoder.encode(query, "UTF-8")}"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SpeedLimit/3.5 (Android App; https://github.com/itomicspaceman/speed-limit)")
                .build()

            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Road lookup failed: ${response.code}")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: return@withContext null
            val json = JSONObject(responseBody)
            val elements = json.optJSONArray("elements")
            
            if (elements == null || elements.length() == 0) {
                Log.w(TAG, "No roads found at location")
                return@withContext null
            }
            
            // Find the first valid road (prefer roads that can have speed limits)
            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                val wayId = element.getLong("id")
                val tags = element.optJSONObject("tags") ?: continue
                val highwayType = tags.optString("highway", "")
                val name = tags.optString("name", null)
                
                if (highwayType.isNotEmpty()) {
                    val isValid = isValidHighwayForSpeedLimit(highwayType)
                    
                    // Cache this for nearby future lookups
                    lastFoundWayId = wayId
                    lastFoundWayLocation = LatLon(lat, lon)
                    lastFoundHighwayType = highwayType
                    
                    Log.d(TAG, "Found road: way $wayId ($highwayType${if (name != null) ", $name" else ""}) - valid for speed limit: $isValid")
                    
                    return@withContext NearestRoadResult(
                        wayId = wayId,
                        highwayType = highwayType,
                        name = name,
                        isValidForSpeedLimit = isValid
                    )
                }
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finding road", e)
            return@withContext null
        }
    }
    
    /**
     * Legacy method for backwards compatibility.
     * @return Way ID of nearest road, or -1 if none found
     */
    suspend fun findNearestWayId(lat: Double, lon: Double): Long {
        return findNearestRoad(lat, lon)?.wayId ?: -1L
    }
    
    /**
     * Check if a highway type is appropriate for speed limits.
     */
    private fun isValidHighwayForSpeedLimit(highwayType: String): Boolean {
        // If it's explicitly valid, return true
        if (highwayType in VALID_SPEED_LIMIT_HIGHWAYS) return true
        
        // If it's explicitly invalid, return false
        if (highwayType in INVALID_SPEED_LIMIT_HIGHWAYS) return false
        
        // For unknown types, be permissive but log
        Log.w(TAG, "Unknown highway type: $highwayType - allowing contribution")
        return true
    }
    
}
