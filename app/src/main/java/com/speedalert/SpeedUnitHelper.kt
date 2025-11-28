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
}

