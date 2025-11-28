package com.speedalert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class SpeedMonitorService : Service() {

    companion object {
        const val TAG = "SpeedMonitorService"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "speed_monitor_channel"
        
        const val ACTION_SPEED_UPDATE = "com.speedalert.SPEED_UPDATE"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_SPEED_LIMIT = "speed_limit"
        const val EXTRA_IS_OVER_LIMIT = "is_over_limit"
        
        // Speed threshold before checking limits (20 mph)
        const val SPEED_CHECK_THRESHOLD_MPH = 20f
        
        // Tolerance percentage (5% over limit)
        const val SPEED_TOLERANCE_PERCENT = 0.05f
        
        // National speed limit (70 mph)
        const val NATIONAL_SPEED_LIMIT_MPH = 70f
        
        // Conversion factor: m/s to mph
        const val MS_TO_MPH = 2.23694f
        
        var isRunning = false
            private set
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var speedLimitProvider: SpeedLimitProvider
    private lateinit var alertPlayer: AlertPlayer
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var lastAlertTime = 0L
    private val alertCooldownMs = 5000L // 5 seconds between alerts

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        speedLimitProvider = SpeedLimitProvider()
        alertPlayer = AlertPlayer(this)
        
        createNotificationChannel()
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        isRunning = true
        
        startForeground(NOTIFICATION_ID, createNotification("Starting speed monitoring..."))
        startLocationUpdates()
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        isRunning = false
        stopLocationUpdates()
        alertPlayer.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(speedMph: Int, limitMph: Int?) {
        val text = if (limitMph != null) {
            "Speed: $speedMph mph | Limit: $limitMph mph"
        } else {
            "Speed: $speedMph mph | Checking limit..."
        }
        
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    processLocation(location)
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // Update every 1 second
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        
        Log.d(TAG, "Location updates started")
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d(TAG, "Location updates stopped")
    }

    private fun processLocation(location: Location) {
        // Get speed in mph
        val speedMph = if (location.hasSpeed()) {
            location.speed * MS_TO_MPH
        } else {
            0f
        }
        
        Log.d(TAG, "Current speed: $speedMph mph at ${location.latitude}, ${location.longitude}")
        
        // Only check speed limits if above threshold
        if (speedMph >= SPEED_CHECK_THRESHOLD_MPH) {
            checkSpeedLimit(location.latitude, location.longitude, speedMph)
        } else {
            // Below threshold, just update display
            broadcastSpeedUpdate(speedMph, -1, false)
            updateNotification(speedMph.toInt(), null)
        }
    }

    private fun checkSpeedLimit(lat: Double, lon: Double, currentSpeedMph: Float) {
        serviceScope.launch {
            try {
                val speedLimitMph = speedLimitProvider.getSpeedLimit(lat, lon)
                
                val isOverLimit = if (speedLimitMph != null) {
                    // Check against the detected speed limit
                    val threshold = speedLimitMph * (1 + SPEED_TOLERANCE_PERCENT)
                    currentSpeedMph > threshold
                } else {
                    // If no limit is found, check against the national speed limit
                    val nationalThreshold = NATIONAL_SPEED_LIMIT_MPH * (1 + SPEED_TOLERANCE_PERCENT)
                    currentSpeedMph > nationalThreshold
                }
                
                Log.d(TAG, "Speed limit: $speedLimitMph mph, Over limit: $isOverLimit")
                
                // Play alert if over limit
                if (isOverLimit && canPlayAlert()) {
                    alertPlayer.playAlert()
                    lastAlertTime = System.currentTimeMillis()
                }
                
                // Broadcast update to UI
                broadcastSpeedUpdate(currentSpeedMph, speedLimitMph ?: -1, isOverLimit)
                updateNotification(currentSpeedMph.toInt(), speedLimitMph)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking speed limit", e)
                broadcastSpeedUpdate(currentSpeedMph, -1, false)
                updateNotification(currentSpeedMph.toInt(), null)
            }
        }
    }

    private fun canPlayAlert(): Boolean {
        return System.currentTimeMillis() - lastAlertTime > alertCooldownMs
    }

    private fun broadcastSpeedUpdate(speedMph: Float, speedLimit: Int, isOverLimit: Boolean) {
        val intent = Intent(ACTION_SPEED_UPDATE).apply {
            putExtra(EXTRA_SPEED, speedMph)
            putExtra(EXTRA_SPEED_LIMIT, speedLimit)
            putExtra(EXTRA_IS_OVER_LIMIT, isOverLimit)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
