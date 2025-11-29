package com.speedlimit

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Helper class for Firebase Analytics and Crashlytics.
 * Provides centralized logging for monitoring app health and API usage.
 */
object AnalyticsHelper {
    
    private const val TAG = "AnalyticsHelper"
    
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null
    
    // Event names
    const val EVENT_RATE_LIMIT_HIT = "rate_limit_hit"
    const val EVENT_API_ERROR = "api_error"
    const val EVENT_API_QUERY = "api_query"
    const val EVENT_CACHE_STATS = "cache_stats"
    const val EVENT_APP_START = "app_monitoring_start"
    const val EVENT_APP_STOP = "app_monitoring_stop"
    
    // Parameter names
    const val PARAM_ERROR_CODE = "error_code"
    const val PARAM_ERROR_TYPE = "error_type"
    const val PARAM_BACKOFF_SECONDS = "backoff_seconds"
    const val PARAM_COUNTRY_CODE = "country_code"
    const val PARAM_CACHE_HIT_RATE = "cache_hit_rate"
    const val PARAM_TOTAL_QUERIES = "total_queries"
    const val PARAM_RATE_LIMIT_COUNT = "rate_limit_count"
    const val PARAM_SESSION_DURATION_MINUTES = "session_duration_minutes"
    
    /**
     * Initialize Firebase Analytics and Crashlytics.
     * Call this once from Application or MainActivity.
     */
    fun initialize(context: Context) {
        try {
            analytics = FirebaseAnalytics.getInstance(context)
            crashlytics = FirebaseCrashlytics.getInstance()
            
            // Set custom keys for Crashlytics
            crashlytics?.setCustomKey("app_version", "3.2")
            
            Log.i(TAG, "Firebase Analytics and Crashlytics initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase", e)
        }
    }
    
    /**
     * Log when API rate limit is hit.
     * This is the critical event for developer monitoring.
     */
    fun logRateLimitHit(errorCode: Int, errorType: String, backoffSeconds: Int, countryCode: String) {
        val bundle = Bundle().apply {
            putInt(PARAM_ERROR_CODE, errorCode)
            putString(PARAM_ERROR_TYPE, errorType)
            putInt(PARAM_BACKOFF_SECONDS, backoffSeconds)
            putString(PARAM_COUNTRY_CODE, countryCode)
        }
        
        analytics?.logEvent(EVENT_RATE_LIMIT_HIT, bundle)
        
        // Also log as non-fatal to Crashlytics for visibility
        crashlytics?.apply {
            setCustomKey("last_rate_limit_code", errorCode)
            setCustomKey("last_rate_limit_type", errorType)
            setCustomKey("country_code", countryCode)
            recordException(RateLimitException(errorCode, errorType, countryCode))
        }
        
        Log.w(TAG, "📊 Logged rate_limit_hit: code=$errorCode, type=$errorType, country=$countryCode")
    }
    
    /**
     * Log generic API errors.
     */
    fun logApiError(errorCode: Int, errorMessage: String, countryCode: String) {
        val bundle = Bundle().apply {
            putInt(PARAM_ERROR_CODE, errorCode)
            putString(PARAM_ERROR_TYPE, errorMessage)
            putString(PARAM_COUNTRY_CODE, countryCode)
        }
        
        analytics?.logEvent(EVENT_API_ERROR, bundle)
        
        Log.w(TAG, "📊 Logged api_error: code=$errorCode, message=$errorMessage")
    }
    
    /**
     * Log cache statistics periodically for health monitoring.
     */
    fun logCacheStats(hitRate: Int, totalQueries: Int, rateLimitCount: Int, countryCode: String) {
        val bundle = Bundle().apply {
            putInt(PARAM_CACHE_HIT_RATE, hitRate)
            putInt(PARAM_TOTAL_QUERIES, totalQueries)
            putInt(PARAM_RATE_LIMIT_COUNT, rateLimitCount)
            putString(PARAM_COUNTRY_CODE, countryCode)
        }
        
        analytics?.logEvent(EVENT_CACHE_STATS, bundle)
        
        Log.d(TAG, "📊 Logged cache_stats: hitRate=$hitRate%, queries=$totalQueries, rateLimits=$rateLimitCount")
    }
    
    /**
     * Log when user starts monitoring.
     */
    fun logMonitoringStart(countryCode: String) {
        val bundle = Bundle().apply {
            putString(PARAM_COUNTRY_CODE, countryCode)
        }
        
        analytics?.logEvent(EVENT_APP_START, bundle)
        
        Log.d(TAG, "📊 Logged monitoring_start: country=$countryCode")
    }
    
    /**
     * Log when user stops monitoring with session stats.
     */
    fun logMonitoringStop(sessionDurationMinutes: Int, cacheHitRate: Int, totalQueries: Int, rateLimitCount: Int) {
        val bundle = Bundle().apply {
            putInt(PARAM_SESSION_DURATION_MINUTES, sessionDurationMinutes)
            putInt(PARAM_CACHE_HIT_RATE, cacheHitRate)
            putInt(PARAM_TOTAL_QUERIES, totalQueries)
            putInt(PARAM_RATE_LIMIT_COUNT, rateLimitCount)
        }
        
        analytics?.logEvent(EVENT_APP_STOP, bundle)
        
        Log.d(TAG, "📊 Logged monitoring_stop: duration=${sessionDurationMinutes}min, hitRate=$cacheHitRate%")
    }
    
    /**
     * Set user property for segmentation.
     */
    fun setUserCountry(countryCode: String) {
        analytics?.setUserProperty("primary_country", countryCode)
        crashlytics?.setCustomKey("primary_country", countryCode)
    }
    
    /**
     * Custom exception for rate limit events in Crashlytics.
     */
    private class RateLimitException(
        errorCode: Int,
        errorType: String,
        countryCode: String
    ) : Exception("Rate limit hit: $errorType (code: $errorCode) in $countryCode")
}

