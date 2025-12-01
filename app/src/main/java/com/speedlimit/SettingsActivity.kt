package com.speedlimit

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.speedlimit.databinding.ActivitySettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Settings screen with two sections:
 * - Free features (OSM account, contribution count)
 * - Premium features (coming soon - Ad-free, Voice, Customization)
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var osmContributor: OsmContributor
    private lateinit var contributionLog: ContributionLog
    
    // TODO: Replace with actual subscription check
    private var isPremium = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        osmContributor = OsmContributor(this)
        contributionLog = ContributionLog(this)
        
        setupUI()
        loadSettings()
    }

    override fun onResume() {
        super.onResume()
        // Refresh settings when returning from OsmSettingsActivity
        loadSettings()
    }

    private fun setupUI() {
        // Back button
        binding.backButton.setOnClickListener {
            finish()
        }
        
        // OSM Account row - navigate to OSM Settings screen
        binding.osmAccountRow.setOnClickListener {
            startActivity(Intent(this, OsmSettingsActivity::class.java))
        }
        
        // Contributions row - navigate to My Contributions screen
        binding.contributionRow.setOnClickListener {
            startActivity(Intent(this, MyContributionsActivity::class.java))
        }
        
        // Premium feature rows - show "Coming soon" message on tap
        binding.adFreeRow.setOnClickListener {
            showComingSoonPrompt()
        }
        
        binding.voiceRow.setOnClickListener {
            showComingSoonPrompt()
        }
        
        binding.customizationRow.setOnClickListener {
            showComingSoonPrompt()
        }
        
        // Subscribe button
        binding.subscribeButton.setOnClickListener {
            showComingSoonPrompt()
        }
        
        // Restore purchases
        binding.restorePurchases.setOnClickListener {
            showComingSoonPrompt()
        }
        
        // Show Tour Again
        binding.showTourRow.setOnClickListener {
            // Reset tour and navigate to MainActivity to show it
            val tourManager = TourManager(this)
            tourManager.resetTour()
            
            // Navigate to MainActivity and trigger tour
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("show_tour", true)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun loadSettings() {
        // OSM status
        if (osmContributor.isLoggedIn()) {
            val username = osmContributor.getUsername() ?: "Unknown"
            binding.osmStatusText.text = getString(R.string.settings_osm_connected, username)
            
            // Fetch OSM contribution count
            loadOsmContributionCount()
        } else {
            binding.osmStatusText.text = getString(R.string.settings_osm_not_connected)
            // Show local count only when not connected
            showLocalCount()
        }
    }

    private fun loadOsmContributionCount() {
        // Show loading state
        binding.contributionCountText.text = "..."
        
        CoroutineScope(Dispatchers.Main).launch {
            val changesets = osmContributor.fetchUserChangesets(limit = 100)
            val osmCount = changesets.size
            
            // Show OSM count (authoritative)
            binding.contributionCountText.text = getString(R.string.settings_contributions_osm, osmCount)
        }
    }

    private fun showLocalCount() {
        // When not connected to OSM, show local success/fail counts
        val successCount = contributionLog.getSuccessCount()
        val failedCount = contributionLog.getFailedCount()
        binding.contributionCountText.text = if (failedCount > 0) {
            "$successCount ✅  $failedCount ❌"
        } else {
            "$successCount"
        }
    }

    private fun showComingSoonPrompt() {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_SpeedLimit_Dialog)
            .setTitle("👑")
            .setMessage("Premium features are coming soon! Stay tuned for updates.")
            .setPositiveButton("OK", null)
            .show()
    }
}
