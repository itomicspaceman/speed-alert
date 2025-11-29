package com.speedlimit

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages local storage of OSM contribution attempts.
 * Stores both successful and failed attempts for user review.
 */
class ContributionLog(context: Context) {

    companion object {
        private const val PREFS_NAME = "contribution_log"
        private const val KEY_ATTEMPTS = "attempts"
        private const val MAX_ENTRIES = 100 // Keep last 100 attempts
        
        // Rate limiting constants
        const val MIN_TIME_BETWEEN_SUBMISSIONS_MS = 30_000L  // 30 seconds
        const val MIN_DISTANCE_BETWEEN_SUBMISSIONS_M = 100.0 // 100 meters
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Status of a contribution attempt.
     */
    enum class Status {
        SUCCESS,           // ✅ Submitted to OSM
        FAILED_NO_AUTH,    // ❌ Not logged in
        FAILED_NO_WAY,     // ❌ No road at location  
        FAILED_GPS_POOR,   // ❌ Location accuracy too low
        FAILED_INVALID_WAY,// ❌ Not a valid road type
        FAILED_API_ERROR,  // ❌ OSM API error
        FAILED_RATE_LIMITED,// ❌ Too soon after last attempt
        FAILED_SAME_LIMIT, // ❌ Same as currently detected limit
        SKIPPED_SAME_LIMIT // ⏭️ Silently skipped (same limit)
    }

    /**
     * A single contribution attempt.
     */
    data class Attempt(
        val timestamp: Long,
        val latitude: Double,
        val longitude: Double,
        val wayId: Long,
        val wayName: String?,
        val speedLimit: Int,
        val unit: String,
        val status: Status,
        val failureReason: String?
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("timestamp", timestamp)
                put("latitude", latitude)
                put("longitude", longitude)
                put("wayId", wayId)
                put("wayName", wayName ?: "")
                put("speedLimit", speedLimit)
                put("unit", unit)
                put("status", status.name)
                put("failureReason", failureReason ?: "")
            }
        }

        companion object {
            fun fromJson(json: JSONObject): Attempt {
                return Attempt(
                    timestamp = json.getLong("timestamp"),
                    latitude = json.getDouble("latitude"),
                    longitude = json.getDouble("longitude"),
                    wayId = json.getLong("wayId"),
                    wayName = json.optString("wayName").takeIf { it.isNotEmpty() },
                    speedLimit = json.getInt("speedLimit"),
                    unit = json.getString("unit"),
                    status = Status.valueOf(json.getString("status")),
                    failureReason = json.optString("failureReason").takeIf { it.isNotEmpty() }
                )
            }
        }
    }

    // Track last submission for rate limiting
    private var lastSubmissionTime: Long = 0
    private var lastSubmissionLat: Double = 0.0
    private var lastSubmissionLon: Double = 0.0

    init {
        // Load last submission info from most recent attempt
        val attempts = getAttempts()
        val lastSuccess = attempts.find { it.status == Status.SUCCESS }
        if (lastSuccess != null) {
            lastSubmissionTime = lastSuccess.timestamp
            lastSubmissionLat = lastSuccess.latitude
            lastSubmissionLon = lastSuccess.longitude
        }
    }

    /**
     * Check if user can submit (rate limiting check).
     * Returns null if OK, or a failure reason string if not.
     */
    fun canSubmit(currentLat: Double, currentLon: Double): String? {
        val now = System.currentTimeMillis()
        
        // Time check
        val timeSinceLast = now - lastSubmissionTime
        if (timeSinceLast < MIN_TIME_BETWEEN_SUBMISSIONS_MS) {
            val secondsRemaining = ((MIN_TIME_BETWEEN_SUBMISSIONS_MS - timeSinceLast) / 1000).toInt()
            return "Wait ${secondsRemaining}s before next submission"
        }
        
        // Distance check (only if we have a previous location)
        if (lastSubmissionLat != 0.0 && lastSubmissionLon != 0.0) {
            val distance = calculateDistance(lastSubmissionLat, lastSubmissionLon, currentLat, currentLon)
            if (distance < MIN_DISTANCE_BETWEEN_SUBMISSIONS_M) {
                return "Move ${(MIN_DISTANCE_BETWEEN_SUBMISSIONS_M - distance).toInt()}m before next submission"
            }
        }
        
        return null // OK to submit
    }

    /**
     * Log a contribution attempt.
     */
    fun logAttempt(attempt: Attempt) {
        val attempts = getAttemptsMutable()
        attempts.add(0, attempt) // Add to front (most recent first)
        
        // Trim to max size
        while (attempts.size > MAX_ENTRIES) {
            attempts.removeAt(attempts.size - 1)
        }
        
        saveAttempts(attempts)
        
        // Update last submission tracking if successful
        if (attempt.status == Status.SUCCESS) {
            lastSubmissionTime = attempt.timestamp
            lastSubmissionLat = attempt.latitude
            lastSubmissionLon = attempt.longitude
        }
    }

    /**
     * Get all logged attempts.
     */
    fun getAttempts(): List<Attempt> {
        return getAttemptsMutable().toList()
    }

    /**
     * Get count of successful submissions.
     */
    fun getSuccessCount(): Int {
        return getAttempts().count { it.status == Status.SUCCESS }
    }

    /**
     * Get count of failed submissions.
     */
    fun getFailedCount(): Int {
        return getAttempts().count { 
            it.status != Status.SUCCESS && 
            it.status != Status.SKIPPED_SAME_LIMIT 
        }
    }

    /**
     * Clear all logged attempts.
     */
    fun clear() {
        prefs.edit().remove(KEY_ATTEMPTS).apply()
        lastSubmissionTime = 0
        lastSubmissionLat = 0.0
        lastSubmissionLon = 0.0
    }

    private fun getAttemptsMutable(): MutableList<Attempt> {
        val json = prefs.getString(KEY_ATTEMPTS, null) ?: return mutableListOf()
        
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<Attempt>()
            for (i in 0 until array.length()) {
                list.add(Attempt.fromJson(array.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun saveAttempts(attempts: List<Attempt>) {
        val array = JSONArray()
        attempts.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ATTEMPTS, array.toString()).apply()
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
}

