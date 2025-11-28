package com.speedlimit

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Service that displays a floating speed overlay on top of other apps.
 * Shows only the current speed and flashes red/white when over limit.
 */
class FloatingSpeedService : Service() {

    companion object {
        const val TAG = "FloatingSpeedService"
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "floating_speed_channel"
        
        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var speedText: TextView
    private lateinit var unitText: TextView
    private lateinit var closeButton: TextView
    
    private var isFlashing = false
    private var flashAnimator: ValueAnimator? = null

    // Broadcast receiver for speed updates
    private val speedUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val speedMph = it.getFloatExtra(SpeedMonitorService.EXTRA_SPEED, 0f)
                val isOverLimit = it.getBooleanExtra(SpeedMonitorService.EXTRA_IS_OVER_LIMIT, false)
                val countryCode = it.getStringExtra(SpeedMonitorService.EXTRA_COUNTRY_CODE) ?: "GB"
                updateDisplay(speedMph, isOverLimit, countryCode)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingSpeedService created")
        isRunning = true
        
        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingView()
        registerSpeedReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FloatingSpeedService started")
        
        // Start as foreground service with appropriate type for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Floating Speed Display",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when floating speed display is active"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Speed/Limit")
            .setContentText("Floating display active • Tap to return")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "FloatingSpeedService destroyed")
        isRunning = false
        stopFlashing()
        
        try {
            unregisterReceiver(speedUpdateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        
        try {
            windowManager.removeView(floatingView)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing floating view", e)
        }
        
        super.onDestroy()
    }

    private fun createFloatingView() {
        // Inflate the floating layout
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_speed, null)
        speedText = floatingView.findViewById(R.id.floatingSpeedText)
        unitText = floatingView.findViewById(R.id.floatingUnitText)
        closeButton = floatingView.findViewById(R.id.closeButton)

        // Set up window parameters
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        // Add touch listener for dragging (with tap detection)
        setupDragListener(layoutParams)
        
        // Close button - just dismiss floating overlay, keep monitoring in background
        closeButton.setOnClickListener {
            stopSelf()
        }

        // Add the view to window
        windowManager.addView(floatingView, layoutParams)
    }

    private fun setupDragListener(layoutParams: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var lastTapTime = 0L
        var hasMoved = false

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                    // Only count as move if moved more than 10 pixels
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        hasMoved = true
                        layoutParams.x = initialX + deltaX.toInt()
                        layoutParams.y = initialY + deltaY.toInt()
                        windowManager.updateViewLayout(floatingView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        // This was a tap, not a drag
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < 400) {
                            // Double tap detected
                            openMainApp()
                            stopSelf()
                        }
                        lastTapTime = currentTime
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun registerSpeedReceiver() {
        val filter = IntentFilter(SpeedMonitorService.ACTION_SPEED_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(speedUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(speedUpdateReceiver, filter)
        }
    }

    private fun updateDisplay(speedMph: Float, isOverLimit: Boolean, countryCode: String) {
        val usesMph = SpeedUnitHelper.usesMph(countryCode)
        
        // Convert to display units
        val displaySpeed = if (usesMph) speedMph.toInt() else SpeedUnitHelper.mphToKmh(speedMph.toInt())
        
        // Update display
        speedText.text = displaySpeed.toString()
        unitText.text = SpeedUnitHelper.getUnitLabel(countryCode)
        
        // Handle flashing when over limit
        if (isOverLimit) {
            if (!isFlashing) {
                startFlashing()
            }
        } else {
            if (isFlashing) {
                stopFlashing()
            }
            speedText.setTextColor(Color.WHITE)
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
                speedText.setTextColor(color)
            }
            start()
        }
    }

    private fun stopFlashing() {
        isFlashing = false
        flashAnimator?.cancel()
        flashAnimator = null
        speedText.setTextColor(Color.WHITE)
    }
}

