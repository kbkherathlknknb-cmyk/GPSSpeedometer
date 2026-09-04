package com.gps.speedometer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat

/**
 * Custom speed gauge view with unique shapes, fonts, and dial styles per driving mode.
 * CALM = thin circular arc, ECO = segmented blocks, TRAFFIC = thick half-gauge, RACE = sharp angular tachometer.
 */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_SPEED = 100f
        private const val MAX_GFORCE = 1.5f
        private const val HIGH_SPEED_THRESHOLD = 80f
    }

    private var currentSpeed = 0f
    private var targetSpeed = 0f
    private var currentGForce = 0f
    private var speedUnit = "km/h"
    private var driveMode = 0
    private var speedAnimator: ValueAnimator? = null
    private val speedInterpolator = DecelerateInterpolator()
    private var gradientCx = Float.NaN
    private var gradientCy = Float.NaN
    private var gradientPrimary = Int.MIN_VALUE
    private var gradientMain = Int.MIN_VALUE
    private var speedGradient: SweepGradient? = null
    private val gradientMatrix = Matrix()
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Mode-specific arc geometry
    private var arcStartAngle = 135f
    private var arcSweepAngle = 270f

    // Colors
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

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
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
            1 -> { // ECO — Segmented hexagonal blocks
                colorPrimary = Color.parseColor("#00E676")
                colorSecondary = Color.parseColor("#FFD54F")
                colorGlow = Color.parseColor("#3300E676")
                colorSecondaryGlow = Color.parseColor("#33FFD54F")
                colorRing = Color.parseColor("#69F0AE")
                arcStartAngle = 135f
                arcSweepAngle = 270f
                speedTextPaint.typeface = Typeface.MONOSPACE
                unitTextPaint.typeface = Typeface.MONOSPACE
                arcPaint.pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)
                arcPaint.strokeWidth = 20f
                arcPaint.strokeCap = Paint.Cap.BUTT
                trackPaint.strokeWidth = 20f
                trackPaint.pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)
                trackPaint.strokeCap = Paint.Cap.BUTT
            }
            2 -> { // TRAFFIC — Wide 180° half-moon gauge
                colorPrimary = Color.parseColor("#FFB300")
                colorSecondary = Color.parseColor("#FF7043")
                colorGlow = Color.parseColor("#33FFB300")
                colorSecondaryGlow = Color.parseColor("#33FF7043")
                colorRing = Color.parseColor("#FFCA28")
                arcStartAngle = 180f
                arcSweepAngle = 180f
                speedTextPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                unitTextPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                arcPaint.pathEffect = null
                arcPaint.strokeWidth = 28f
                arcPaint.strokeCap = Paint.Cap.ROUND
                trackPaint.strokeWidth = 28f
                trackPaint.pathEffect = null
                trackPaint.strokeCap = Paint.Cap.ROUND
            }
            3 -> { // RACE — Tight 300° tachometer ring
                colorPrimary = Color.parseColor("#FF3D00")
                colorSecondary = Color.parseColor("#FFD600")
                colorGlow = Color.parseColor("#33FF3D00")
                colorSecondaryGlow = Color.parseColor("#33FFD600")
                colorRing = Color.parseColor("#FF6E40")
                arcStartAngle = 120f
                arcSweepAngle = 300f
                speedTextPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
                unitTextPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
                arcPaint.pathEffect = null
                arcPaint.strokeWidth = 14f
                arcPaint.strokeCap = Paint.Cap.BUTT
                trackPaint.strokeWidth = 14f
                trackPaint.pathEffect = null
                trackPaint.strokeCap = Paint.Cap.BUTT
            }
            else -> { // CALM — Clean thin 270° arc
                colorPrimary = ContextCompat.getColor(context, R.color.speed_cyan)
                colorSecondary = ContextCompat.getColor(context, R.color.speed_orange)
                colorGlow = ContextCompat.getColor(context, R.color.speed_cyan_glow)
                colorSecondaryGlow = Color.parseColor("#33FF6D00")
                colorRing = ContextCompat.getColor(context, R.color.speed_green)
                arcStartAngle = 135f
                arcSweepAngle = 270f
                speedTextPaint.typeface = Typeface.create("sans-serif-light", Typeface.BOLD)
                unitTextPaint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                arcPaint.pathEffect = null
                arcPaint.strokeWidth = 12f
                arcPaint.strokeCap = Paint.Cap.ROUND
                trackPaint.strokeWidth = 12f
                trackPaint.pathEffect = null
                trackPaint.strokeCap = Paint.Cap.ROUND
            }
        }

        trackPaint.color = colorTrack
        gForceTrackPaint.color = colorTrack
        unitTextPaint.color = textColor
        gForceTextPaint.color = colorRing
        tickPaint.color = colorTrack
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
        val clamped = speed.coerceIn(0f, MAX_SPEED)
        if (kotlin.math.abs(clamped - targetSpeed) < 0.2f) return
        targetSpeed = clamped
        val animDuration = if (driveMode == 3) 180L else 400L
        if (speedAnimator == null) {
            speedAnimator = ValueAnimator.ofFloat(currentSpeed, targetSpeed).apply {
                interpolator = speedInterpolator
                addUpdateListener { anim ->
                    currentSpeed = anim.animatedValue as Float
                    invalidate()
                }
            }
        }
        speedAnimator?.cancel()
        speedAnimator?.duration = animDuration
        speedAnimator?.setFloatValues(currentSpeed, targetSpeed)
        speedAnimator?.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - 28f
        val gRadius = radius - 26f

        // Adjust center for TRAFFIC half-gauge (push center down so arc fills top)
        val effectiveCy = if (driveMode == 2) cy + radius * 0.25f else cy

        arcRect.set(cx - radius, effectiveCy - radius, cx + radius, effectiveCy + radius)
        gForceRect.set(cx - gRadius, effectiveCy - gRadius, cx + gRadius, effectiveCy + gRadius)

        // 1. Draw track arcs
        canvas.drawArc(arcRect, arcStartAngle, arcSweepAngle, false, trackPaint)
        if (driveMode != 2) { // Skip inner ring for half-gauge
            canvas.drawArc(gForceRect, arcStartAngle, arcSweepAngle, false, gForceTrackPaint)
        }

        // 2. Draw tick marks
        drawModeTicks(canvas, cx, effectiveCy, radius)

        // 3. Draw speed number labels on the arc
        if (driveMode == 3) {
            drawSpeedNumbers(canvas, cx, effectiveCy, radius)
        }

        // 4. Draw speed arc
        val ratio = currentSpeed / MAX_SPEED
        val sweepAngle = arcSweepAngle * ratio

        if (sweepAngle > 0f) {
            val isHighSpeed = currentSpeed >= HIGH_SPEED_THRESHOLD
            val mainColor = if (isHighSpeed) colorSecondary else colorPrimary
            val glowCol = if (isHighSpeed) colorSecondaryGlow else colorGlow

            if (speedGradient == null || cx != gradientCx || effectiveCy != gradientCy ||
                gradientPrimary != colorPrimary || gradientMain != mainColor) {
                speedGradient = SweepGradient(cx, effectiveCy, intArrayOf(colorPrimary, mainColor), null)
                gradientCx = cx
                gradientCy = effectiveCy
                gradientPrimary = colorPrimary
                gradientMain = mainColor
            }
            gradientMatrix.reset()
            gradientMatrix.postRotate(arcStartAngle, cx, effectiveCy)
            speedGradient?.setLocalMatrix(gradientMatrix)
            arcPaint.shader = speedGradient

            glowPaint.color = glowCol
            glowPaint.strokeWidth = if (driveMode == 2) 40f else 32f
            canvas.drawArc(arcRect, arcStartAngle, sweepAngle, false, glowPaint)
            canvas.drawArc(arcRect, arcStartAngle, sweepAngle, false, arcPaint)
        }

        // 5. Draw G-Force inner arc (except Traffic half-gauge)
        if (driveMode != 2) {
            val gRatio = currentGForce / MAX_GFORCE
            val gSweep = arcSweepAngle * gRatio
            if (gSweep > 0f) {
                val gCol = when {
                    currentGForce > 0.8f -> colorSecondary
                    currentGForce > 0.4f -> colorPrimary
                    else -> colorRing
                }
                gForceArcPaint.color = gCol
                canvas.drawArc(gForceRect, arcStartAngle, gSweep, false, gForceArcPaint)
            }
        }

        // 6. Draw speed text — large and instantly readable
        val speedInt = currentSpeed.toInt()
        speedTextPaint.textSize = radius * 0.85f
        speedTextPaint.letterSpacing = -0.06f
        speedTextPaint.color = if (currentSpeed >= HIGH_SPEED_THRESHOLD) colorSecondary else colorPrimary
        val textGlow = if (currentSpeed >= HIGH_SPEED_THRESHOLD) colorSecondaryGlow else colorGlow
        speedTextPaint.setShadowLayer(30f, 0f, 0f, textGlow)

        val textCy = if (driveMode == 2) effectiveCy - radius * 0.05f else effectiveCy + speedTextPaint.textSize * 0.2f
        canvas.drawText(speedInt.toString(), cx, textCy, speedTextPaint)

        // 7. Draw unit text
        unitTextPaint.textSize = radius * 0.17f
        canvas.drawText(speedUnit, cx, textCy + unitTextPaint.textSize * 1.8f, unitTextPaint)

        // 8. Draw G-force label
        if (currentGForce > 0.05f) {
            gForceTextPaint.textSize = radius * 0.12f
            gForceTextPaint.color = when {
                currentGForce > 0.8f -> colorSecondary
                currentGForce > 0.4f -> colorPrimary
                else -> colorRing
            }
            val gLabelY = if (driveMode == 2) effectiveCy - radius * 0.65f else effectiveCy - radius * 0.45f
            canvas.drawText(String.format("%.1f G", currentGForce), cx, gLabelY, gForceTextPaint)
        }

        // 9. Draw needle for RACE mode
        if (driveMode == 3 && currentSpeed > 0f) {
            drawNeedle(canvas, cx, effectiveCy, radius)
        }
    }

    private fun drawModeTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val tickCount: Int
        val outerR: Float
        val innerR: Float

        when (driveMode) {
            3 -> { // RACE: 40 sharp racing ticks, every 5th is longer
                tickCount = 40
                outerR = radius + 16f
                innerR = radius + 4f
                tickPaint.strokeWidth = 2.5f
                tickPaint.color = Color.parseColor("#55FF3D00")
            }
            2 -> { // TRAFFIC: 10 bold urban markers
                tickCount = 10
                outerR = radius + 18f
                innerR = radius + 4f
                tickPaint.strokeWidth = 5f
                tickPaint.color = Color.parseColor("#55FFB300")
            }
            1 -> { // ECO: 12 dots
                tickCount = 12
                outerR = radius + 12f
                innerR = radius + 6f
                tickPaint.strokeWidth = 3f
                tickPaint.color = colorTrack
            }
            else -> { // CALM: 8 elegant dots
                tickCount = 8
                outerR = radius + 12f
                innerR = radius + 6f
                tickPaint.strokeWidth = 3f
                tickPaint.color = colorTrack
            }
        }

        val stepAngle = arcSweepAngle / tickCount
        for (i in 0..tickCount) {
            val angleDeg = arcStartAngle + i * stepAngle
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val isMajor = (driveMode == 3 && i % 5 == 0)
            val actualInner = if (isMajor) radius - 2f else innerR
            val x1 = cx + (actualInner * Math.cos(angleRad)).toFloat()
            val y1 = cy + (actualInner * Math.sin(angleRad)).toFloat()
            val x2 = cx + (outerR * Math.cos(angleRad)).toFloat()
            val y2 = cy + (outerR * Math.sin(angleRad)).toFloat()
            if (isMajor) {
                tickPaint.strokeWidth = 4f
                tickPaint.color = Color.parseColor("#AAFF3D00")
            }
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
            if (isMajor) {
                tickPaint.strokeWidth = 2.5f
                tickPaint.color = Color.parseColor("#55FF3D00")
            }
        }
    }

    private fun drawSpeedNumbers(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        numberPaint.textSize = radius * 0.11f
        numberPaint.color = Color.parseColor("#88FF6E40")
        numberPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

        val numR = radius + 26f
        val numCount = 8
        val step = arcSweepAngle / numCount
        val speedStep = MAX_SPEED / numCount

        for (i in 0..numCount) {
            val angleDeg = arcStartAngle + i * step
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val x = cx + (numR * Math.cos(angleRad)).toFloat()
            val y = cy + (numR * Math.sin(angleRad)).toFloat() + numberPaint.textSize * 0.35f
            val speedVal = (i * speedStep).toInt()
            canvas.drawText(speedVal.toString(), x, y, numberPaint)
        }
    }

    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val ratio = currentSpeed / MAX_SPEED
        val angleDeg = arcStartAngle + arcSweepAngle * ratio
        val angleRad = Math.toRadians(angleDeg.toDouble())

        val needleLength = radius * 0.75f
        val endX = cx + (needleLength * Math.cos(angleRad)).toFloat()
        val endY = cy + (needleLength * Math.sin(angleRad)).toFloat()

        needlePaint.color = if (currentSpeed >= HIGH_SPEED_THRESHOLD) colorSecondary else colorPrimary
        needlePaint.setShadowLayer(12f, 0f, 0f, colorGlow)
        canvas.drawLine(cx, cy, endX, endY, needlePaint)

        // Center dot
        dotPaint.color = colorPrimary
        canvas.drawCircle(cx, cy, 8f, dotPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        speedAnimator?.cancel()
    }
}
