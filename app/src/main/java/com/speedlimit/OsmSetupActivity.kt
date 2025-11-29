package com.speedlimit

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.speedlimit.databinding.ActivityOsmSetupBinding
import com.speedlimit.databinding.DialogOsmLoginSuccessBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Post-disclaimer OSM setup screen.
 * Encourages users to connect their OSM account while they're not driving.
 * This is the ideal time to set up since they need to interact with OSM website.
 */
class OsmSetupActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OsmSetupActivity"
        const val EXTRA_FROM_DISCLAIMER = "from_disclaimer"
    }

    private lateinit var binding: ActivityOsmSetupBinding
    private lateinit var osmContributor: OsmContributor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOsmSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        osmContributor = OsmContributor(this)

        // If already connected, go straight to main
        if (osmContributor.isLoggedIn()) {
            goToMain()
            return
        }

        setupUI()
        
        // Handle OAuth callback if this was launched from redirect
        handleOAuthIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }
    
    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        
        // Check if this is an OAuth callback
        if (data.scheme == "speedlimit" && data.host == "oauth" && data.path == "/callback") {
            val code = data.getQueryParameter("code")
            if (code != null) {
                Log.d(TAG, "Received OAuth callback with code")
                CoroutineScope(Dispatchers.Main).launch {
                    val success = osmContributor.handleOAuthCallback(code)
                    if (success) {
                        // Show success message then go to main
                        showLoginSuccessDialog()
                    }
                }
            }
        }
    }
    
    private fun showLoginSuccessDialog() {
        val dialogBinding = DialogOsmLoginSuccessBinding.inflate(LayoutInflater.from(this))
        
        val username = osmContributor.getUsername() ?: "there"
        dialogBinding.messageText.text = getString(R.string.osm_login_success_message, username)
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_SpeedLimit_Dialog)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        dialogBinding.greatButton.setOnClickListener {
            dialog.dismiss()
            goToMain()
        }
        
        dialog.show()
    }

    private fun setupUI() {
        binding.connectButton.setOnClickListener {
            // Start OAuth flow - user will be redirected back after auth
            osmContributor.startLogin()
        }

        binding.skipButton.setOnClickListener {
            goToMain()
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Check if user completed OAuth while away (in case callback went elsewhere)
        if (osmContributor.isLoggedIn()) {
            goToMain()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

