package com.speedlimit

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class SpeedMonitorService : Service() {

    companion object {
        const val TAG = "SpeedMonitorService"
        const val NOTIFICATION_ID = 1
        const val RATE_LIMIT_NOTIFICATION_ID = 2
        const val CHANNEL_ID = "speed_monitor_channel"
        
        const val ACTION_SPEED_UPDATE = "com.speedlimit.SPEED_UPDATE"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_SPEED_LIMIT = "speed_limit"
        const val EXTRA_IS_OVER_LIMIT = "is_over_limit"
        const val EXTRA_COUNTRY_CODE = "country_code"
        const val EXTRA_WAY_ID = "way_id"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_ROAD_NAME = "road_name"
        const val EXTRA_HIGHWAY_TYPE = "highway_type"
        const val EXTRA_ROAD_DISTANCE = "road_distance"
        
        // Speed threshold before checking limits (country-aware)
        const val SPEED_CHECK_THRESHOLD_MPH = 20f      // For mph countries
        const val SPEED_CHECK_THRESHOLD_KMH_AS_MPH = 12.5f  // 20 km/h ≈ 12.5 mph for km/h countries
        
        // Tolerance percentage (5% over limit)
        const val SPEED_TOLERANCE_PERCENT = 0.05f
        
        // National speed limit (70 mph)
        const val NATIONAL_SPEED_LIMIT_MPH = 70f
        
        // Conversion factor: m/s to mph
        const val MS_TO_MPH = 2.23694f
        
        // Minimum movement before re-processing location (battery optimization)
        const val MIN_MOVEMENT_METERS = 5f
        
        var isRunning = false
            private set
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var speedLimitProvider: SpeedLimitProvider
    private lateinit var alertPlayer: AlertPlayer
    private lateinit var voiceAnnouncer: VoiceAnnouncer
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Track last known speed limit for change detection
    private var lastKnownSpeedLimit: Int = -1
    
    // Track last processed location (battery optimization - skip if barely moved)
    private var lastProcessedLocation: Location? = null
    private var lastSpeedLimitResult: Int = -1
    
    private var lastAlertTime = 0L
    private val alertCooldownMs = 5000L // 5 seconds between alerts
    
    // Rate limit alert receiver
    private val rateLimitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SpeedLimitProvider.ACTION_RATE_LIMIT_ALERT) {
                val limitType = intent.getStringExtra(SpeedLimitProvider.EXTRA_RATE_LIMIT_TYPE) ?: "Unknown"
                val backoffSeconds = intent.getIntExtra(SpeedLimitProvider.EXTRA_BACKOFF_SECONDS, 30)
                
                Log.w(TAG, "Rate limit alert received: $limitType, backoff: ${backoffSeconds}s")
                
                // Log to Firebase for developer monitoring (no user-facing toast needed)
                showRateLimitNotification(limitType, backoffSeconds)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        speedLimitProvider = SpeedLimitProvider(this)
        alertPlayer = AlertPlayer(this)
        voiceAnnouncer = VoiceAnnouncer(this)
        
        createNotificationChannel()
        setupLocationCallback()
        
        // Register rate limit receiver
        val filter = IntentFilter(SpeedLimitProvider.ACTION_RATE_LIMIT_ALERT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(rateLimitReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(rateLimitReceiver, filter)
        }
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
        voiceAnnouncer.shutdown()
        serviceScope.cancel()
        
        // Unregister rate limit receiver
        try {
            unregisterReceiver(rateLimitReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered")
        }
        
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
    
    private fun showRateLimitNotification(limitType: String, backoffSeconds: Int) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚠️ API Limit Reached")
            .setContentText("$limitType - Using cached data for ${backoffSeconds}s")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(RATE_LIMIT_NOTIFICATION_ID, notification)
    }

    private fun updateNotification(speedMph: Int, limitMph: Int?) {
        val countryCode = speedLimitProvider.currentCountryCode
        val usesMph = SpeedUnitHelper.usesMph(countryCode)
        val unit = SpeedUnitHelper.getUnitLabel(countryCode)
        
        val displaySpeed = if (usesMph) speedMph else SpeedUnitHelper.mphToKmh(speedMph)
        val displayLimit = if (limitMph != null) {
            if (usesMph) limitMph else SpeedUnitHelper.mphToKmh(limitMph)
        } else null
        
        val text = if (displayLimit != null) {
            "Speed: $displaySpeed $unit | Limit: $displayLimit $unit"
        } else {
            "Speed: $displaySpeed $unit | Checking limit..."
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
        
        // Get bearing (direction of travel) - important for detecting turns
        val bearing = if (location.hasBearing()) {
            location.bearing
        } else {
            -1f  // Unknown bearing
        }
        
        Log.d(TAG, "Current speed: $speedMph mph, bearing: $bearing° at ${location.latitude}, ${location.longitude}")
        
        // Battery optimization: skip processing if barely moved (< 5m)
        val lastLoc = lastProcessedLocation
        if (lastLoc != null && location.distanceTo(lastLoc) < MIN_MOVEMENT_METERS) {
            // Reuse last speed limit result, just update speed display
            broadcastSpeedUpdate(speedMph, lastSpeedLimitResult, false, location.latitude, location.longitude, location.accuracy)
            updateNotification(speedMph.toInt(), if (lastSpeedLimitResult > 0) lastSpeedLimitResult else null)
            return
        }
        
        // Get country-aware threshold (lower for km/h countries to catch 20 km/h zones)
        val countryCode = speedLimitProvider.currentCountryCode
        val threshold = if (SpeedUnitHelper.usesMph(countryCode)) {
            SPEED_CHECK_THRESHOLD_MPH
        } else {
            SPEED_CHECK_THRESHOLD_KMH_AS_MPH  // 20 km/h = 12.5 mph
        }
        
        // Only check speed limits if above threshold
        if (speedMph >= threshold) {
            lastProcessedLocation = location
            checkSpeedLimit(location.latitude, location.longitude, speedMph, bearing, location.accuracy)
        } else {
            // Below threshold, just update display
            broadcastSpeedUpdate(speedMph, -1, false, location.latitude, location.longitude, location.accuracy)
            updateNotification(speedMph.toInt(), null)
        }
    }

    private fun checkSpeedLimit(lat: Double, lon: Double, currentSpeedMph: Float, bearing: Float, accuracy: Float) {
        serviceScope.launch {
            try {
                val speedLimitMph = speedLimitProvider.getSpeedLimit(lat, lon, bearing)
                val countryCode = speedLimitProvider.currentCountryCode
                val usesMph = SpeedUnitHelper.usesMph(countryCode)
                
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
                
                // Voice announcements (premium feature)
                if (speedLimitMph != null) {
                    if (speedLimitMph != lastKnownSpeedLimit) {
                        // Speed limit changed - announce it
                        val displayLimit = if (usesMph) speedLimitMph else SpeedUnitHelper.mphToKmh(speedLimitMph)
                        voiceAnnouncer.announceSpeedLimitChange(displayLimit, usesMph)
                        lastKnownSpeedLimit = speedLimitMph
                    }
                } else {
                    if (lastKnownSpeedLimit != -1) {
                        // Entered unknown zone
                        voiceAnnouncer.announceUnknownZone()
                        lastKnownSpeedLimit = -1
                    }
                }
                
                // Play alert if over limit
                if (isOverLimit && canPlayAlert()) {
                    alertPlayer.playAlert()
                    voiceAnnouncer.announceOverLimit()
                    lastAlertTime = System.currentTimeMillis()
                    
                    // Auto-show floating display if user dismissed it
                    if (FloatingSpeedService.autoShowOnAlert && !FloatingSpeedService.isRunning) {
                        FloatingSpeedService.autoShowOnAlert = false  // Reset flag
                        if (android.provider.Settings.canDrawOverlays(this@SpeedMonitorService)) {
                            val floatingIntent = Intent(this@SpeedMonitorService, FloatingSpeedService::class.java)
                            ContextCompat.startForegroundService(this@SpeedMonitorService, floatingIntent)
                        }
                    }
                }
                
                // Cache the result for battery optimization
                lastSpeedLimitResult = speedLimitMph ?: -1
                
                // Broadcast update to UI (including location for crowdsourcing validation)
                broadcastSpeedUpdate(currentSpeedMph, speedLimitMph ?: -1, isOverLimit, lat, lon, accuracy)
                updateNotification(currentSpeedMph.toInt(), speedLimitMph)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking speed limit", e)
                broadcastSpeedUpdate(currentSpeedMph, -1, false, lat, lon, accuracy)
                updateNotification(currentSpeedMph.toInt(), null)
            }
        }
    }

    private fun canPlayAlert(): Boolean {
        return System.currentTimeMillis() - lastAlertTime > alertCooldownMs
    }

    private fun broadcastSpeedUpdate(speedMph: Float, speedLimit: Int, isOverLimit: Boolean, 
                                       lat: Double = 0.0, lon: Double = 0.0, accuracy: Float = Float.MAX_VALUE) {
        val countryCode = speedLimitProvider.currentCountryCode
        val wayId = speedLimitProvider.currentWayId
        val roadName = speedLimitProvider.currentRoadName
        val highwayType = speedLimitProvider.currentHighwayType
        val roadDistance = speedLimitProvider.currentRoadDistance
        
        val intent = Intent(ACTION_SPEED_UPDATE).apply {
            putExtra(EXTRA_SPEED, speedMph)
            putExtra(EXTRA_SPEED_LIMIT, speedLimit)
            putExtra(EXTRA_IS_OVER_LIMIT, isOverLimit)
            putExtra(EXTRA_COUNTRY_CODE, countryCode)
            putExtra(EXTRA_WAY_ID, wayId)
            putExtra(EXTRA_LATITUDE, lat)
            putExtra(EXTRA_LONGITUDE, lon)
            putExtra(EXTRA_ACCURACY, accuracy)
            putExtra(EXTRA_ROAD_NAME, roadName ?: "")
            putExtra(EXTRA_HIGHWAY_TYPE, highwayType ?: "")
            putExtra(EXTRA_ROAD_DISTANCE, roadDistance)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}

