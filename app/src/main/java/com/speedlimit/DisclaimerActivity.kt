package com.speedlimit

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.speedlimit.databinding.ActivityDisclaimerBinding

/**
 * Shows a legal disclaimer that users must accept EVERY TIME the app starts.
 * This provides maximum legal protection - users cannot claim they forgot
 * or didn't understand the terms.
 */
class DisclaimerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDisclaimerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Always show disclaimer - no skipping, maximum legal protection
        binding = ActivityDisclaimerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupButtons()
    }

    private fun setupButtons() {
        binding.acceptButton.setOnClickListener {
            // Proceed to main app (no persistence - will show again next launch)
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

