package com.speedlimit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.speedlimit.databinding.ActivityOsmSettingsBinding

/**
 * OSM Settings screen showing account details and options.
 * Handles both connected and disconnected states.
 */
class OsmSettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OsmSettingsActivity"
        private const val OSM_BASE_URL = "https://www.openstreetmap.org"
        private const val OSM_ABOUT_URL = "https://www.openstreetmap.org/about"
        private const val OSM_LOGOUT_URL = "https://www.openstreetmap.org/logout"
    }
    
    // Flag to track if we're in the middle of switching accounts
    private var isSwitchingAccount = false

    private lateinit var binding: ActivityOsmSettingsBinding
    private lateinit var osmContributor: OsmContributor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOsmSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        osmContributor = OsmContributor(this)

        setupUI()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        // Update UI in case user completed OAuth while away
        updateUI()
        
        // If we were switching accounts and came back, start the login flow
        if (isSwitchingAccount) {
            isSwitchingAccount = false
            // Small delay to let the browser close
            binding.root.postDelayed({
                osmContributor.startLogin()
            }, 500)
        }
    }

    private fun setupUI() {
        // Back button
        binding.backButton.setOnClickListener {
            finish()
        }

        // === Connected State Options ===
        
        // View Profile
        binding.viewProfileRow.setOnClickListener {
            openOsmProfile()
        }

        // Edit History
        binding.editHistoryRow.setOnClickListener {
            openEditHistory()
        }

        // About OSM (connected)
        binding.aboutOsmRow.setOnClickListener {
            openAboutOsm()
        }

        // Switch Account
        binding.switchAccountRow.setOnClickListener {
            showSwitchAccountDialog()
        }

        // Disconnect
        binding.disconnectRow.setOnClickListener {
            showDisconnectDialog()
        }

        // === Disconnected State Options ===

        // Connect button
        binding.connectButton.setOnClickListener {
            osmContributor.startLogin()
        }

        // About OSM (disconnected)
        binding.aboutOsmRowDisconnected.setOnClickListener {
            openAboutOsm()
        }
    }

    private fun updateUI() {
        val isLoggedIn = osmContributor.isLoggedIn()
        val username = osmContributor.getUsername()

        if (isLoggedIn && username != null) {
            // Show connected state
            binding.connectionStatus.text = getString(R.string.osm_settings_connected_as, username)
            binding.connectionStatus.setTextColor(0xFF4CAF50.toInt()) // Green
            binding.connectedContainer.visibility = View.VISIBLE
            binding.disconnectedContainer.visibility = View.GONE
        } else {
            // Show disconnected state
            binding.connectionStatus.text = getString(R.string.osm_settings_not_connected)
            binding.connectionStatus.setTextColor(0xFF888888.toInt()) // Gray
            binding.connectedContainer.visibility = View.GONE
            binding.disconnectedContainer.visibility = View.VISIBLE
        }
    }

    private fun openOsmProfile() {
        val username = osmContributor.getUsername() ?: return
        val url = "$OSM_BASE_URL/user/$username"
        openUrl(url)
    }

    private fun openEditHistory() {
        val username = osmContributor.getUsername() ?: return
        val url = "$OSM_BASE_URL/user/$username/history"
        openUrl(url)
    }

    private fun openAboutOsm() {
        openUrl(OSM_ABOUT_URL)
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun showSwitchAccountDialog() {
        AlertDialog.Builder(this, R.style.Theme_SpeedLimit_Dialog)
            .setTitle(R.string.osm_settings_switch_confirm)
            .setMessage(R.string.osm_settings_switch_message)
            .setPositiveButton(R.string.osm_settings_switch_account) { _, _ ->
                switchAccount()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun switchAccount() {
        // 1. Clear local token
        osmContributor.logout()
        updateUI()
        
        // 2. Set flag so we start login after returning from logout
        isSwitchingAccount = true
        
        // 3. Open OSM logout URL to clear browser session
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(this, Uri.parse(OSM_LOGOUT_URL))
    }

    private fun showDisconnectDialog() {
        AlertDialog.Builder(this, R.style.Theme_SpeedLimit_Dialog)
            .setTitle(R.string.osm_settings_disconnect_confirm)
            .setMessage(R.string.osm_settings_disconnect_message)
            .setPositiveButton(R.string.osm_settings_disconnect) { _, _ ->
                osmContributor.logout()
                updateUI()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

