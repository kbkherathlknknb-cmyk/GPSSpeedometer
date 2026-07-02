package com.gps.speedometer

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlin.math.roundToInt

class FloatingSpeedometerService : Service(), SensorEngine.SensorCallback {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "FloatingSpeedometerChannel"
        const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorEngine: SensorEngine

    private var widgetSpeedText: TextView? = null
    private var widgetUnitText: TextView? = null
    private var widgetSubText: TextView? = null

    private var currentDirection = "N"
    private var currentGForce = 0.0f
    private var isMph = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sensorEngine = SensorEngine(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        setupFloatingView()
        startLocationUpdates()
        sensorEngine.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isMph = intent?.getBooleanExtra("IS_MPH", false) ?: false
        widgetUnitText?.text = if (isMph) "mph" else "km/h"
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun setupFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        widgetSpeedText = floatingView?.findViewById(R.id.widgetSpeedText)
        widgetUnitText = floatingView?.findViewById(R.id.widgetUnitText)
        widgetSubText = floatingView?.findViewById(R.id.widgetSubText)

        floatingView?.findViewById<ImageView>(R.id.widgetCloseBtn)?.setOnClickListener {
            stopSelf()
        }

        floatingView?.findViewById<ImageView>(R.id.widgetExpandBtn)?.setOnClickListener {
            val expandIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(expandIntent)
            stopSelf()
        }

        // Drag and Drop touch listener
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingView?.findViewById<View>(R.id.widgetRoot)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    floatingView?.let {
                        windowManager.updateViewLayout(it, layoutParams)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, layoutParams)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    updateSpeed(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun updateSpeed(location: Location) {
        if (!location.hasSpeed() || location.speed < 0.5f) {
            widgetSpeedText?.text = "0"
            return
        }
        val speedMs = location.speed
        val displaySpeed = if (isMph) speedMs * 2.23694f else speedMs * 3.6f
        widgetSpeedText?.text = displaySpeed.roundToInt().toString()
    }

    override fun onHeadingChanged(azimuth: Float, cardinal: String) {
        currentDirection = cardinal
        updateSubText()
    }

    override fun onAccelerationChanged(accelerationMs2: Float, gForce: Float) {
        currentGForce = gForce
        updateSubText()
    }

    private fun updateSubText() {
        val gFormatted = String.format("%.1fG", currentGForce)
        widgetSubText?.text = "$currentDirection • $gFormatted"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Speedometer Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps GPS speedometer overlay active in background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Speedometer Active")
            .setContentText("Tap to return to dashboard or close overlay")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorEngine.stop()
        floatingView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
        }
    }
}
