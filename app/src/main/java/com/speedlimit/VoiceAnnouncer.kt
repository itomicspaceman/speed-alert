package com.speedlimit

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Handles voice announcements using Android's Text-to-Speech.
 * 
 * This is a PREMIUM feature that provides spoken alerts for:
 * - Speed limit changes
 * - Unknown speed limit zones
 * - Over speed limit warnings
 * 
 * Uses the device's default language for auto-localization.
 */
class VoiceAnnouncer(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceAnnouncer"
        private const val PREFS_NAME = "voice_settings"
        
        // Preference keys for each announcement type
        const val PREF_ANNOUNCE_LIMIT_CHANGE = "announce_limit_change"
        const val PREF_ANNOUNCE_UNKNOWN_ZONE = "announce_unknown_zone"
        const val PREF_ANNOUNCE_OVER_LIMIT = "announce_over_limit"
        const val PREF_VOICE_ENABLED = "voice_enabled"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Track last announcements to avoid repeating
    private var lastAnnouncedLimit: Int = -1
    private var lastOverLimitAnnouncement: Long = 0
    private val overLimitCooldownMs = 30_000L // 30 seconds between "over limit" announcements

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Use device's default locale
            val result = tts?.setLanguage(Locale.getDefault())
            
            isInitialized = when (result) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.w(TAG, "TTS language not supported, falling back to English")
                    tts?.setLanguage(Locale.ENGLISH)
                    true
                }
                else -> true
            }
            
            // Slightly slower speech rate for clarity
            tts?.setSpeechRate(0.9f)
            
            Log.d(TAG, "TTS initialized: $isInitialized")
        } else {
            Log.e(TAG, "TTS initialization failed")
            isInitialized = false
        }
    }

    /**
     * Check if voice announcements are enabled (premium feature).
     */
    fun isEnabled(): Boolean {
        return prefs.getBoolean(PREF_VOICE_ENABLED, false)
    }

    /**
     * Check if a specific announcement type is enabled.
     */
    fun isAnnouncementEnabled(type: String): Boolean {
        return prefs.getBoolean(type, true)
    }

    /**
     * Set whether voice announcements are enabled.
     */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_VOICE_ENABLED, enabled).apply()
    }

    /**
     * Set whether a specific announcement type is enabled.
     */
    fun setAnnouncementEnabled(type: String, enabled: Boolean) {
        prefs.edit().putBoolean(type, enabled).apply()
    }

    /**
     * Announce a speed limit change.
     * Example: "Limit 30" or "Limit 50 kilometers per hour"
     */
    fun announceSpeedLimitChange(limit: Int, usesMph: Boolean) {
        if (!isEnabled() || !isInitialized) return
        if (!isAnnouncementEnabled(PREF_ANNOUNCE_LIMIT_CHANGE)) return
        if (limit == lastAnnouncedLimit) return // Don't repeat same limit
        
        lastAnnouncedLimit = limit
        
        // Keep it short and clear
        val unit = if (usesMph) "" else "kilometers per hour"
        val text = if (unit.isEmpty()) {
            "Limit $limit"
        } else {
            "Limit $limit $unit"
        }
        
        speak(text)
    }

    /**
     * Announce entering an unknown speed limit zone.
     * Example: "Limit unknown"
     */
    fun announceUnknownZone() {
        if (!isEnabled() || !isInitialized) return
        if (!isAnnouncementEnabled(PREF_ANNOUNCE_UNKNOWN_ZONE)) return
        if (lastAnnouncedLimit == -1) return // Don't repeat if already announced
        
        lastAnnouncedLimit = -1
        speak("Limit unknown")
    }

    /**
     * Announce when going over the speed limit.
     * Example: "Over limit"
     */
    fun announceOverLimit() {
        if (!isEnabled() || !isInitialized) return
        if (!isAnnouncementEnabled(PREF_ANNOUNCE_OVER_LIMIT)) return
        
        // Check cooldown
        val now = System.currentTimeMillis()
        if (now - lastOverLimitAnnouncement < overLimitCooldownMs) return
        
        lastOverLimitAnnouncement = now
        speak("Over limit")
    }

    /**
     * Play a preview of the voice (for settings screen).
     */
    fun playPreview(text: String) {
        if (!isInitialized) return
        speak(text)
    }

    /**
     * Speak the given text.
     */
    private fun speak(text: String) {
        if (!isInitialized) return
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speed_limit_announcement")
        Log.d(TAG, "Speaking: $text")
    }

    /**
     * Reset the announcement state (e.g., when monitoring stops).
     */
    fun reset() {
        lastAnnouncedLimit = -1
        lastOverLimitAnnouncement = 0
    }

    /**
     * Shut down TTS when no longer needed.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

