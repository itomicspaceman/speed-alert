package com.speedalert

import android.content.Context
import android.location.Geocoder
import android.util.Log
import java.util.Locale

/**
 * Helper for handling speed unit conversions and country detection.
 * Automatically detects whether to use mph or km/h based on location.
 */
object SpeedUnitHelper {
    
    private const val TAG = "SpeedUnitHelper"
    
    // Conversion factors
    const val KMH_TO_MPH = 0.621371
    const val MPH_TO_KMH = 1.60934
    const val MS_TO_MPH = 2.23694
    const val MS_TO_KMH = 3.6
    
    /**
     * Countries that use miles per hour.
     * ISO 3166-1 alpha-2 country codes.
     */
    private val MPH_COUNTRIES = setOf(
        "GB", // United Kingdom
        "US", // United States
        "LR", // Liberia
        "MM", // Myanmar
        "WS", // Samoa (American)
        "BS", // Bahamas
        "BZ", // Belize
        "VG", // British Virgin Islands
        "KY", // Cayman Islands
        "DM", // Dominica
        "GD", // Grenada
        "GY", // Guyana
        "JM", // Jamaica
        "KN", // Saint Kitts and Nevis
        "LC", // Saint Lucia
        "VC", // Saint Vincent and the Grenadines
        "AG", // Antigua and Barbuda
        "AI", // Anguilla
        "MS", // Montserrat
        "TC", // Turks and Caicos
        "VI", // US Virgin Islands
    )
    
    /**
     * National speed limits by country (in local units).
     * Used when OSM returns "national" as the maxspeed.
     */
    private val NATIONAL_LIMITS = mapOf(
        // MPH countries
        "GB" to 60,  // UK: 60 mph on single carriageway, 70 on motorway
        "US" to 55,  // US: varies by state, 55 is common default
        
        // KM/H countries (common values)
        "AU" to 100, // Australia
        "DE" to 100, // Germany (outside autobahn)
        "FR" to 80,  // France
        "ES" to 90,  // Spain
        "IT" to 90,  // Italy
        "NL" to 80,  // Netherlands
        "BE" to 70,  // Belgium
        "AT" to 100, // Austria
        "CH" to 80,  // Switzerland
        "IE" to 80,  // Ireland (km/h since 2005)
        "NZ" to 100, // New Zealand
        "CA" to 80,  // Canada
        "JP" to 60,  // Japan
        "KR" to 80,  // South Korea
        "CN" to 100, // China
        "IN" to 80,  // India
        "BR" to 80,  // Brazil
        "MX" to 80,  // Mexico
        "ZA" to 100, // South Africa
    )
    
    // Cache for country detection
    private var cachedCountryCode: String? = null
    private var cachedLat: Double = 0.0
    private var cachedLon: Double = 0.0
    private var cacheTimestamp: Long = 0
    private const val CACHE_DURATION_MS = 300_000L // 5 minutes
    private const val CACHE_DISTANCE_THRESHOLD = 10_000.0 // 10km before re-checking country
    
    /**
     * Detect the country code from GPS coordinates.
     * Uses Android's Geocoder with caching.
     * Falls back to device locale if geocoding fails.
     */
    fun detectCountry(context: Context, lat: Double, lon: Double): String {
        // Check cache first
        if (isCacheValid(lat, lon)) {
            return cachedCountryCode ?: getDeviceCountry()
        }
        
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            
            val countryCode = addresses?.firstOrNull()?.countryCode
            
            if (countryCode != null) {
                // Update cache
                cachedCountryCode = countryCode
                cachedLat = lat
                cachedLon = lon
                cacheTimestamp = System.currentTimeMillis()
                
                Log.d(TAG, "Detected country: $countryCode")
                countryCode
            } else {
                Log.w(TAG, "Geocoder returned no country, using device locale")
                getDeviceCountry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed, using device locale", e)
            getDeviceCountry()
        }
    }
    
