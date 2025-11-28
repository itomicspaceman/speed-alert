package com.speedlimit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Handles playing loud audio alerts when speed limit is exceeded.
 * Uses system tones for reliability (no external audio files needed).
 */
class AlertPlayer(private val context: Context) {

    companion object {
        const val TAG = "AlertPlayer"
        
        // Alert duration in milliseconds
        const val ALERT_DURATION_MS = 1000
        
        // Vibration pattern: wait, vibrate, wait, vibrate (ms)
        val VIBRATION_PATTERN = longArrayOf(0, 200, 100, 200, 100, 200)
    }

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null

    init {
        try {
            // Create tone generator at max volume using alarm stream
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            
            // Get vibrator service
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            // Get audio manager for volume control
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            Log.d(TAG, "AlertPlayer initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AlertPlayer", e)
        }
    }

    /**
     * Play a loud alert sound and vibration.
     */
    fun playAlert() {
        Log.d(TAG, "Playing speed alert!")
        
        // Ensure alarm volume is audible
        ensureAudibleVolume()
        
        // Play alert tone
        playAlertTone()
        
        // Vibrate device
        vibrateDevice()
    }

    private fun ensureAudibleVolume() {
        audioManager?.let { am ->
            val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            
            // Set to at least 70% volume for alerts
            val minAlertVolume = (maxVolume * 0.7).toInt()
            if (currentVolume < minAlertVolume) {
                am.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    minAlertVolume,
                    0 // No UI flag
                )
            }
        }
    }

    private fun playAlertTone() {
        try {
            // Play a sequence of tones for attention
            toneGenerator?.let { tg ->
                // First beep - high pitch urgent tone
                tg.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 300)
                
                // Schedule second beep (using simple delay approach)
                Thread {
                    try {
                        Thread.sleep(400)
                        tg.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 300)
                        Thread.sleep(400)
                        tg.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 300)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in tone sequence", e)
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alert tone", e)
            // Fallback to simpler tone
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_DTMF_0, ALERT_DURATION_MS)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback tone also failed", e2)
            }
        }
    }

    private fun vibrateDevice() {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(
                            VibrationEffect.createWaveform(
                                VIBRATION_PATTERN,
                                -1 // Don't repeat
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(VIBRATION_PATTERN, -1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating device", e)
        }
    }

    /**
     * Release resources when service is destroyed.
     */
    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
            Log.d(TAG, "AlertPlayer released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AlertPlayer", e)
        }
    }
}

