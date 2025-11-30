package com.speedlimit

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.speedlimit.databinding.ActivitySettingsBinding

/**
 * Settings screen with two sections:
 * - Free features (OSM account, contribution count)
 * - Premium features (voice announcements - locked for non-subscribers)
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var osmContributor: OsmContributor
    private lateinit var contributionLog: ContributionLog
    private lateinit var voiceAnnouncer: VoiceAnnouncer
    
    // TODO: Replace with actual subscription check
    private var isPremium = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        osmContributor = OsmContributor(this)
        contributionLog = ContributionLog(this)
        voiceAnnouncer = VoiceAnnouncer(this)
        
        setupUI()
        loadSettings()
    }

    private fun setupUI() {
        // Back button
        binding.backButton.setOnClickListener {
            finish()
        }
        
        // OSM Account row
        binding.osmAccountRow.setOnClickListener {
            if (osmContributor.isLoggedIn()) {
                // Show logout dialog
                showLogoutDialog()
            } else {
                // Start OAuth flow
                osmContributor.startLogin()
            }
        }
        
        // Contributions row - navigate to My Contributions screen
        binding.contributionRow.setOnClickListener {
            startActivity(Intent(this, MyContributionsActivity::class.java))
        }
        
        // Voice icon - tap to preview
        binding.voiceIcon.setOnClickListener {
            if (isPremium || voiceAnnouncer.isEnabled()) {
                voiceAnnouncer.playPreview("Limit 30")
            }
        }
        
        // Voice master switch
        binding.voiceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isPremium) {
                // Show subscribe prompt
                binding.voiceSwitch.isChecked = false
                showSubscribePrompt()
                return@setOnCheckedChangeListener
            }
            
            voiceAnnouncer.setEnabled(isChecked)
            binding.voiceOptionsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        // Voice sub-options
        binding.voiceLimitChangeSwitch.setOnCheckedChangeListener { _, isChecked ->
            voiceAnnouncer.setAnnouncementEnabled(VoiceAnnouncer.PREF_ANNOUNCE_LIMIT_CHANGE, isChecked)
        }
        
        binding.voiceUnknownSwitch.setOnCheckedChangeListener { _, isChecked ->
            voiceAnnouncer.setAnnouncementEnabled(VoiceAnnouncer.PREF_ANNOUNCE_UNKNOWN_ZONE, isChecked)
        }
        
        binding.voiceOverLimitSwitch.setOnCheckedChangeListener { _, isChecked ->
            voiceAnnouncer.setAnnouncementEnabled(VoiceAnnouncer.PREF_ANNOUNCE_OVER_LIMIT, isChecked)
        }
        
        // Subscribe button
        binding.subscribeButton.setOnClickListener {
            // TODO: Launch Google Play billing flow
            showSubscribePrompt()
        }
        
        // Restore purchases
        binding.restorePurchases.setOnClickListener {
            // TODO: Implement restore purchases
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
        } else {
            binding.osmStatusText.text = getString(R.string.settings_osm_not_connected)
        }
        
        // Contribution count (from local log)
        val successCount = contributionLog.getSuccessCount()
        val failedCount = contributionLog.getFailedCount()
        binding.contributionCountText.text = if (failedCount > 0) {
            "$successCount ✅  $failedCount ❌"
        } else {
            "$successCount"
        }
        
        // Premium features state
        updatePremiumUI()
    }

    private fun updatePremiumUI() {
        if (isPremium) {
            // Show unlocked state
            binding.voiceSwitch.visibility = View.VISIBLE
            binding.voiceLock.visibility = View.GONE
            binding.subscribeButton.visibility = View.GONE
            binding.restorePurchases.visibility = View.GONE
            
            // Load voice settings
            val voiceEnabled = voiceAnnouncer.isEnabled()
            binding.voiceSwitch.isChecked = voiceEnabled
            binding.voiceOptionsContainer.visibility = if (voiceEnabled) View.VISIBLE else View.GONE
            
            binding.voiceLimitChangeSwitch.isChecked = voiceAnnouncer.isAnnouncementEnabled(VoiceAnnouncer.PREF_ANNOUNCE_LIMIT_CHANGE)
            binding.voiceUnknownSwitch.isChecked = voiceAnnouncer.isAnnouncementEnabled(VoiceAnnouncer.PREF_ANNOUNCE_UNKNOWN_ZONE)
            binding.voiceOverLimitSwitch.isChecked = voiceAnnouncer.isAnnouncementEnabled(VoiceAnnouncer.PREF_ANNOUNCE_OVER_LIMIT)
        } else {
            // Show locked state
            binding.voiceSwitch.visibility = View.GONE
            binding.voiceLock.visibility = View.VISIBLE
            binding.voiceOptionsContainer.visibility = View.GONE
            binding.subscribeButton.visibility = View.VISIBLE
            binding.restorePurchases.visibility = View.VISIBLE
        }
    }

    private fun showLogoutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_SpeedLimit_Dialog)
            .setTitle("🗺️")
            .setMessage("Disconnect from OpenStreetMap?")
            .setPositiveButton("Disconnect") { _, _ ->
                osmContributor.logout()
                loadSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSubscribePrompt() {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_SpeedLimit_Dialog)
            .setTitle("👑")
            .setMessage("Voice announcements are a premium feature. Subscribe to unlock!")
            .setPositiveButton("Subscribe") { _, _ ->
                // TODO: Launch billing flow
            }
            .setNegativeButton("Not Now", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceAnnouncer.shutdown()
    }
}

