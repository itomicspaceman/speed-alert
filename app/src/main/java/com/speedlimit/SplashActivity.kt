package com.speedlimit

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import androidx.appcompat.app.AppCompatActivity

/**
 * Custom splash screen showing app branding before disclaimer.
 * Displays app icon, "Speed/Limit" name, and Itomic Digital credit.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Animate the icon with a subtle scale and fade
        val icon = findViewById<android.widget.ImageView>(R.id.splashIcon)
        val appName = findViewById<android.widget.TextView>(R.id.appNameText)
        
        // Icon animation - scale up and fade in
        val scaleAnimation = ScaleAnimation(
            0.8f, 1.0f, 0.8f, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 600
        }
        
        val fadeAnimation = AlphaAnimation(0f, 1f).apply {
            duration = 600
        }
        
        val iconAnimSet = AnimationSet(true).apply {
            addAnimation(scaleAnimation)
            addAnimation(fadeAnimation)
        }
        
        icon.startAnimation(iconAnimSet)
        
        // App name fades in slightly delayed
        appName.alpha = 0f
        appName.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(300)
            .start()

        // Navigate to disclaimer after a brief display
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, DisclaimerActivity::class.java))
            finish()
            // Smooth fade transition (suppress deprecation - still works, replacement requires API 34+)
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 1800) // 1.8 seconds total splash time
    }
}

