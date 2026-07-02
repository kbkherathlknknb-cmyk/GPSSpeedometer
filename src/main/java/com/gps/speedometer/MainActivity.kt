package com.gps.speedometer

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration as AndroidConfig
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class MainActivity : AppCompatActivity(), SensorEngine.SensorCallback {

    // ============================
    // Views
    // ============================
    private lateinit var speedGauge: SpeedGaugeView
    private lateinit var sportyAccelView: SportyAccelerationView
    private lateinit var compassArrow: ImageView
    private lateinit var headingText: TextView
    private lateinit var cardinalText: TextView
    private lateinit var gpsIndicator: View
    private lateinit var gpsStatusText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var maxSpeedText: TextView
    private lateinit var avgSpeedText: TextView
    private lateinit var distanceText: TextView
    private lateinit var totalOdoText: TextView
    private lateinit var tripTimeText: TextView
    private lateinit var btnResetTrip: LinearLayout
    private lateinit var btnTripLog: LinearLayout

    // Layout groups for Mini Window (PiP) toggling
    private lateinit var statusBar: View
    private lateinit var statsGroup: View
    private lateinit var tripActionsBar: View
    private lateinit var btnUnitToggle: TextView
    private lateinit var btnMenu: ImageView

    // ============================
    // Location & Sensor Engine
    // ============================
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorEngine: SensorEngine
    private var currentLocation: Location? = null
    private var isMph = false
    private var driveMode = 0 // 0=Calm, 1=Eco, 2=Traffic, 3=Aggressive

    // ============================
    // Trip data & Total Odometer
    // ============================
    private var tripStartTime: Long = 0
    private var tripMaxSpeed = 0f
    private var tripTotalSpeed = 0f
    private var tripSpeedCount = 0
    private var tripDistance = 0f
    private var totalOdoKm = 0.0
    private var lastTripLocation: Location? = null
    private var tripActive = false

    // ============================
    // Speed smoothing
    // ============================
    private val speedHistory = mutableListOf<Float>()
    private val SMOOTH_SIZE = 3

    // ============================
    // Timer
    // ============================
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (tripActive && tripStartTime > 0) {
                val elapsed = System.currentTimeMillis() - tripStartTime
                tripTimeText.text = formatDuration(elapsed)
            }
            timerHandler.postDelayed(this, 1000)
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            startLocationUpdates()
        } else {
            setGpsStatus("disconnected", getString(R.string.gps_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load SharedPreferences settings
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        driveMode = prefs.getInt("DRIVE_MODE", 0)
        isMph = prefs.getBoolean("IS_MPH", false)
        totalOdoKm = prefs.getFloat("TOTAL_ODO_KM", 0f).toDouble()

        setContentView(R.layout.activity_main)

        sensorEngine = SensorEngine(this)

        bindViews()
        applyDriveModeAndUnit()
        setupClickListeners()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationCallback()

        timerHandler.post(timerRunnable)
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        sensorEngine.start(this)
    }

    override fun onPause() {
        super.onPause()
        sensorEngine.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        timerHandler.removeCallbacks(timerRunnable)
        sensorEngine.stop()
    }

    override fun onConfigurationChanged(newConfig: AndroidConfig) {
        super.onConfigurationChanged(newConfig)
        // Responsive ScrollView and layout weights automatically handle screen rotation and split-screen sizing
    }

    private fun bindViews() {
        speedGauge = findViewById(R.id.speedGauge)
        sportyAccelView = findViewById(R.id.sportyAccelView)
        compassArrow = findViewById(R.id.compassArrow)
        headingText = findViewById(R.id.headingText)
        cardinalText = findViewById(R.id.cardinalText)
        gpsIndicator = findViewById(R.id.gpsIndicator)
        gpsStatusText = findViewById(R.id.gpsStatusText)
        accuracyText = findViewById(R.id.accuracyText)
        maxSpeedText = findViewById(R.id.maxSpeed)
        avgSpeedText = findViewById(R.id.avgSpeed)
        distanceText = findViewById(R.id.distanceText)
        totalOdoText = findViewById(R.id.totalOdoText)
        tripTimeText = findViewById(R.id.tripTime)
        btnResetTrip = findViewById(R.id.btnResetTrip)
        btnTripLog = findViewById(R.id.btnTripLog)

        statusBar = findViewById(R.id.statusBar)
        statsGroup = findViewById(R.id.statsGroup)
        tripActionsBar = findViewById(R.id.tripActionsBar)
        btnUnitToggle = findViewById(R.id.btnUnitToggle)
        btnMenu = findViewById(R.id.btnMenu)
    }

    private fun applyDriveModeAndUnit() {
        speedGauge.setDriveMode(driveMode)
        sportyAccelView.setDriveMode(driveMode)
        speedGauge.setUnit(if (isMph) "mph" else "km/h")
        sportyAccelView.setUnit(isMph)
        btnUnitToggle.text = if (isMph) "MPH" else "KM/H"
        updateOdoText()
    }

    private fun updateOdoText() {
        val odoVal = if (isMph) totalOdoKm * 0.621371 else totalOdoKm
        totalOdoText.text = String.format("%.1f", odoVal)
    }

    // ============================
    // Sensor Engine Callbacks
    // ============================
    override fun onHeadingChanged(azimuth: Float, cardinal: String) {
        val rounded = azimuth.roundToInt()
        headingText.text = "${rounded}°"
        cardinalText.text = cardinal

        ObjectAnimator.ofFloat(compassArrow, "rotation", compassArrow.rotation, azimuth).apply {
            duration = 300
            start()
        }
    }

    override fun onAccelerationChanged(accelerationMs2: Float, gForce: Float) {
        val currentSpeedMs = currentLocation?.let { if (it.hasSpeed()) it.speed else 0f } ?: 0f
        sportyAccelView.updateTelemetry(accelerationMs2, gForce, currentSpeedMs)
        speedGauge.setGForce(gForce)
    }

    // ============================
    // Picture-in-Picture Mode
    // ============================
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: AndroidConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            statusBar.visibility = View.GONE
            statsGroup.visibility = View.GONE
            tripActionsBar.visibility = View.GONE
        } else {
            statusBar.visibility = View.VISIBLE
            statsGroup.visibility = View.VISIBLE
            tripActionsBar.visibility = View.VISIBLE
        }
    }

    // ============================
    // Location & Accurate Distance
    // ============================
    private fun checkAndRequestPermissions() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                AlertDialog.Builder(this)
                    .setTitle("GPS Permission Required")
                    .setMessage(getString(R.string.permission_rationale))
                    .setPositiveButton("Grant") { _, _ ->
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        setGpsStatus("searching", getString(R.string.gps_searching))

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .setWaitForAccurateLocation(false)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    onLocationUpdate(location)
                }
            }
        }
    }

    private fun onLocationUpdate(location: Location) {
        currentLocation = location

        val accuracy = location.accuracy
        if (accuracy <= 20f) {
            setGpsStatus("connected", getString(R.string.gps_active))
        } else if (accuracy <= 50f) {
            setGpsStatus("connected", getString(R.string.gps_active))
        } else {
            setGpsStatus("searching", "Low accuracy")
        }
        accuracyText.text = "±${accuracy.toInt()}m"

        var speedRaw = 0f
        if (location.hasSpeed() && location.speed >= 0f) {
            speedRaw = location.speed
        }
        val convertedSpeed = if (isMph) speedRaw * 2.23694f else speedRaw * 3.6f

        speedHistory.add(convertedSpeed)
        if (speedHistory.size > SMOOTH_SIZE) {
            speedHistory.removeAt(0)
        }
        val smoothed = speedHistory.average().toFloat()
        val displaySpeed = if (smoothed < 1.5f) 0f else smoothed

        speedGauge.setSpeed(displaySpeed)
        updateTripStatsAndOdo(displaySpeed, location)
    }

    private fun setGpsStatus(status: String, text: String) {
        gpsStatusText.text = text
        val drawable = when (status) {
            "connected" -> R.drawable.gps_dot_green
            "searching" -> R.drawable.gps_dot_orange
            else -> R.drawable.gps_dot_red
        }
        gpsIndicator.setBackgroundResource(drawable)
    }

    // ============================
    // Trip Stats, Doppler Distance & Total Odo
    // ============================
    private fun updateTripStatsAndOdo(speed: Float, location: Location) {
        if (speed > 0f && !tripActive) {
            tripActive = true
            if (tripStartTime == 0L) tripStartTime = System.currentTimeMillis()
        }

        if (speed > 0f) {
            if (speed > tripMaxSpeed) {
                tripMaxSpeed = speed
                maxSpeedText.text = tripMaxSpeed.toInt().toString()
            }
            tripTotalSpeed += speed
            tripSpeedCount++
            avgSpeedText.text = (tripTotalSpeed / tripSpeedCount).toInt().toString()
        }

        // Doppler & Anti-Jitter Distance Filter
        if (location.accuracy <= 20f && location.hasSpeed() && location.speed >= 0.8f) {
            lastTripLocation?.let { last ->
                val dtSec = (location.time - last.time) / 1000f
                if (dtSec in 0.2f..60f) {
                    val dMetersGps = last.distanceTo(location)
                    val dMetersDoppler = location.speed * dtSec
                    val dMeters = minOf(dMetersGps, dMetersDoppler * 1.3f)

                    if (dMeters > 0.5f) {
                        val dKm = (dMeters / 1000.0)
                        val dUnit = if (isMph) dKm * 0.621371 else dKm
                        tripDistance += dUnit.toFloat()
                        distanceText.text = String.format("%.1f", tripDistance)

                        totalOdoKm += dKm
                        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        prefs.edit().putFloat("TOTAL_ODO_KM", totalOdoKm.toFloat()).apply()
                        updateOdoText()
                    }
                }
            }
        }
        lastTripLocation = location
    }

    private fun resetTrip() {
        tripStartTime = 0
        tripMaxSpeed = 0f
        tripTotalSpeed = 0f
        tripSpeedCount = 0
        tripDistance = 0f
        tripActive = false

        maxSpeedText.text = "0"
        avgSpeedText.text = "0"
        distanceText.text = "0.0"
        tripTimeText.text = "00:00"
    }

    private fun saveCurrentTripToLog() {
        if (tripDistance < 0.1f) {
            Toast.makeText(this, "Trip too short to log!", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val jsonStr = prefs.getString("TRIP_LOG_JSON", "[]") ?: "[]"
        val array = JSONArray(jsonStr)

        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        val dateStr = dateFormat.format(Date())
        val durationStr = if (tripStartTime > 0) formatDuration(System.currentTimeMillis() - tripStartTime) else "00:00"
        val avgVal = if (tripSpeedCount > 0) (tripTotalSpeed / tripSpeedCount).toInt() else 0

        val obj = JSONObject().apply {
            put("date", dateStr)
            put("distance", String.format("%.1f %s", tripDistance, if (isMph) "mi" else "km"))
            put("duration", durationStr)
            put("maxSpeed", "${tripMaxSpeed.toInt()} ${if (isMph) "mph" else "km/h"}")
            put("avgSpeed", "$avgVal ${if (isMph) "mph" else "km/h"}")
        }
        array.put(0, obj) // Insert at beginning
        prefs.edit().putString("TRIP_LOG_JSON", array.toString()).apply()
        Toast.makeText(this, "Trip saved to log! 📝", Toast.LENGTH_SHORT).show()
    }

    private fun showTripLogDialog() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val jsonStr = prefs.getString("TRIP_LOG_JSON", "[]") ?: "[]"
        val array = JSONArray(jsonStr)

        if (array.length() == 0) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.trip_log_title))
                .setMessage(getString(R.string.no_trips_logged))
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val items = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            items.add("📅 ${o.getString("date")}\n🛣️ ${o.getString("distance")} in ⏱️ ${o.getString("duration")}\n🏎️ Max: ${o.getString("maxSpeed")} | Avg: ${o.getString("avgSpeed")}")
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.trip_log_title))
            .setItems(items.toTypedArray(), null)
            .setPositiveButton("OK", null)
            .setNeutralButton(getString(R.string.clear_log)) { _, _ ->
                prefs.edit().putString("TRIP_LOG_JSON", "[]").apply()
                Toast.makeText(this, "Trip log cleared!", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ============================
    // Click Listeners & Three-Dot Side Panel
    // ============================
    private fun setupClickListeners() {
        btnResetTrip.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Trip Data?")
                .setMessage("Would you like to save this trip to your Trip Log before resetting?")
                .setPositiveButton("Save & Reset") { _, _ ->
                    saveCurrentTripToLog()
                    resetTrip()
                }
                .setNegativeButton("Just Reset") { _, _ ->
                    resetTrip()
                }
                .setNeutralButton("Cancel", null)
                .show()
        }

        btnTripLog.setOnClickListener { showTripLogDialog() }

        btnUnitToggle.setOnClickListener {
            isMph = !isMph
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("IS_MPH", isMph).apply()
            applyDriveModeAndUnit()
            currentLocation?.let { onLocationUpdate(it) }
        }

        btnMenu.setOnClickListener { showThreeDotMenu() }
    }

    private fun showThreeDotMenu() {
        val modeNames = arrayOf("🧘 Calm Mode", "🌱 Eco Mode", "🚗 Traffic Mode", "🏁 Aggressive Mode")
        val currentModeStr = modeNames[driveMode]
        val themeStr = if ((resources.configuration.uiMode and AndroidConfig.UI_MODE_NIGHT_MASK) == AndroidConfig.UI_MODE_NIGHT_YES) "☀️ Light Theme" else "🌙 Dark Theme"
        
        val items = arrayOf(
            "🏎️ Driving Mode: $currentModeStr",
            "📝 Trip Log History",
            "📱 Mini Window Mode (PiP / Overlay)",
            "🎨 Switch Theme: $themeStr"
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.menu_title))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showDriveModeSelector()
                    1 -> showTripLogDialog()
                    2 -> showMiniWindowSelector()
                    3 -> toggleTheme()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDriveModeSelector() {
        val modes = arrayOf(
            getString(R.string.mode_calm),
            getString(R.string.mode_eco),
            getString(R.string.mode_traffic),
            getString(R.string.mode_aggressive)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_drive_mode))
            .setSingleChoiceItems(modes, driveMode) { dialog, which ->
                driveMode = which
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                prefs.edit().putInt("DRIVE_MODE", driveMode).apply()
                applyDriveModeAndUnit()
                dialog.dismiss()
                Toast.makeText(this, "Switched to ${modes[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMiniWindowSelector() {
        val options = arrayOf("Picture-in-Picture Mode (PiP)", "Draggable Floating Overlay")
        AlertDialog.Builder(this)
            .setTitle("Select Mini Window Mode")
            .setItems(options) { _, which ->
                if (which == 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val params = PictureInPictureParams.Builder().build()
                        enterPictureInPictureMode(params)
                    } else {
                        Toast.makeText(this, "Picture-in-Picture requires Android 8.0+", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    startFloatingWidgetMode()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleTheme() {
        val currentNightMode = resources.configuration.uiMode and AndroidConfig.UI_MODE_NIGHT_MASK
        val newMode = if (currentNightMode == AndroidConfig.UI_MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putInt("NIGHT_MODE", newMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
        recreate()
    }

    private fun startFloatingWidgetMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            Toast.makeText(this, getString(R.string.overlay_permission_msg), Toast.LENGTH_LONG).show()
            startActivity(intent)
        } else {
            val serviceIntent = Intent(this, FloatingSpeedometerService::class.java).apply {
                putExtra("IS_MPH", isMph)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            moveTaskToBack(true)
        }
    }

    // ============================
    // Utility
    // ============================
    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val hrs = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
