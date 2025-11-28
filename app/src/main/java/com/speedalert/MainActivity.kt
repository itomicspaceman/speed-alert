package com.speedalert

import android.Manifest
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.speedalert.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isMonitoring = false
    private var isFlashing = false
    private var flashAnimator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())

    private val speedLimitViews by lazy {
        mapOf(
            20 to binding.speedLimit20,
            30 to binding.speedLimit30,
            40 to binding.speedLimit40,
            50 to binding.speedLimit50,
            60 to binding.speedLimit60,
            70 to binding.speedLimit70
        )
    }

    // Broadcast receiver for speed updates from the service
    private val speedUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val speed = it.getFloatExtra(SpeedMonitorService.EXTRA_SPEED, 0f)
                val speedLimit = it.getIntExtra(SpeedMonitorService.EXTRA_SPEED_LIMIT, -1)
                val isOverLimit = it.getBooleanExtra(SpeedMonitorService.EXTRA_IS_OVER_LIMIT, false)
                updateSpeedDisplay(speed, speedLimit, isOverLimit)
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
        if (granted) {
            checkNotificationPermissionAndStart()
        } else {
            Toast.makeText(
                this,
                "Background location denied - app may not work when minimized",
                Toast.LENGTH_LONG
            ).show()
            checkNotificationPermissionAndStart()
        }
    }

    // Permission launcher for notifications (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeedMonitoring()
        } else {
            Toast.makeText(
                this,
                "Notification permission denied - service notification won't show",
                Toast.LENGTH_SHORT
            ).show()
            startSpeedMonitoring()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkIfServiceRunning()
    }

    override fun onResume() {
        super.onResume()
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

    private fun updateSpeedDisplay(speed: Float, speedLimit: Int, isOverLimit: Boolean) {
        val speedMph = speed.toInt()
        binding.currentSpeedText.text = speedMph.toString()
        
        // Update speed limit grid
        speedLimitViews.values.forEach { it.setTextColor(Color.WHITE) }
        speedLimitViews[speedLimit]?.setTextColor(ContextCompat.getColor(this, R.color.info_blue))
        
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
                // Alternate between white and red
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
        speedLimitViews.values.forEach { it.setTextColor(Color.WHITE) }
    }
}
