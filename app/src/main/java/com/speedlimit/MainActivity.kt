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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.flexbox.FlexboxLayout
import com.speedlimit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isMonitoring = false
    private var isFlashing = false
    private var flashAnimator: ValueAnimator? = null
    private var pendingFloatingMode = false  // Flag to launch floating mode after monitoring starts

    // Dynamically created speed limit buttons
    private val speedLimitButtons = mutableListOf<TextView>()
    
    // Current display limits and country
    private var currentDisplayLimits = listOf<Int>()
    private var currentCountryCode = "GB"

    // Broadcast receiver for speed updates from the service
    private val speedUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val speedMph = it.getFloatExtra(SpeedMonitorService.EXTRA_SPEED, 0f)
                val speedLimitMph = it.getIntExtra(SpeedMonitorService.EXTRA_SPEED_LIMIT, -1)
                val isOverLimit = it.getBooleanExtra(SpeedMonitorService.EXTRA_IS_OVER_LIMIT, false)
                val countryCode = it.getStringExtra(SpeedMonitorService.EXTRA_COUNTRY_CODE) ?: "GB"
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

        setupUI()
        setupSpeedLimitGrid(currentCountryCode)
        checkIfServiceRunning()
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
     */
    private fun createSpeedLimitButton(limit: Int): TextView {
        val button = TextView(this).apply {
            text = limit.toString()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f)
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            gravity = Gravity.CENTER
            
            // Large touch targets for easy tapping while driving
            // Use WRAP_CONTENT width to let text breathe, fixed height
            val height = dpToPx(90)
            val marginH = dpToPx(12)  // Horizontal margin
            val marginV = dpToPx(8)   // Vertical margin
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                height
            ).apply {
                setMargins(marginH, marginV, marginH, marginV)
                // Ensure minimum width for tap target
                minWidth = dpToPx(90)
            }
            
            // Add horizontal padding for better tap area
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            
            // Make it clickable for crowdsourcing
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
     * Called when user taps a speed limit to contribute.
     * TODO: Implement crowdsourcing - send to backend/OSM
     */
    private fun onSpeedLimitSelected(limit: Int) {
        // Visual feedback - brief flash of the selected button
        speedLimitButtons.forEachIndexed { index, button ->
            val limitValue = currentDisplayLimits.getOrNull(index) ?: 0
            if (limitValue == limit) {
                // Flash green briefly to confirm selection
                button.setTextColor(Color.GREEN)
                button.postDelayed({ button.setTextColor(Color.WHITE) }, 300)
            }
        }
        // TODO: Send to backend/OSM - currentLocation, selectedLimit, timestamp
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
    }
}

