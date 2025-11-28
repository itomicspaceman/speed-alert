package com.speedlimit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.speedlimit.databinding.ActivityDisclaimerBinding

/**
 * Shows a legal disclaimer that users must accept before using the app.
 * Acceptance is stored in SharedPreferences and only shown once.
 */
class DisclaimerActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "speedlimit_prefs"
        private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"
        
        /**
         * Check if user has already accepted the disclaimer.
         */
        fun hasAcceptedDisclaimer(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)
        }
    }

    private lateinit var binding: ActivityDisclaimerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // If already accepted, skip straight to main
        if (hasAcceptedDisclaimer(this)) {
            launchMainActivity()
            return
        }
        
        binding = ActivityDisclaimerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupButtons()
    }

    private fun setupButtons() {
        binding.acceptButton.setOnClickListener {
            // Save acceptance
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).apply()
            
            // Proceed to main app
            launchMainActivity()
        }
        
        binding.rejectButton.setOnClickListener {
            // User declined - close the app
            finishAffinity()
        }
    }

    private fun launchMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

