package com.gps.speedometer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.*

class  SensorEngine(context: Context) : SensorEventListener {

    interface SensorCallback {
        fun onHeadingChanged(azimuth: Float, cardinal: String)
        fun onAccelerationChanged(accelerationMs2: Float, gForce: Float)
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val linearAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private var callback: SensorCallback? = null

    // Fallback orientation calculation
    private val accelerometerValues = FloatArray(3)
    private val magnetometerValues = FloatArray(3)
    private var hasAccelerometer = false
    private var hasMagnetometer = false
    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    // Low-pass filter for smoothing
    private var smoothedAzimuth = -1f
    private var smoothedAccel = 0f
    private val ALPHA_HEADING = 0.15f
    private val ALPHA_ACCEL = 0.2f

    private val CARDINALS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun start(callback: SensorCallback) {
        this.callback = callback
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }

        if (linearAccelSensor != null) {
            sensorManager.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_UI)
        } else if (rotationVectorSensor != null && accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        callback = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationValues)
                var azimuth = (Math.toDegrees(orientationValues[0].toDouble()).toFloat() + 360f) % 360f
                updateHeading(azimuth)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (rotationVectorSensor == null) {
                    System.arraycopy(event.values, 0, accelerometerValues, 0, 3)
                    hasAccelerometer = true
                    calculateFallbackOrientation()
                }
                if (linearAccelSensor == null) {
                    // Approximate linear acceleration by removing gravity (roughly ~9.8)
                    val totalAccel = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2])
                    val linear = max(0f, abs(totalAccel - 9.80665f))
                    updateAcceleration(linear)
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (rotationVectorSensor == null) {
                    System.arraycopy(event.values, 0, magnetometerValues, 0, 3)
                    hasMagnetometer = true
                    calculateFallbackOrientation()
                }
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val linear = sqrt(x * x + y * y + z * z)
                updateAcceleration(linear)
            }
        }
    }

    private fun calculateFallbackOrientation() {
        if (hasAccelerometer && hasMagnetometer) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerValues, magnetometerValues)) {
                SensorManager.getOrientation(rotationMatrix, orientationValues)
                val azimuth = (Math.toDegrees(orientationValues[0].toDouble()).toFloat() + 360f) % 360f
                updateHeading(azimuth)
            }
        }
    }

    private fun updateHeading(rawAzimuth: Float) {
        if (smoothedAzimuth < 0f) {
            smoothedAzimuth = rawAzimuth
        } else {
            // Handle 360 -> 0 wrap around smoothly
            var diff = rawAzimuth - smoothedAzimuth
            if (diff < -180f) diff += 360f
            else if (diff > 180f) diff -= 360f
            smoothedAzimuth = (smoothedAzimuth + ALPHA_HEADING * diff + 360f) % 360f
        }

        val cardinalIndex = ((smoothedAzimuth + 22.5f) % 360f / 45f).toInt()
        val cardinal = CARDINALS[cardinalIndex % 8]
        callback?.onHeadingChanged(smoothedAzimuth, cardinal)
    }

    private fun updateAcceleration(rawLinearMs2: Float) {
        // Apply low-pass filter to linear acceleration
        val filtered = if (rawLinearMs2 < 0.15f) 0f else rawLinearMs2
        smoothedAccel = smoothedAccel + ALPHA_ACCEL * (filtered - smoothedAccel)
        val displayAccel = if (smoothedAccel < 0.1f) 0f else smoothedAccel
        val gForce = displayAccel / 9.80665f
        callback?.onAccelerationChanged(displayAccel, gForce)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