    /**
     * Get the device's configured country as fallback.
     */
    private fun getDeviceCountry(): String {
        return Locale.getDefault().country.also {
            Log.d(TAG, "Using device locale country: $it")
        }
    }
    
    private fun isCacheValid(lat: Double, lon: Double): Boolean {
        if (cachedCountryCode == null) return false
        if (System.currentTimeMillis() - cacheTimestamp > CACHE_DURATION_MS) return false
        
        val distance = calculateDistance(cachedLat, cachedLon, lat, lon)
        return distance < CACHE_DISTANCE_THRESHOLD
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
    
    /**
     * Check if a country uses miles per hour.
     */
    fun usesMph(countryCode: String): Boolean {
        return countryCode.uppercase() in MPH_COUNTRIES
    }
    
    /**
     * Get the national speed limit for a country (in the country's local units).
     * Returns null if unknown.
     */
    fun getNationalLimit(countryCode: String): Int? {
        return NATIONAL_LIMITS[countryCode.uppercase()]
    }
    
    /**
     * Convert km/h to mph.
     */
    fun kmhToMph(kmh: Int): Int {
        return (kmh * KMH_TO_MPH).toInt()
    }
    
    /**
     * Convert mph to km/h.
     */
    fun mphToKmh(mph: Int): Int {
        return (mph * MPH_TO_KMH).toInt()
    }
    
    /**
     * Convert m/s to mph.
     */
    fun msToMph(ms: Float): Float {
        return ms * MS_TO_MPH.toFloat()
    }
    
    /**
     * Convert m/s to km/h.
     */
    fun msToKmh(ms: Float): Float {
        return ms * MS_TO_KMH.toFloat()
    }
    
    /**
     * Parse an OSM maxspeed value and return the speed in mph.
     * Handles explicit units (e.g., "80 km/h", "30 mph") and implicit units based on country.
     * 
     * @param maxspeed The raw maxspeed string from OSM
     * @param countryCode ISO country code for implicit unit detection
     * @return Speed in mph, or null if unparseable
     */
    fun parseSpeedLimitToMph(maxspeed: String, countryCode: String): Int? {
        if (maxspeed.isBlank()) return null
        
        val trimmed = maxspeed.trim().lowercase()
        val countryUsesMph = usesMph(countryCode)
        
        // Handle special cases
        when (trimmed) {
            "national" -> {
                val nationalLimit = getNationalLimit(countryCode) ?: return null
                return if (countryUsesMph) nationalLimit else kmhToMph(nationalLimit)
            }
            "walk", "living_street" -> return if (countryUsesMph) 20 else kmhToMph(20)
            "none", "unlimited" -> return null
        }
        
        // Extract numeric value
        val numberPattern = Regex("(\\d+)")
        val match = numberPattern.find(trimmed) ?: return null
        val value = match.groupValues[1].toIntOrNull() ?: return null
        
        // Check for explicit units
        return when {
            trimmed.contains("mph") -> value // Already in mph
            trimmed.contains("km") || trimmed.contains("kph") -> kmhToMph(value) // Convert from km/h
            else -> {
                // No explicit unit - use country's default
                if (countryUsesMph) value else kmhToMph(value)
            }
        }
    }
    
    /**
     * Get the display unit string for a country.
     */
    fun getUnitLabel(countryCode: String): String {
        return if (usesMph(countryCode)) "mph" else "km/h"
    }
    
    /**
     * Common speed limits by country (in local units).
     * Used for the speed limit selection grid.
     */
    private val COMMON_SPEED_LIMITS = mapOf(
        // MPH countries
        "GB" to listOf(20, 30, 40, 50, 60, 70),
        "US" to listOf(25, 35, 45, 55, 65, 75),
        
        // Europe (km/h)
        "DE" to listOf(30, 50, 70, 100, 120, 130),  // Germany
        "FR" to listOf(30, 50, 80, 110, 130),       // France
        "ES" to listOf(30, 50, 80, 90, 100, 120),   // Spain
        "IT" to listOf(30, 50, 70, 90, 110, 130),   // Italy
        "NL" to listOf(30, 50, 80, 100, 120, 130),  // Netherlands
        "BE" to listOf(30, 50, 70, 90, 120),        // Belgium
        "AT" to listOf(30, 50, 70, 100, 130),       // Austria
        "CH" to listOf(30, 50, 80, 100, 120),       // Switzerland
        "PL" to listOf(30, 50, 70, 90, 120, 140),   // Poland
        "CZ" to listOf(30, 50, 70, 90, 110, 130),   // Czech Republic
        "PT" to listOf(30, 50, 80, 100, 120),       // Portugal
        "SE" to listOf(30, 50, 70, 90, 110, 120),   // Sweden
        "NO" to listOf(30, 50, 70, 80, 90, 100),    // Norway
        "DK" to listOf(30, 50, 80, 110, 130),       // Denmark
        "FI" to listOf(30, 50, 80, 100, 120),       // Finland
        "IE" to listOf(30, 50, 60, 80, 100, 120),   // Ireland
        "GR" to listOf(30, 50, 70, 90, 110, 130),   // Greece
        
        // Asia-Pacific (km/h)
        "AU" to listOf(40, 50, 60, 80, 100, 110),   // Australia
        "NZ" to listOf(30, 50, 60, 80, 100),        // New Zealand
        "JP" to listOf(30, 40, 50, 60, 80, 100),    // Japan
        "KR" to listOf(30, 50, 60, 80, 100, 110),   // South Korea
        "CN" to listOf(30, 40, 60, 80, 100, 120),   // China
        "IN" to listOf(30, 40, 50, 60, 80, 100),    // India
        "SG" to listOf(40, 50, 60, 70, 80, 90),     // Singapore
        "MY" to listOf(30, 60, 80, 90, 110),        // Malaysia
        "TH" to listOf(30, 45, 60, 80, 90, 120),    // Thailand
        
        // Americas (km/h except US)
        "CA" to listOf(30, 50, 60, 80, 100, 110),   // Canada
        "MX" to listOf(30, 40, 60, 80, 100, 110),   // Mexico
        "BR" to listOf(30, 40, 60, 80, 100, 110),   // Brazil
        "AR" to listOf(40, 60, 80, 100, 110, 120),  // Argentina
        
        // Middle East & Africa (km/h)
        "ZA" to listOf(40, 60, 80, 100, 120),       // South Africa
        "AE" to listOf(40, 60, 80, 100, 120, 140),  // UAE
        "SA" to listOf(40, 50, 80, 100, 120),       // Saudi Arabia
        "IL" to listOf(30, 50, 60, 80, 90, 110),    // Israel
        "EG" to listOf(30, 45, 60, 90, 100),        // Egypt
    )
    
    // Default limits when country not in database
    private val DEFAULT_MPH_LIMITS = listOf(20, 30, 40, 50, 60, 70)
    private val DEFAULT_KMH_LIMITS = listOf(30, 50, 60, 80, 100, 120)
    
    /**
     * Get the common speed limits for a country (in local units).
     * Returns country-specific limits if known, otherwise defaults based on unit system.
     */
    fun getCommonSpeedLimits(countryCode: String): List<Int> {
        val code = countryCode.uppercase()
        return COMMON_SPEED_LIMITS[code] ?: if (usesMph(code)) DEFAULT_MPH_LIMITS else DEFAULT_KMH_LIMITS
    }
    
    /**
     * Merge discovered limits with known limits for a country.
     * Returns a sorted, deduplicated list of reasonable speed limits.
     */
    fun mergeSpeedLimits(countryCode: String, discoveredLimits: List<Int>): List<Int> {
        val knownLimits = getCommonSpeedLimits(countryCode)
        val usesMph = usesMph(countryCode)
        
        // Combine known and discovered, filter out unreasonable values
        val maxReasonable = if (usesMph) 85 else 150
        val minReasonable = if (usesMph) 5 else 10
        
        return (knownLimits + discoveredLimits)
            .filter { it in minReasonable..maxReasonable }
            .distinct()
            .sorted()
    }
}

