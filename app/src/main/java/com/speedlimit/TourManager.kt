package com.speedlimit

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import androidx.core.content.ContextCompat
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence

/**
 * Manages the app tour/onboarding experience using TapTargetView.
 * Shows spotlight highlights on key UI elements to guide new users.
 */
class TourManager(private val activity: Activity) {

    companion object {
        private const val PREFS_NAME = "tour_prefs"
        private const val KEY_TOUR_COMPLETED = "tour_completed"
    }

    private val prefs: SharedPreferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Check if the tour has been completed.
     */
    fun isTourCompleted(): Boolean {
        return prefs.getBoolean(KEY_TOUR_COMPLETED, false)
    }

    /**
     * Mark the tour as completed.
     */
    fun markTourCompleted() {
        prefs.edit().putBoolean(KEY_TOUR_COMPLETED, true).apply()
    }

    /**
     * Reset tour so it shows again.
     */
    fun resetTour() {
        prefs.edit().putBoolean(KEY_TOUR_COMPLETED, false).apply()
    }

    /**
     * Start the tour sequence.
     * 
     * @param toggleButton The play/stop button
     * @param speedLimitContainer The speed limit grid container
     * @param floatingButton The floating overlay button
     * @param settingsButton The settings button
     * @param onTourComplete Called when tour finishes
     */
    fun startTour(
        toggleButton: View,
        speedLimitContainer: View,
        floatingButton: View,
        settingsButton: View,
        onTourComplete: () -> Unit = {}
    ) {
        val primaryColor = ContextCompat.getColor(activity, R.color.info_blue)
        val textColor = android.graphics.Color.WHITE
        val dimColor = android.graphics.Color.parseColor("#99000000")

        TapTargetSequence(activity)
            .targets(
                // Step 1: Start/Stop button
                TapTarget.forView(
                    toggleButton,
                    activity.getString(R.string.tour_start_title),
                    activity.getString(R.string.tour_start_description)
                )
                    .outerCircleColor(R.color.info_blue)
                    .outerCircleAlpha(0.96f)
                    .targetCircleColor(android.R.color.white)
                    .titleTextSize(24)
                    .titleTextColor(android.R.color.white)
                    .descriptionTextSize(16)
                    .descriptionTextColor(android.R.color.white)
                    .textTypeface(android.graphics.Typeface.SANS_SERIF)
                    .dimColor(android.R.color.black)
                    .drawShadow(true)
                    .cancelable(true)
                    .tintTarget(false)
                    .transparentTarget(false)
                    .targetRadius(60),

                // Step 2: Speed limit grid
                TapTarget.forView(
                    speedLimitContainer,
                    activity.getString(R.string.tour_limits_title),
                    activity.getString(R.string.tour_limits_description)
                )
                    .outerCircleColor(R.color.info_blue)
                    .outerCircleAlpha(0.96f)
                    .targetCircleColor(android.R.color.white)
                    .titleTextSize(24)
                    .titleTextColor(android.R.color.white)
                    .descriptionTextSize(16)
                    .descriptionTextColor(android.R.color.white)
                    .dimColor(android.R.color.black)
                    .drawShadow(true)
                    .cancelable(true)
                    .tintTarget(false)
                    .transparentTarget(true)
                    .targetRadius(120),

                // Step 3: Floating mode button
                TapTarget.forView(
                    floatingButton,
                    activity.getString(R.string.tour_floating_title),
                    activity.getString(R.string.tour_floating_description)
                )
                    .outerCircleColor(R.color.info_blue)
                    .outerCircleAlpha(0.96f)
                    .targetCircleColor(android.R.color.white)
                    .titleTextSize(24)
                    .titleTextColor(android.R.color.white)
                    .descriptionTextSize(16)
                    .descriptionTextColor(android.R.color.white)
                    .dimColor(android.R.color.black)
                    .drawShadow(true)
                    .cancelable(true)
                    .tintTarget(false)
                    .transparentTarget(false)
                    .targetRadius(50),

                // Step 4: Settings button
                TapTarget.forView(
                    settingsButton,
                    activity.getString(R.string.tour_settings_title),
                    activity.getString(R.string.tour_settings_description)
                )
                    .outerCircleColor(R.color.info_blue)
                    .outerCircleAlpha(0.96f)
                    .targetCircleColor(android.R.color.white)
                    .titleTextSize(24)
                    .titleTextColor(android.R.color.white)
                    .descriptionTextSize(16)
                    .descriptionTextColor(android.R.color.white)
                    .dimColor(android.R.color.black)
                    .drawShadow(true)
                    .cancelable(true)
                    .tintTarget(false)
                    .transparentTarget(false)
                    .targetRadius(50)
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    markTourCompleted()
                    onTourComplete()
                }

                override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) {
                    // Optional: Track progress
                }

                override fun onSequenceCanceled(lastTarget: TapTarget) {
                    // User cancelled early - still mark as completed so they're not pestered
                    markTourCompleted()
                    onTourComplete()
                }
            })
            .continueOnCancel(false)
            .considerOuterCircleCanceled(true)
            .start()
    }
}

