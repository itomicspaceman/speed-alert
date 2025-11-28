package com.speedlimit

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Provides speed limit data from OpenStreetMap via the Overpass API.
 * Automatically adapts to local speed units based on GPS location.
 */
class SpeedLimitProvider(private val context: Context) {

    companion object {
        const val TAG = "SpeedLimitProvider"
        
        // Overpass API endpoint (public server)
        const val OVERPASS_API_URL = "https://overpass-api.de/api/interpreter"
        
        // Search radius in meters
        const val SEARCH_RADIUS_METERS = 50
        
        // Cache duration in milliseconds (30 seconds)
        const val CACHE_DURATION_MS = 30_000L
        
        // Minimum distance to trigger new API call (meters)
        const val MIN_DISTANCE_FOR_NEW_QUERY = 100.0
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Speed limit cache
    private var cachedSpeedLimit: Int? = null
    private var cachedLat: Double = 0.0
    private var cachedLon: Double = 0.0
    private var cacheTimestamp: Long = 0
    
    // Current country (for display purposes)
    var currentCountryCode: String = "GB"
        private set

    /**
     * Get the speed limit for a given location.
     * Returns the speed limit in mph, or null if not found.
     */
    suspend fun getSpeedLimit(lat: Double, lon: Double): Int? = withContext(Dispatchers.IO) {
        // Detect country for this location
        currentCountryCode = SpeedUnitHelper.detectCountry(context, lat, lon)
        
        // Check cache first
        if (isCacheValid(lat, lon)) {
            Log.d(TAG, "Using cached speed limit: $cachedSpeedLimit mph (country: $currentCountryCode)")
            return@withContext cachedSpeedLimit
        }

        try {
            val speedLimit = queryOverpassApi(lat, lon, currentCountryCode)
            
            // Update cache
            cachedSpeedLimit = speedLimit
            cachedLat = lat
            cachedLon = lon
            cacheTimestamp = System.currentTimeMillis()
            
            Log.d(TAG, "Fetched speed limit: $speedLimit mph (country: $currentCountryCode)")
            return@withContext speedLimit
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching speed limit", e)
            return@withContext cachedSpeedLimit // Return last known value if available
        }
    }

    private fun isCacheValid(lat: Double, lon: Double): Boolean {
        if (cachedSpeedLimit == null) return false
        if (System.currentTimeMillis() - cacheTimestamp > CACHE_DURATION_MS) return false
        
        val distance = calculateDistance(cachedLat, cachedLon, lat, lon)
        return distance < MIN_DISTANCE_FOR_NEW_QUERY
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

    private fun queryOverpassApi(lat: Double, lon: Double, countryCode: String): Int? {
        // Overpass QL query to find roads with speed limits near the location
        val query = """
            [out:json][timeout:10];
            way(around:$SEARCH_RADIUS_METERS,$lat,$lon)["maxspeed"];
            out tags;
        """.trimIndent()

        val url = "$OVERPASS_API_URL?data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        
        Log.d(TAG, "Querying Overpass API for location: $lat, $lon (country: $countryCode)")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SpeedLimit/1.0 (Android App)")
            .build()

        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            Log.e(TAG, "Overpass API error: ${response.code}")
            return null
        }

        val responseBody = response.body?.string() ?: return null
        return parseOverpassResponse(responseBody, countryCode)
    }

    private fun parseOverpassResponse(jsonString: String, countryCode: String): Int? {
        try {
            val json = JSONObject(jsonString)
            val elements = json.optJSONArray("elements") ?: return null
            
            if (elements.length() == 0) {
                Log.d(TAG, "No roads with speed limits found nearby")
                return null
            }

            // Collect all speed limits and return the most common one
            val speedLimits = mutableListOf<Int>()
            
            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                val tags = element.optJSONObject("tags") ?: continue
                val maxspeed = tags.optString("maxspeed", "") 
                
                // Use country-aware parsing
                val limit = SpeedUnitHelper.parseSpeedLimitToMph(maxspeed, countryCode)
                if (limit != null) {
                    speedLimits.add(limit)
                }
            }

            if (speedLimits.isEmpty()) return null

            // Return the most common speed limit
            return speedLimits.groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Overpass response", e)
            return null
        }
    }
}

