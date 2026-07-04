package com.gps.speedometer

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
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
import android.util.Rational
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.location.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class MainActivity : AppCompatActivity(), SensorEngine.SensorCallback {

    // ============================
    // Top Bar & ViewPager
    // ============================
    private lateinit var statusBar: View
    private lateinit var swipeHint: View
    private lateinit var gpsIndicator: View
    private lateinit var gpsStatusText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var btnUnitToggle: TextView
    private lateinit var btnMenu: ImageView
    private lateinit var viewPager: ViewPager2

    // ============================
    // Page 0: Speedometer Dashboard
    // ============================
    private lateinit var speedGauge: SpeedGaugeView
    private lateinit var sportyAccelView: SportyAccelerationView
    private lateinit var compassArrow: ImageView
    private lateinit var headingText: TextView
    private lateinit var cardinalText: TextView
    private lateinit var maxSpeedText: TextView
    private lateinit var avgSpeedText: TextView
    private lateinit var distanceText: TextView
    private lateinit var totalOdoText: TextView
    private lateinit var tripTimeText: TextView
    private lateinit var btnResetTrip: View
    private lateinit var btnTripLog: View
    private lateinit var statsGroup: View
    private lateinit var tripActionsBar: View

    // ============================
    // Page 1: Trip Log
    // ============================
    private lateinit var tripLogItemsContainer: LinearLayout
    private lateinit var tripLogEmptyState: View
    private lateinit var tripLogCount: TextView
    private lateinit var btnClearTripLog: View

    // ============================
    // Location & Sensor Engine
    // ============================
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorEngine: SensorEngine
    private var currentLocation: Location? = null
    private var isMph = false
    private var driveMode = 0

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

    // Speed smoothing
    private val speedHistory = mutableListOf<Float>()
    private val SMOOTH_SIZE = 3

    // Timer
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (tripActive && tripStartTime > 0 && ::tripTimeText.isInitialized) {
                tripTimeText.text = formatDuration(System.currentTimeMillis() - tripStartTime)
            }
            timerHandler.postDelayed(this, 1000)
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            startLocationUpdates()
        } else {
            setGpsStatus("disconnected", getString(R.string.gps_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        driveMode = prefs.getInt("DRIVE_MODE", 0)
        isMph = prefs.getBoolean("IS_MPH", false)
        totalOdoKm = prefs.getFloat("TOTAL_ODO_KM", 0f).toDouble()

        setContentView(R.layout.activity_main)
        sensorEngine = SensorEngine(this)

        bindTopBar()
        setupViewPager()
        applyDriveModeAndUnit()
        setupTopBarListeners()

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
    }

    private fun bindTopBar() {
        statusBar = findViewById(R.id.statusBar)
        swipeHint = findViewById(R.id.swipeHint)
        gpsIndicator = findViewById(R.id.gpsIndicator)
        gpsStatusText = findViewById(R.id.gpsStatusText)
        accuracyText = findViewById(R.id.accuracyText)
        btnUnitToggle = findViewById(R.id.btnUnitToggle)
        btnMenu = findViewById(R.id.btnMenu)
        viewPager = findViewById(R.id.viewPager)
    }

    private fun setupViewPager() {
        val dashboardView = layoutInflater.inflate(R.layout.page_dashboard, null)
        val tripLogView = layoutInflater.inflate(R.layout.page_trip_log, null)

        // Bind Dashboard views
        speedGauge = dashboardView.findViewById(R.id.speedGauge)
        sportyAccelView = dashboardView.findViewById(R.id.sportyAccelView)
        compassArrow = dashboardView.findViewById(R.id.compassArrow)
        headingText = dashboardView.findViewById(R.id.headingText)
        cardinalText = dashboardView.findViewById(R.id.cardinalText)
        maxSpeedText = dashboardView.findViewById(R.id.maxSpeed)
        avgSpeedText = dashboardView.findViewById(R.id.avgSpeed)
        distanceText = dashboardView.findViewById(R.id.distanceText)
        totalOdoText = dashboardView.findViewById(R.id.totalOdoText)
        tripTimeText = dashboardView.findViewById(R.id.tripTime)
        btnResetTrip = dashboardView.findViewById(R.id.btnResetTrip)
        btnTripLog = dashboardView.findViewById(R.id.btnTripLog)
        statsGroup = dashboardView.findViewById(R.id.statsGroup)
        tripActionsBar = dashboardView.findViewById(R.id.tripActionsBar)

        btnResetTrip.setOnClickListener { confirmResetTrip() }
        btnTripLog.setOnClickListener { viewPager.currentItem = 1 }

        // Bind Trip Log views
        tripLogItemsContainer = tripLogView.findViewById(R.id.tripLogItemsContainer)
        tripLogEmptyState = tripLogView.findViewById(R.id.emptyLogState)
        tripLogCount = tripLogView.findViewById(R.id.tripLogCount)
        btnClearTripLog = tripLogView.findViewById(R.id.btnClearTripLog)

        btnClearTripLog.setOnClickListener {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit().putString("TRIP_LOG_JSON", "[]").apply()
            refreshTripLogPage()
            Toast.makeText(this, "Trip log cleared!", Toast.LENGTH_SHORT).show()
        }

        val pages = listOf(dashboardView, tripLogView)
        viewPager.offscreenPageLimit = pages.size

        viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = pages[viewType]
                (v.parent as? ViewGroup)?.removeView(v)
                v.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                return object : RecyclerView.ViewHolder(v) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemCount(): Int = pages.size
            override fun getItemViewType(position: Int): Int = position
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                swipeHint.visibility = if (position == 0) View.VISIBLE else View.GONE
                if (position == 1) refreshTripLogPage()
            }
        })
    }

    private fun refreshTripLogPage() {
        if (!::tripLogItemsContainer.isInitialized) return
        tripLogItemsContainer.removeAllViews()
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val jsonStr = prefs.getString("TRIP_LOG_JSON", "[]") ?: "[]"
        val array = JSONArray(jsonStr)

        if (array.length() == 0) {
            tripLogEmptyState.visibility = View.VISIBLE
            tripLogCount.text = "0 trips"
        } else {
            tripLogEmptyState.visibility = View.GONE
            tripLogCount.text = "${array.length()} trips"
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val itemView = layoutInflater.inflate(R.layout.item_trip_log, tripLogItemsContainer, false)
                itemView.findViewById<TextView>(R.id.tripItemDate).text = "📅 ${o.getString("date")}"
                itemView.findViewById<TextView>(R.id.tripItemDuration).text = "⏱️ ${o.getString("duration")}"
                itemView.findViewById<TextView>(R.id.tripItemDistance).text = o.getString("distance")
                itemView.findViewById<TextView>(R.id.tripItemMaxSpeed).text = o.getString("maxSpeed")
                itemView.findViewById<TextView>(R.id.tripItemAvgSpeed).text = o.getString("avgSpeed")
                tripLogItemsContainer.addView(itemView)
            }
        }
    }

    private fun setupTopBarListeners() {
        btnUnitToggle.setOnClickListener {
            isMph = !isMph
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putBoolean("IS_MPH", isMph).apply()
            applyDriveModeAndUnit()
            currentLocation?.let { onLocationUpdate(it) }
        }
        btnMenu.setOnClickListener { showThreeDotMenu() }
    }

    private fun applyDriveModeAndUnit() {
        if (::speedGauge.isInitialized) {
            speedGauge.setDriveMode(driveMode)
            speedGauge.setUnit(if (isMph) "mph" else "km/h")
        }
        if (::sportyAccelView.isInitialized) {
            sportyAccelView.setDriveMode(driveMode)
            sportyAccelView.setUnit(isMph)
        }
        btnUnitToggle.text = if (isMph) "MPH" else "KM/H"
        updateOdoText()
    }

    private fun updateOdoText() {
        if (::totalOdoText.isInitialized) {
            val odoVal = if (isMph) totalOdoKm * 0.621371 else totalOdoKm
            totalOdoText.text = String.format("%.1f", odoVal)
        }
    }

    // ============================
    // Sensor Callbacks
    // ============================
    override fun onHeadingChanged(azimuth: Float, cardinal: String) {
        if (::headingText.isInitialized) {
            headingText.text = "${azimuth.roundToInt()}°"
            cardinalText.text = cardinal
            ObjectAnimator.ofFloat(compassArrow, "rotation", compassArrow.rotation, azimuth).apply {
                duration = 300; start()
            }
        }
    }

    override fun onAccelerationChanged(accelerationMs2: Float, gForce: Float) {
        if (::sportyAccelView.isInitialized) {
            val speedMs = currentLocation?.let { if (it.hasSpeed()) it.speed else 0f } ?: 0f
            sportyAccelView.updateTelemetry(accelerationMs2, gForce, speedMs)
            speedGauge.setGForce(gForce)
        }
    }

    // ============================
    // PiP Mode — Show only the gauge
    // ============================
    override fun onPictureInPictureModeChanged(isInPiP: Boolean, newConfig: AndroidConfig) {
        super.onPictureInPictureModeChanged(isInPiP, newConfig)
        if (isInPiP) {
            statusBar.visibility = View.GONE
            swipeHint.visibility = View.GONE
            if (::statsGroup.isInitialized) statsGroup.visibility = View.GONE
            if (::tripActionsBar.isInitialized) tripActionsBar.visibility = View.GONE
            if (::sportyAccelView.isInitialized) sportyAccelView.visibility = View.GONE
            // Hide compass in PiP
            try { findViewById<View>(R.id.compassGroup)?.visibility = View.GONE } catch (_: Exception) {}
            // Force ViewPager to dashboard
            viewPager.currentItem = 0
        } else {
            statusBar.visibility = View.VISIBLE
            swipeHint.visibility = View.VISIBLE
            if (::statsGroup.isInitialized) statsGroup.visibility = View.VISIBLE
            if (::tripActionsBar.isInitialized) tripActionsBar.visibility = View.VISIBLE
            if (::sportyAccelView.isInitialized) sportyAccelView.visibility = View.VISIBLE
            try { findViewById<View>(R.id.compassGroup)?.visibility = View.VISIBLE } catch (_: Exception) {}
        }
    }

    // ============================
    // Location
    // ============================
    private fun checkAndRequestPermissions() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> startLocationUpdates()
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                AlertDialog.Builder(this)
                    .setTitle("GPS Permission Required")
                    .setMessage(getString(R.string.permission_rationale))
                    .setPositiveButton("Grant") { _, _ ->
                        locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                    .setNegativeButton("Cancel", null).show()
            }
            else -> locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        setGpsStatus("searching", getString(R.string.gps_searching))
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500).setWaitForAccurateLocation(false).build()
        fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onLocationUpdate(it) }
            }
        }
    }

    private fun onLocationUpdate(location: Location) {
        currentLocation = location
        val accuracy = location.accuracy
        if (accuracy <= 20f) setGpsStatus("connected", getString(R.string.gps_active))
        else if (accuracy <= 50f) setGpsStatus("connected", getString(R.string.gps_active))
        else setGpsStatus("searching", "Low accuracy")
        accuracyText.text = "±${accuracy.toInt()}m"

        var speedRaw = if (location.hasSpeed() && location.speed >= 0f) location.speed else 0f
        val converted = if (isMph) speedRaw * 2.23694f else speedRaw * 3.6f
        speedHistory.add(converted)
        if (speedHistory.size > SMOOTH_SIZE) speedHistory.removeAt(0)
        val smoothed = speedHistory.average().toFloat()
        val display = if (smoothed < 1.5f) 0f else smoothed

        if (::speedGauge.isInitialized) {
            speedGauge.setSpeed(display)
            updateTripStatsAndOdo(display, location)
        }
    }

    private fun setGpsStatus(status: String, text: String) {
        gpsStatusText.text = text
        gpsIndicator.setBackgroundResource(when (status) {
            "connected" -> R.drawable.gps_dot_green
            "searching" -> R.drawable.gps_dot_orange
            else -> R.drawable.gps_dot_red
        })
    }

    // ============================
    // Trip Stats
    // ============================
    private fun updateTripStatsAndOdo(speed: Float, location: Location) {
        if (speed > 0f && !tripActive) {
            tripActive = true
            if (tripStartTime == 0L) tripStartTime = System.currentTimeMillis()
        }
        if (speed > 0f) {
            if (speed > tripMaxSpeed) { tripMaxSpeed = speed; maxSpeedText.text = tripMaxSpeed.toInt().toString() }
            tripTotalSpeed += speed; tripSpeedCount++
            avgSpeedText.text = (tripTotalSpeed / tripSpeedCount).toInt().toString()
        }
        if (location.accuracy <= 20f && location.hasSpeed() && location.speed >= 0.8f) {
            lastTripLocation?.let { last ->
                val dtSec = (location.time - last.time) / 1000f
                if (dtSec in 0.2f..60f) {
                    val dGps = last.distanceTo(location)
                    val dDop = location.speed * dtSec
                    val dM = minOf(dGps, dDop * 1.3f)
                    if (dM > 0.5f) {
                        val dKm = dM / 1000.0
                        tripDistance += (if (isMph) dKm * 0.621371 else dKm).toFloat()
                        distanceText.text = String.format("%.1f", tripDistance)
                        totalOdoKm += dKm
                        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putFloat("TOTAL_ODO_KM", totalOdoKm.toFloat()).apply()
                        updateOdoText()
                    }
                }
            }
        }
        lastTripLocation = location
    }

    private fun confirmResetTrip() {
        AlertDialog.Builder(this)
            .setTitle("Reset Trip Data?")
            .setMessage("Save this trip to your Trip Log before resetting?")
            .setPositiveButton("Save & Reset") { _, _ -> saveCurrentTripToLog(); resetTrip() }
            .setNegativeButton("Just Reset") { _, _ -> resetTrip() }
            .setNeutralButton("Cancel", null).show()
    }

    private fun resetTrip() {
        tripStartTime = 0; tripMaxSpeed = 0f; tripTotalSpeed = 0f; tripSpeedCount = 0
        tripDistance = 0f; tripActive = false
        if (::maxSpeedText.isInitialized) {
            maxSpeedText.text = "0"; avgSpeedText.text = "0"
            distanceText.text = "0.0"; tripTimeText.text = "00:00"
        }
    }

    private fun saveCurrentTripToLog() {
        if (tripDistance < 0.1f) { Toast.makeText(this, "Trip too short!", Toast.LENGTH_SHORT).show(); return }
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val oldArray = JSONArray(prefs.getString("TRIP_LOG_JSON", "[]") ?: "[]")
        val dateStr = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())
        val durStr = if (tripStartTime > 0) formatDuration(System.currentTimeMillis() - tripStartTime) else "00:00"
        val avgVal = if (tripSpeedCount > 0) (tripTotalSpeed / tripSpeedCount).toInt() else 0
        val unit = if (isMph) "mph" else "km/h"
        val distUnit = if (isMph) "mi" else "km"

        val newTrip = JSONObject().apply {
            put("date", dateStr); put("distance", String.format("%.1f %s", tripDistance, distUnit))
            put("duration", durStr); put("maxSpeed", "${tripMaxSpeed.toInt()} $unit")
            put("avgSpeed", "$avgVal $unit")
        }

        // Build a new array: new trip first, then all existing trips (prepend, not replace)
        val newArray = JSONArray()
        newArray.put(newTrip)
        for (i in 0 until oldArray.length()) {
            newArray.put(oldArray.getJSONObject(i))
        }

        // Keep max 50 trips to avoid unbounded storage growth
        val trimmed = JSONArray()
        for (i in 0 until minOf(newArray.length(), 50)) {
            trimmed.put(newArray.getJSONObject(i))
        }

        prefs.edit().putString("TRIP_LOG_JSON", trimmed.toString()).apply()
        Toast.makeText(this, "Trip saved! 📝", Toast.LENGTH_SHORT).show()
    }

    // ============================
    // Three-Dot Menu
    // ============================
    private fun showThreeDotMenu() {
        val dv = layoutInflater.inflate(R.layout.dialog_three_dot_menu, null)
        val dialog = AlertDialog.Builder(this).setView(dv).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnCalm = dv.findViewById<TextView>(R.id.modeCalmBtn)
        val btnEco = dv.findViewById<TextView>(R.id.modeEcoBtn)
        val btnTraffic = dv.findViewById<TextView>(R.id.modeTrafficBtn)
        val btnRace = dv.findViewById<TextView>(R.id.modeAggressiveBtn)

        fun hl(sel: Int) {
            btnCalm.alpha = if (sel == 0) 1f else 0.4f
            btnEco.alpha = if (sel == 1) 1f else 0.4f
            btnTraffic.alpha = if (sel == 2) 1f else 0.4f
            btnRace.alpha = if (sel == 3) 1f else 0.4f
        }
        hl(driveMode)

        fun sel(mode: Int, name: String) {
            driveMode = mode
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putInt("DRIVE_MODE", driveMode).apply()
            applyDriveModeAndUnit(); hl(mode)
            Toast.makeText(this, "Switched to $name", Toast.LENGTH_SHORT).show()
        }

        btnCalm.setOnClickListener { sel(0, "Calm Mode") }
        btnEco.setOnClickListener { sel(1, "Eco Mode") }
        btnTraffic.setOnClickListener { sel(2, "Traffic Mode") }
        btnRace.setOnClickListener { sel(3, "Race Mode") }

        dv.findViewById<View>(R.id.menuTripLogBtn).setOnClickListener { dialog.dismiss(); viewPager.currentItem = 1 }
        dv.findViewById<View>(R.id.menuPipBtn).setOnClickListener {
            dialog.dismiss()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(1, 1))
                    .build()
                enterPictureInPictureMode(params)
            } else Toast.makeText(this, "PiP requires Android 8.0+", Toast.LENGTH_SHORT).show()
        }
        dv.findViewById<View>(R.id.menuOverlayBtn).setOnClickListener { dialog.dismiss(); startFloatingWidget() }
        dv.findViewById<View>(R.id.menuThemeBtn).setOnClickListener { dialog.dismiss(); toggleTheme() }
        dv.findViewById<View>(R.id.menuCloseBtn).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun toggleTheme() {
        val cur = resources.configuration.uiMode and AndroidConfig.UI_MODE_NIGHT_MASK
        val newMode = if (cur == AndroidConfig.UI_MODE_NIGHT_YES) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putInt("NIGHT_MODE", newMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
        recreate()
    }

    private fun startFloatingWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.overlay_permission_msg), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            val si = Intent(this, FloatingSpeedometerService::class.java).apply { putExtra("IS_MPH", isMph) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(si) else startService(si)
            moveTaskToBack(true)
        }
    }

    // ============================
    // Utility
    // ============================
    private fun formatDuration(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
    }
}
