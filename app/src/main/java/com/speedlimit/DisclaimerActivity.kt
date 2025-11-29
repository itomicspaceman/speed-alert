package com.speedlimit

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.speedlimit.databinding.ActivityDisclaimerBinding

/**
 * Shows a legal disclaimer that users must accept EVERY TIME the app starts.
 * This provides maximum legal protection - users cannot claim they forgot
 * or didn't understand the terms.
 */
class DisclaimerActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_OSM_SETUP_SHOWN = "osm_setup_shown"
    }

    private lateinit var binding: ActivityDisclaimerBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // Always show disclaimer - no skipping, maximum legal protection
        binding = ActivityDisclaimerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupButtons()
    }

    private fun setupButtons() {
        binding.acceptButton.setOnClickListener {
            // Check if we should show OSM setup (first time after disclaimer)
            val osmContributor = OsmContributor(this)
            val osmSetupShown = prefs.getBoolean(KEY_OSM_SETUP_SHOWN, false)
            
            if (!osmSetupShown && !osmContributor.isLoggedIn()) {
                // First time - show OSM setup to encourage connection while not driving
                prefs.edit().putBoolean(KEY_OSM_SETUP_SHOWN, true).apply()
                launchOsmSetup()
            } else {
                // Already seen setup or already logged in - go to main
                launchMainActivity()
            }
        }
        
        binding.rejectButton.setOnClickListener {
            // User declined - close the app
            finishAffinity()
        }
    }

    private fun launchOsmSetup() {
        startActivity(Intent(this, OsmSetupActivity::class.java))
        finish()
    }

    private fun launchMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

