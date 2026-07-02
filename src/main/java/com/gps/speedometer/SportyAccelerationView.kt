package com.gps.speedometer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * High-tech sporty F1-style acceleration telemetry gauge and 0-50 km/h sprint timer.
 */
class SportyAccelerationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TOTAL_SEGMENTS = 30
        private const val CENTER_SEGMENT = 15
        private const val MAX_G_DISPLAY = 1.2f
    }

    private var currentGForce = 0f
    private var currentAccelMs2 = 0f
    private var driveMode = 0 // 0=Calm, 1=Eco, 2=Traffic, 3=Aggressive
    private var isMph = false

    // 0-50 / 0-30 Sprint Timer
    private var sprintStartTime = 0L
    private var isSprintRunning = false
    private var lastSprintDisplay = "⚡ 0-50 km/h: READY"

    private val segmentRect = RectF()
    private val paintLit = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintDim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("monospace", Typeface.NORMAL)
    }

    // Colors
    private var colorPrimary = Color.CYAN
    private var colorAccent = Color.GREEN
    private var colorWarn = Color.YELLOW
    private var colorBrake = Color.parseColor("#FF3D00")
    private var colorDim = Color.parseColor("#1F00E5FF")
    private var textPrimaryCol = Color.WHITE
    private var textMutedCol = Color.LTGRAY

    init {
        loadColors()
    }

    fun loadColors() {
        textPrimaryCol = ContextCompat.getColor(context, R.color.text_primary)
        textMutedCol = ContextCompat.getColor(context, R.color.text_muted)
        updateModeColors()
    }

    fun setDriveMode(mode: Int) {
        this.driveMode = mode
        updateModeColors()
        invalidate()
    }

    fun setUnit(isMph: Boolean) {
        this.isMph = isMph
        if (!isSprintRunning) {
            lastSprintDisplay = if (isMph) "⚡ 0-30 mph: READY" else "⚡ 0-50 km/h: READY"
        }
        invalidate()
    }

    private fun updateModeColors() {
        when (driveMode) {
            1 -> { // ECO
                colorPrimary = Color.parseColor("#00E676")
                colorAccent = Color.parseColor("#69F0AE")
                colorWarn = Color.parseColor("#FFD54F")
                colorDim = Color.parseColor("#1F00E676")
            }
            2 -> { // TRAFFIC
                colorPrimary = Color.parseColor("#FFB300")
                colorAccent = Color.parseColor("#FFCA28")
                colorWarn = Color.parseColor("#FF7043")
                colorDim = Color.parseColor("#1FFFB300")
            }
            3 -> { // AGGRESSIVE
                colorPrimary = Color.parseColor("#FF3D00")
                colorAccent = Color.parseColor("#FF6E40")
                colorWarn = Color.parseColor("#FFAB00")
                colorDim = Color.parseColor("#1FFF3D00")
            }
            else -> { // CALM
                colorPrimary = Color.parseColor("#00E5FF")
                colorAccent = Color.parseColor("#80D8FF")
                colorWarn = Color.parseColor("#FFD180")
                colorDim = Color.parseColor("#1F00E5FF")
            }
        }
        paintDim.color = colorDim
    }

    fun updateTelemetry(accelerationMs2: Float, gForce: Float, speedMs: Float) {
        this.currentAccelMs2 = accelerationMs2
        this.currentGForce = gForce

        // Check Sprint Timer
        val speedVal = if (isMph) speedMs * 2.23694f else speedMs * 3.6f
        val targetSpeed = if (isMph) 30f else 50f
        val sprintLabel = if (isMph) "0-30 mph" else "0-50 km/h"

        if (speedVal < 1.0f) {
            if (isSprintRunning) {
                isSprintRunning = false
            }
            lastSprintDisplay = "⚡ $sprintLabel: READY"
        } else if (!isSprintRunning && speedVal in 1.0f..15.0f && gForce > 0.18f) {
            isSprintRunning = true
            sprintStartTime = System.currentTimeMillis()
            lastSprintDisplay = "⚡ $sprintLabel: SPRINTING..."
        } else if (isSprintRunning) {
            val elapsed = (System.currentTimeMillis() - sprintStartTime) / 1000f
            if (speedVal >= targetSpeed) {
                isSprintRunning = false
                lastSprintDisplay = String.format("⚡ %s: %.2fs 🏆", sprintLabel, elapsed)
            } else {
                lastSprintDisplay = String.format("⚡ %s: %.1fs...", sprintLabel, elapsed)
            }
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (w < 10 || h < 10) return

        // 1. Draw telemetry labels at top
        labelPaint.textSize = h * 0.16f
        labelPaint.color = colorPrimary
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("LAUNCH +G", 8f, h * 0.22f, labelPaint)

        labelPaint.color = colorWarn
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("PEAK +1.2G", w - 8f, h * 0.22f, labelPaint)

        textPaint.textSize = h * 0.18f
        textPaint.color = textPrimaryCol
        val displayG = maxOf(0f, currentGForce)
        val displayAccel = maxOf(0f, currentAccelMs2)
        val centerStr = String.format("+%.1f m/s² (+%.2f G)", displayAccel, displayG)
        canvas.drawText(centerStr, w / 2f, h * 0.22f, textPaint)

        // 2. Draw 30 segmented LED bar from left (index 0) to right (index 29)
        val barTop = h * 0.32f
        val barBottom = h * 0.62f
        val totalPadding = w * 0.04f
        val availableWidth = w - totalPadding
        val segmentGap = 4f
        val segmentWidth = (availableWidth - (TOTAL_SEGMENTS - 1) * segmentGap) / TOTAL_SEGMENTS
        val startX = totalPadding / 2f

        val gRatio = (displayG / MAX_G_DISPLAY).coerceIn(0f, 1f)
        val litCount = (gRatio * TOTAL_SEGMENTS).roundToInt()

        for (i in 0 until TOTAL_SEGMENTS) {
            val left = startX + i * (segmentWidth + segmentGap)
            segmentRect.set(left, barTop, left + segmentWidth, barBottom)

            var isLit = false
            var segColor = colorDim

            if (i < litCount || i == 0) { // Keep 1st LED lit as ready indicator
                isLit = true
                segColor = when {
                    i >= 24 -> colorBrake // Red/Orange peak
                    i >= 16 -> colorWarn  // Yellow mid-high
                    i >= 8 -> colorAccent // Green/Bright mid
                    else -> colorPrimary  // Base color
                }
            }

            if (isLit) {
                paintLit.color = segColor
                paintLit.setShadowLayer(8f, 0f, 0f, segColor)
                canvas.drawRoundRect(segmentRect, 4f, 4f, paintLit)
            } else {
                canvas.drawRoundRect(segmentRect, 4f, 4f, paintDim)
            }
        }

        // 3. Draw Sprint Timer at bottom
        textPaint.textSize = h * 0.22f
        textPaint.color = if (lastSprintDisplay.contains("🏆")) colorWarn else textPrimaryCol
        textPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        canvas.drawText(lastSprintDisplay, w / 2f, h * 0.92f, textPaint)
    }
}
