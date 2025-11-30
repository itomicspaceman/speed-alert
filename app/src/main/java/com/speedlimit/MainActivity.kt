package com.speedlimit

import android.Manifest
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.flexbox.FlexboxLayout
import com.speedlimit.databinding.ActivityMainBinding
import com.speedlimit.databinding.DialogOsmContributionBinding
import com.speedlimit.databinding.DialogOsmSuccessBinding
import com.speedlimit.databinding.DialogOsmWhyConnectBinding
import com.speedlimit.databinding.DialogOsmLoginSuccessBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var osmContributor: OsmContributor
    private lateinit var contributionLog: ContributionLog
    private lateinit var tourManager: TourManager
    private lateinit var speedLimitProvider: SpeedLimitProvider  // For way ID lookup when contributing
    private lateinit var voiceAnnouncer: VoiceAnnouncer  // For "Thank you!" on successful contribution
    private var isMonitoring = false
    private var isFlashing = false
    private var flashAnimator: ValueAnimator? = null
    private var pendingFloatingMode = false  // Flag to launch floating mode after monitoring starts

    // Dynamically created speed limit buttons
    private val speedLimitButtons = mutableListOf<TextView>()
    
    // Current display limits and country
    private var currentDisplayLimits = listOf<Int>()
    private var currentCountryCode = "GB"
    
    // Current way ID for crowdsourcing (received from SpeedMonitorService)
    private var currentWayId: Long = -1L
    
    // Currently detected speed limit (for "same limit" check)
    private var currentDetectedLimit: Int = -1
    
    // Current location (for validation)
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var currentGpsAccuracy: Float = Float.MAX_VALUE
    
    // Pending contribution after OAuth login
    private var pendingContributionLimit: Int = -1

    // Broadcast receiver for speed updates from the service
    private val speedUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val speedMph = it.getFloatExtra(SpeedMonitorService.EXTRA_SPEED, 0f)
                val speedLimitMph = it.getIntExtra(SpeedMonitorService.EXTRA_SPEED_LIMIT, -1)
                val isOverLimit = it.getBooleanExtra(SpeedMonitorService.EXTRA_IS_OVER_LIMIT, false)
                val countryCode = it.getStringExtra(SpeedMonitorService.EXTRA_COUNTRY_CODE) ?: "GB"
                val wayId = it.getLongExtra(SpeedMonitorService.EXTRA_WAY_ID, -1L)
                val latitude = it.getDoubleExtra(SpeedMonitorService.EXTRA_LATITUDE, 0.0)
                val longitude = it.getDoubleExtra(SpeedMonitorService.EXTRA_LONGITUDE, 0.0)
                val accuracy = it.getFloatExtra(SpeedMonitorService.EXTRA_ACCURACY, Float.MAX_VALUE)
                
                // Store way ID for crowdsourcing
                if (wayId > 0) {
                    currentWayId = wayId
                }
                
                // Store current location for validation
                currentLatitude = latitude
                currentLongitude = longitude
                currentGpsAccuracy = accuracy
                
                // Store currently detected limit for "same limit" check
                currentDetectedLimit = speedLimitMph
                
                updateSpeedDisplay(speedMph, speedLimitMph, isOverLimit, countryCode)
            }
        }
    }

    // Permission launcher for location
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocationPermission()
            } else {
                checkNotificationPermissionAndStart()
            }
        } else {
            showPermissionDeniedDialog()
        }
    }

    // Permission launcher for background location (Android 10+)
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Proceed regardless - app will work with reduced functionality if denied
        checkNotificationPermissionAndStart()
    }

    // Permission launcher for notifications (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Proceed regardless - notification just won't show if denied
        startSpeedMonitoring()
    }
    
    // Permission launcher for overlay (floating window)
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startFloatingMode()
        }
        // If denied, user will understand - no toast needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Analytics and Crashlytics
        AnalyticsHelper.initialize(this)
        
        // Initialize OSM contributor
        osmContributor = OsmContributor(this)
        
        // Initialize contribution log
        contributionLog = ContributionLog(this)
        
        // Initialize speed limit provider (for way ID lookup when contributing)
        speedLimitProvider = SpeedLimitProvider(this)
        
        // Initialize voice announcer (for "Thank you!" on successful contribution)
        voiceAnnouncer = VoiceAnnouncer(this)
        
        // Initialize tour manager
        tourManager = TourManager(this)

        setupUI()
        setupSpeedLimitGrid(currentCountryCode)
        checkIfServiceRunning()
        
        // Handle OAuth callback if app was launched from OSM
        handleOAuthIntent(intent)
        
        // Show tour on first launch OR if triggered from Settings
        val showTourFromSettings = intent?.getBooleanExtra("show_tour", false) ?: false
        if (!tourManager.isTourCompleted() || showTourFromSettings) {
            binding.root.post {
                startAppTour()
            }
        }
    }
    
    /**
     * Start the app tour to guide new users.
     */
    fun startAppTour() {
        tourManager.startTour(
            toggleButton = binding.toggleButton,
            speedLimitContainer = binding.speedLimitContainer,
            floatingButton = binding.floatingModeButton,
            settingsButton = binding.settingsButton
        )
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the intent for future reference
        handleOAuthIntent(intent)
        
        // Check if we should show the tour (from Settings -> Show Tour Again)
        val showTour = intent.getBooleanExtra("show_tour", false)
        if (showTour) {
            binding.root.post {
                startAppTour()
            }
        }
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
                        // Show login success dialog
                        showLoginSuccessDialog()
                    }
                }
            }
        }
    }
    
    /**
     * Show login success dialog after OAuth completes.
     * After dismissing, auto-submits any pending contribution.
     */
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
            // If we had a pending contribution, submit it silently now
            if (pendingContributionLimit > 0) {
                val limit = pendingContributionLimit
                pendingContributionLimit = -1
                
                // Now try the submission again (will go through validation)
                onSpeedLimitSelected(limit)
            }
        }
        
        dialog.show()
        setDialogFullWidth(dialog)
    }

    override fun onResume() {
        super.onResume()
        
        // Stop floating service if running (user returned to app)
        if (FloatingSpeedService.isRunning) {
            stopService(Intent(this, FloatingSpeedService::class.java))
        }
        
        val filter = IntentFilter(SpeedMonitorService.ACTION_SPEED_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(speedUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(speedUpdateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(speedUpdateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFlashing()
        voiceAnnouncer.shutdown()
    }

    private fun setupUI() {
        binding.toggleButton.setOnClickListener {
            if (isMonitoring) {
                stopSpeedMonitoring()
            } else {
                checkPermissionsAndStart()
            }
        }
        
        binding.floatingModeButton.setOnClickListener {
            if (isMonitoring) {
                checkOverlayPermissionAndStartFloating()
            } else {
                // Start monitoring first, then go to floating mode
                pendingFloatingMode = true
                checkPermissionsAndStart()
            }
        }
        
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    
    private fun checkOverlayPermissionAndStartFloating() {
        if (Settings.canDrawOverlays(this)) {
            startFloatingMode()
        } else {
            // Request overlay permission
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("To display speed over other apps, please grant the 'Display over other apps' permission.")
                .setPositiveButton("Grant") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    overlayPermissionLauncher.launch(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun startFloatingMode() {
        // Start the floating service as foreground
        ContextCompat.startForegroundService(this, Intent(this, FloatingSpeedService::class.java))
        
        // Minimize the app (go to home screen)
        moveTaskToBack(true)
    }

    /**
     * Dynamically creates speed limit buttons based on country.
     */
    private fun setupSpeedLimitGrid(countryCode: String) {
        val limits = SpeedUnitHelper.getCommonSpeedLimits(countryCode)
        
        // Only rebuild if limits changed
        if (limits == currentDisplayLimits) return
        
        currentDisplayLimits = limits
        currentCountryCode = countryCode
        
        // Clear existing buttons
        binding.speedLimitContainer.removeAllViews()
        speedLimitButtons.clear()
        
        // Create button for each limit
        limits.forEach { limit ->
            val button = createSpeedLimitButton(limit)
            binding.speedLimitContainer.addView(button)
            speedLimitButtons.add(button)
        }
    }

    /**
     * Creates a styled TextView button for a speed limit value.
     * 
     * Layout strategy for international flexibility:
     * - FlexboxLayout handles wrapping naturally based on content width
     * - 2-digit numbers (mph): typically fit 3 per row
     * - 3-digit numbers (km/h like 100, 120): naturally take more space, may fit 2-3 per row
     * - Moderate padding expands touch target without forcing layout changes
     * - Works for 5-7 speed limits across different countries
     */
    private fun createSpeedLimitButton(limit: Int): TextView {
        val button = TextView(this).apply {
            text = limit.toString()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f)
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            gravity = Gravity.CENTER
            
            // Height: generous for easy tapping
            val height = dpToPx(95)
            
            // Margins: visual spacing between buttons (small = less dead space)
            val marginH = dpToPx(8)
            val marginV = dpToPx(6)
            
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                height
            ).apply {
                setMargins(marginH, marginV, marginH, marginV)
                // No minWidth - let content determine width naturally
                // This allows 3-digit numbers to be wider than 2-digit
            }
            
            // Padding: expands touch target (clickable area)
            // Horizontal: generous for easier side tapping
            // Vertical: moderate, height already generous
            setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))
            
            // Make it clickable with visual feedback
            setBackgroundResource(android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
            
            setOnClickListener {
                onSpeedLimitSelected(limit)
            }
        }
        return button
    }

    /**
     * Called when user taps a speed limit to contribute to OpenStreetMap.
     * Implements silent validation and one-tap submission for safe driving UX.
     */
    private fun onSpeedLimitSelected(limit: Int) {
        val button = findButtonForLimit(limit) ?: return
        val unit = if (SpeedUnitHelper.usesMph(currentCountryCode)) "mph" else "km/h"
        
        // === VALIDATION CHECKS ===
        
        // 1. Skip if same as currently detected limit (nothing to contribute)
        if (limit == currentDetectedLimit && currentDetectedLimit > 0) {
            // Silently skip - the limit is already correct
            logAttempt(limit, unit, ContributionLog.Status.SKIPPED_SAME_LIMIT, "Same as detected limit")
            return
        }
        
        // 2. Check if logged into OSM
        if (!osmContributor.isLoggedIn()) {
            // Not logged in - show prompt (this is the ONLY time we show a dialog)
            showContributionDialog(limit)
            return
        }
        
        // 3. Check rate limiting (time + distance)
        val rateLimitReason = contributionLog.canSubmit(currentLatitude, currentLongitude)
        if (rateLimitReason != null) {
            rejectContribution(button, limit, unit, ContributionLog.Status.FAILED_RATE_LIMITED, 
                rateLimitReason, getString(R.string.voice_rejected_wait))
            return
        }
        
        // 4. Check GPS accuracy
        if (currentGpsAccuracy > 50f || currentGpsAccuracy == Float.MAX_VALUE) {
            val accuracyMessage = if (currentGpsAccuracy >= Float.MAX_VALUE - 1) {
                "GPS not available yet"
            } else {
                "GPS accuracy: ${currentGpsAccuracy.toInt()}m (need <50m)"
            }
            rejectContribution(button, limit, unit, ContributionLog.Status.FAILED_GPS_POOR,
                accuracyMessage, getString(R.string.voice_rejected_gps))
            return
        }
        
        // 5. Check if location is valid
        if (currentLatitude == 0.0 && currentLongitude == 0.0) {
            rejectContribution(button, limit, unit, ContributionLog.Status.FAILED_GPS_POOR,
                "No location available", getString(R.string.voice_rejected_no_location))
            return
        }
        
        // Note: Speed value validation not needed - UI only offers pre-defined valid limits
        
        // 6. Check if we have a valid way ID - if not, try to find one with validation
        if (currentWayId <= 0) {
            // Launch coroutine to find and validate the nearest road
            CoroutineScope(Dispatchers.Main).launch {
                val roadResult = speedLimitProvider.findNearestRoad(currentLatitude, currentLongitude)
                
                if (roadResult == null) {
                    rejectContribution(button, limit, unit, ContributionLog.Status.FAILED_NO_WAY,
                        "No road detected at location", getString(R.string.voice_rejected_no_road))
                    return@launch
                }
                
                // Check if this road type is appropriate for speed limits
                if (!roadResult.isValidForSpeedLimit) {
                    val roadTypeName = roadResult.highwayType.replace("_", " ")
                    rejectContribution(button, limit, unit, ContributionLog.Status.FAILED_INVALID_WAY,
                        "Not a road ($roadTypeName)", getString(R.string.voice_rejected_not_road))
                    return@launch
                }
                
                // All good - submit
                currentWayId = roadResult.wayId
                submitSpeedLimitSilently(limit, unit, button)
            }
            return
        }
        
        // === ALL CHECKS PASSED - SUBMIT SILENTLY ===
        submitSpeedLimitSilently(limit, unit, button)
    }
    
    /**
     * Find the button view for a given speed limit value.
     */
    private fun findButtonForLimit(limit: Int): TextView? {
        return speedLimitButtons.getOrNull(currentDisplayLimits.indexOf(limit))
    }
    
    /**
     * Flash a button green (success) or red (failure).
     */
    private fun flashButton(button: TextView, success: Boolean) {
        val originalColor = button.currentTextColor
        val flashColor = if (success) Color.parseColor("#00FF00") else Color.parseColor("#FF0000")
        
        // Flash animation
        val animator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 500
            addUpdateListener { 
                val fraction = it.animatedValue as Float
                if (fraction > 0.5f) {
                    button.setTextColor(flashColor)
                } else {
                    button.setTextColor(originalColor)
                }
            }
        }
        animator.start()
        
        // Reset to appropriate color after animation
        button.postDelayed({
            // If this limit is currently detected, show it highlighted
            if (currentDisplayLimits.getOrNull(speedLimitButtons.indexOf(button)) == currentDetectedLimit) {
                button.setTextColor(ContextCompat.getColor(this, R.color.info_blue))
            } else {
                button.setTextColor(Color.WHITE)
            }
        }, 600)
    }
    
    /**
     * Vibrate for success feedback.
     */
    private fun vibrateSuccess() {
        vibrate(longArrayOf(0, 100)) // Single short pulse
    }
    
    /**
     * Vibrate for error feedback.
     */
    private fun vibrateError() {
        vibrate(longArrayOf(0, 50, 50, 50)) // Double pulse
    }
    
    /**
     * Vibrate with pattern.
     */
    private fun vibrate(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }
    
    /**
     * Log a contribution attempt to local storage.
     */
    private fun logAttempt(limit: Int, unit: String, status: ContributionLog.Status, reason: String?) {
        contributionLog.logAttempt(
            ContributionLog.Attempt(
                timestamp = System.currentTimeMillis(),
                latitude = currentLatitude,
                longitude = currentLongitude,
                wayId = currentWayId,
                wayName = null, // Could be fetched from OSM if needed
                speedLimit = limit,
                unit = unit,
                status = status,
                failureReason = reason
            )
        )
    }
    
    /**
     * Submit speed limit silently (no confirmation dialog).
     * Shows green flash on success, red flash on failure.
     */
    private fun submitSpeedLimitSilently(limit: Int, unit: String, button: TextView) {
        CoroutineScope(Dispatchers.Main).launch {
            val success = osmContributor.submitSpeedLimit(currentWayId, limit, unit)
            
            if (success) {
                flashButton(button, true) // Green flash
                vibrateSuccess()
                logAttempt(limit, unit, ContributionLog.Status.SUCCESS, null)
                
                // Record submission for instant gratification (5 min before deferring to OSM)
                val limitMph = if (SpeedUnitHelper.usesMph(currentCountryCode)) limit 
                               else SpeedUnitHelper.kmhToMph(limit)
                speedLimitProvider.recordUserSubmission(currentWayId, limitMph)
                
                // Voice "Thank you!" - brief and non-distracting
                voiceAnnouncer.speakFeedback(getString(R.string.voice_thank_you))
            } else {
                // OSM rejected the submission
                rejectContribution(button, limit, unit, ContributionLog.Status.FAILED_API_ERROR,
                    "OSM API error", getString(R.string.voice_rejected_osm))
            }
        }
    }
    
    /**
     * Handle a rejected contribution with visual, haptic, voice feedback and logging.
     */
    private fun rejectContribution(
        button: TextView, 
        limit: Int, 
        unit: String, 
        status: ContributionLog.Status,
        logReason: String,
        voiceMessage: String
    ) {
        flashButton(button, false) // Red flash
        vibrateError()
        logAttempt(limit, unit, status, logReason)
        voiceAnnouncer.speakFeedback(voiceMessage)
    }
    
    /**
     * Show the OSM contribution dialog.
     * This is ONLY shown when the user is NOT logged in.
     * Once logged in, submissions are silent (one-tap).
     */
    private fun showContributionDialog(limit: Int) {
        val dialogBinding = DialogOsmContributionBinding.inflate(LayoutInflater.from(this))
        
        val unit = SpeedUnitHelper.getUnitLabel(currentCountryCode)
        dialogBinding.speedLimitValue.text = limit.toString()
        dialogBinding.speedLimitUnit.text = unit
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_SpeedLimit_Dialog)
            .setView(dialogBinding.root)
            .create()
        
        // This dialog is only shown when NOT logged in
        // Hide submit button, show connect button
        dialogBinding.connectOsmButton.visibility = android.view.View.VISIBLE
        dialogBinding.whyConnectLink.visibility = android.view.View.VISIBLE
        dialogBinding.submitButton.visibility = android.view.View.GONE
        
        dialogBinding.connectOsmButton.setOnClickListener {
            // Save the limit for after OAuth completes
            pendingContributionLimit = limit
            dialog.dismiss()
            
            // Log the attempt (pending auth)
            val unitStr = if (SpeedUnitHelper.usesMph(currentCountryCode)) "mph" else "km/h"
            logAttempt(limit, unitStr, ContributionLog.Status.FAILED_NO_AUTH, "User not logged in - starting OAuth")
            
            // Start OAuth flow (uses Chrome Custom Tabs)
            osmContributor.startLogin()
        }
        
        // Show "Why Connect?" info dialog
        dialogBinding.whyConnectLink.setOnClickListener {
            dialog.dismiss()
            showWhyConnectDialog(limit)
        }
        
        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
        setDialogFullWidth(dialog)
    }
    
    /**
     * Make dialog full width with standard margins.
     * Adapts naturally to any screen size.
     */
    private fun setDialogFullWidth(dialog: AlertDialog) {
        dialog.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            // Horizontal margins let content breathe
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }
    
    /**
     * Show the "Why Connect?" info dialog.
     */
    private fun showWhyConnectDialog(pendingLimit: Int) {
        val dialogBinding = DialogOsmWhyConnectBinding.inflate(LayoutInflater.from(this))
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_SpeedLimit_Dialog)
            .setView(dialogBinding.root)
            .create()
        
        dialogBinding.connectButton.setOnClickListener {
            // Save the limit for after OAuth completes
            pendingContributionLimit = pendingLimit
            dialog.dismiss()
            
            // Start OAuth flow (uses Chrome Custom Tabs)
            osmContributor.startLogin()
        }
        
        dialogBinding.skipButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
        setDialogFullWidth(dialog)
    }
    

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun checkIfServiceRunning() {
        isMonitoring = SpeedMonitorService.isRunning
        updateButtonIcon()
    }

    private fun checkPermissionsAndStart() {
        when {
            hasLocationPermission() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission()) {
                    requestBackgroundLocationPermission()
                } else {
                    checkNotificationPermissionAndStart()
                }
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showLocationRationaleDialog()
            }
            else -> {
                requestLocationPermission()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AlertDialog.Builder(this)
                .setTitle("Background Location Required")
                .setMessage("To monitor your speed while the app is minimized, please allow 'All the time' location access in the next screen.")
                .setPositiveButton("Continue") { _, _ ->
                    backgroundLocationPermissionLauncher.launch(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                }
                .setNegativeButton("Skip") { _, _ ->
                    checkNotificationPermissionAndStart()
                }
                .show()
        }
    }

    private fun checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startSpeedMonitoring()
    }

    private fun showLocationRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("This app needs location access to monitor your speed and provide speed limit alerts.")
            .setPositiveButton("Grant") { _, _ ->
                requestLocationPermission()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Location permission is required for speed monitoring. Please enable it in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }

    private fun startSpeedMonitoring() {
        val serviceIntent = Intent(this, SpeedMonitorService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        isMonitoring = true
        updateButtonIcon()
        
        // If user tapped floating button, proceed to floating mode now
        if (pendingFloatingMode) {
            pendingFloatingMode = false
            checkOverlayPermissionAndStartFloating()
        }
    }

    private fun stopSpeedMonitoring() {
        val serviceIntent = Intent(this, SpeedMonitorService::class.java)
        stopService(serviceIntent)
        isMonitoring = false
        stopFlashing()
        resetSpeedDisplay()
        updateButtonIcon()
    }

    private fun updateButtonIcon() {
        val icon = if (isMonitoring) R.drawable.ic_stop else R.drawable.ic_play
        binding.toggleButton.setImageResource(icon)
    }

    private fun updateSpeedDisplay(speedMph: Float, speedLimitMph: Int, isOverLimit: Boolean, countryCode: String) {
        val usesMph = SpeedUnitHelper.usesMph(countryCode)
        
        // Update grid if country changed
        if (countryCode != currentCountryCode) {
            setupSpeedLimitGrid(countryCode)
        }
        
        // Convert to display units
        val displaySpeed = if (usesMph) speedMph.toInt() else SpeedUnitHelper.mphToKmh(speedMph.toInt())
        val displayLimit = if (speedLimitMph > 0) {
            if (usesMph) speedLimitMph else SpeedUnitHelper.mphToKmh(speedLimitMph)
        } else -1
        
        // Update speed display
        binding.currentSpeedText.text = displaySpeed.toString()
        
        // Update unit label
        binding.currentSpeedUnit.text = SpeedUnitHelper.getUnitLabel(countryCode)
        
        // Show/hide unknown limit indicator
        if (speedLimitMph <= 0) {
            binding.unknownLimitIndicator.visibility = View.VISIBLE
        } else {
            binding.unknownLimitIndicator.visibility = View.GONE
        }
        
        // Highlight matching speed limit button
        speedLimitButtons.forEachIndexed { index, button ->
            val limitValue = currentDisplayLimits.getOrNull(index) ?: 0
            if (limitValue == displayLimit) {
                button.setTextColor(ContextCompat.getColor(this, R.color.info_blue))
            } else {
                button.setTextColor(Color.WHITE)
            }
        }
        
        // Handle flashing animation when over limit
        if (isOverLimit) {
            if (!isFlashing) {
                startFlashing()
            }
        } else {
            if (isFlashing) {
                stopFlashing()
            }
            binding.currentSpeedText.setTextColor(Color.WHITE)
        }
    }

    private fun startFlashing() {
        isFlashing = true
        
        flashAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val color = if (fraction < 0.5f) Color.WHITE else Color.parseColor("#FF0000")
                binding.currentSpeedText.setTextColor(color)
            }
            start()
        }
    }

    private fun stopFlashing() {
        isFlashing = false
        flashAnimator?.cancel()
        flashAnimator = null
        binding.currentSpeedText.setTextColor(Color.WHITE)
    }

    private fun resetSpeedDisplay() {
        binding.currentSpeedText.text = "0"
        binding.currentSpeedText.setTextColor(Color.WHITE)
        speedLimitButtons.forEach { it.setTextColor(Color.WHITE) }
        binding.unknownLimitIndicator.visibility = View.GONE
    }
}

