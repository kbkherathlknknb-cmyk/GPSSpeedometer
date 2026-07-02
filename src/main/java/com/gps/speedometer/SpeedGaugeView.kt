package com.gps.speedometer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat

/**
 * Custom circular speed gauge view with integrated G-Force ring, Light/Dark adaptive colors, and Driving Modes.
 */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_SPEED = 200f       // km/h or mph max on gauge
        private const val MAX_GFORCE = 1.5f      // 1.5G max
        private const val ARC_START_ANGLE = 135f  // Start at bottom-left
        private const val ARC_SWEEP_ANGLE = 270f  // 270 degree sweep
        private const val HIGH_SPEED_THRESHOLD = 120f
    }

    private var currentSpeed = 0f
    private var targetSpeed = 0f
    private var currentGForce = 0f
    private var speedUnit = "km/h"
    private var driveMode = 0 // 0=Calm, 1=Eco, 2=Traffic, 3=Aggressive
    private var speedAnimator: ValueAnimator? = null

    // Colors loaded from theme and mode
    private var colorPrimary = Color.CYAN
    private var colorSecondary = Color.parseColor("#FF6D00")
    private var colorGlow = Color.BLUE
    private var colorSecondaryGlow = Color.parseColor("#33FF6D00")
    private var colorRing = Color.GREEN
    private var colorTrack = Color.DKGRAY
    private var textColor = Color.WHITE

    // Paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
    }

    private val gForceTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
    }

    private val gForceArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 32f
        strokeCap = Paint.Cap.ROUND
    }

    private val speedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
        letterSpacing = -0.05f
    }

    private val unitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.NORMAL)
        letterSpacing = 0.15f
    }

    private val gForceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
        letterSpacing = 0.05f
    }

    private val arcRect = RectF()
    private val gForceRect = RectF()

    init {
        loadColors()
    }

    fun loadColors() {
        colorTrack = ContextCompat.getColor(context, R.color.speed_cyan_dim)
        textColor = ContextCompat.getColor(context, R.color.text_secondary)
        updateModeColors()
    }

    fun setDriveMode(mode: Int) {
        this.driveMode = mode
        updateModeColors()
        invalidate()
    }

    private fun updateModeColors() {
        when (driveMode) {
            1 -> { // ECO MODE (Emerald & Lime)
                colorPrimary = Color.parseColor("#00E676")
                colorSecondary = Color.parseColor("#FFD54F")
                colorGlow = Color.parseColor("#3300E676")
                colorSecondaryGlow = Color.parseColor("#33FFD54F")
                colorRing = Color.parseColor("#69F0AE")
            }
            2 -> { // TRAFFIC MODE (Amber & Gold)
                colorPrimary = Color.parseColor("#FFB300")
                colorSecondary = Color.parseColor("#FF7043")
                colorGlow = Color.parseColor("#33FFB300")
                colorSecondaryGlow = Color.parseColor("#33FF7043")
                colorRing = Color.parseColor("#FFCA28")
            }
            3 -> { // AGGRESSIVE MODE (Racing Red & Fiery Orange)
                colorPrimary = Color.parseColor("#FF3D00")
                colorSecondary = Color.parseColor("#FFD600")
                colorGlow = Color.parseColor("#33FF3D00")
                colorSecondaryGlow = Color.parseColor("#33FFD600")
                colorRing = Color.parseColor("#FF6E40")
            }
            else -> { // CALM MODE (Cyan & Orange)
                colorPrimary = ContextCompat.getColor(context, R.color.speed_cyan)
                colorSecondary = ContextCompat.getColor(context, R.color.speed_orange)
                colorGlow = ContextCompat.getColor(context, R.color.speed_cyan_glow)
                colorSecondaryGlow = Color.parseColor("#33FF6D00")
                colorRing = ContextCompat.getColor(context, R.color.speed_green)
            }
        }

        trackPaint.color = colorTrack
        gForceTrackPaint.color = colorTrack
        unitTextPaint.color = textColor
        gForceTextPaint.color = colorRing
    }

    fun setUnit(unit: String) {
        this.speedUnit = unit
        invalidate()
    }

    fun setGForce(gForce: Float) {
        this.currentGForce = gForce.coerceIn(0f, MAX_GFORCE)
        invalidate()
    }

    fun setSpeed(speed: Float) {
        targetSpeed = speed.coerceIn(0f, MAX_SPEED)

        val animDuration = if (driveMode == 3) 200L else 400L // Faster animation in Aggressive mode!

        speedAnimator?.cancel()
        speedAnimator = ValueAnimator.ofFloat(currentSpeed, targetSpeed).apply {
            duration = animDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                currentSpeed = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - 28f
        val gRadius = radius - 26f

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        gForceRect.set(cx - gRadius, cy - gRadius, cx + gRadius, cy + gRadius)

        // 1. Draw tracks
        canvas.drawArc(arcRect, ARC_START_ANGLE, ARC_SWEEP_ANGLE, false, trackPaint)
        canvas.drawArc(gForceRect, ARC_START_ANGLE, ARC_SWEEP_ANGLE, false, gForceTrackPaint)

        // 2. Draw speed arc
        val ratio = currentSpeed / MAX_SPEED
        val sweepAngle = ARC_SWEEP_ANGLE * ratio

        if (sweepAngle > 0f) {
            val isHighSpeed = currentSpeed >= HIGH_SPEED_THRESHOLD
            val mainColor = if (isHighSpeed) colorSecondary else colorPrimary
            val glowCol = if (isHighSpeed) colorSecondaryGlow else colorGlow

            arcPaint.shader = SweepGradient(cx, cy, intArrayOf(colorPrimary, mainColor), null).apply {
                setLocalMatrix(Matrix().apply {
                    postRotate(ARC_START_ANGLE, cx, cy)
                })
            }

            glowPaint.color = glowCol
            canvas.drawArc(arcRect, ARC_START_ANGLE, sweepAngle, false, glowPaint)
            canvas.drawArc(arcRect, ARC_START_ANGLE, sweepAngle, false, arcPaint)
        }

        // 3. Draw G-Force arc (inner ring)
        val gRatio = currentGForce / MAX_GFORCE
        val gSweep = ARC_SWEEP_ANGLE * gRatio
        if (gSweep > 0f) {
            val gCol = when {
                currentGForce > 0.8f -> colorSecondary
                currentGForce > 0.4f -> colorPrimary
                else -> colorRing
            }
            gForceArcPaint.color = gCol
            canvas.drawArc(gForceRect, ARC_START_ANGLE, gSweep, false, gForceArcPaint)
        }

        // 4. Draw speed text
        val speedInt = currentSpeed.toInt()
        speedTextPaint.textSize = radius * 0.65f
        speedTextPaint.color = if (currentSpeed >= HIGH_SPEED_THRESHOLD) colorSecondary else colorPrimary
        val glowCol = if (currentSpeed >= HIGH_SPEED_THRESHOLD) colorSecondaryGlow else colorGlow
        speedTextPaint.setShadowLayer(25f, 0f, 0f, glowCol)

        canvas.drawText(speedInt.toString(), cx, cy + speedTextPaint.textSize * 0.18f, speedTextPaint)

        // 5. Draw unit text
        unitTextPaint.textSize = radius * 0.14f
        canvas.drawText(speedUnit, cx, cy + speedTextPaint.textSize * 0.18f + unitTextPaint.textSize * 2.2f, unitTextPaint)

        // 6. Draw G-force label at top center of inner arc
        if (currentGForce > 0.05f) {
            gForceTextPaint.textSize = radius * 0.12f
            gForceTextPaint.color = when {
                currentGForce > 0.8f -> colorSecondary
                currentGForce > 0.4f -> colorPrimary
                else -> colorRing
            }
            canvas.drawText(String.format("%.1f G", currentGForce), cx, cy - radius * 0.45f, gForceTextPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        speedAnimator?.cancel()
    }
}
