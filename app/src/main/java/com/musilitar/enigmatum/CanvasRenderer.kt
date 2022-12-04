package com.musilitar.enigmatum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.WatchFaceLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin

class CanvasRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : Renderer.CanvasRenderer2<CanvasRenderer.SharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    16L,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {
    class SharedAssets : Renderer.SharedAssets {
        override fun onDestroy() {
        }
    }

    override suspend fun createSharedAssets(): SharedAssets {
        return SharedAssets()
    }

    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var data: Data = Data()

    // Converts resource ids into Colors and ComplicationDrawable.
    private var watchFaceColors = convertToWatchFaceColorPalette(
        context,
        data.activeColorStyle,
        data.ambientColorStyle
    )

    // Initializes paint object for painting the clock hands with default values.
    private val clockHandPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth =
            context.resources.getDimensionPixelSize(R.dimen.clock_hand_stroke_width).toFloat()
    }

    private val outerElementPaint = Paint().apply {
        isAntiAlias = true
    }

    // Used to paint the main hour hand text with the hour pips, i.e., 3, 6, 9, and 12 o'clock.
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = context.resources.getDimensionPixelSize(R.dimen.hour_mark_size).toFloat()
    }

    private lateinit var hourHandFill: Path
    private lateinit var hourHandBorder: Path
    private lateinit var minuteHandFill: Path
    private lateinit var minuteHandBorder: Path
    private lateinit var secondHand: Path

    // Changed when setting changes cause a change in the minute hand arm (triggered by user in
    // updateUserStyle() via userStyleRepository.addUserStyleListener()).
    private var armLengthChangedRecalculateClockHands: Boolean = false

    // Default size of watch face drawing area, that is, a no size rectangle. Will be replaced with
    // valid dimensions from the system.
    private var currentWatchFaceSize = Rect(0, 0, 0, 0)

    init {
        scope.launch {
            currentUserStyleRepository.userStyle.collect { userStyle ->
                updateData(userStyle)
            }
        }
    }

    private fun updateData(userStyle: UserStyle) {
        Log.d(TAG, "updateData(): $userStyle")

        var updatedData: Data = data
        for (entry in userStyle) {
            when (entry.key.id.toString()) {
                DISPLAY_TWENTY_FOUR_HOURS_SETTING -> {
                    val option = entry.value as UserStyleSetting.BooleanUserStyleSetting.BooleanOption

                    updatedData = updatedData.copy(
                        displayTwentyFourHours = option.value
                    )
                }
            }
        }

        // Only updates if something changed.
        if (data != updatedData) {
            data = updatedData

            // Recreates Color and ComplicationDrawable from resource ids.
            watchFaceColors = convertToWatchFaceColorPalette(
                context,
                data.activeColorStyle,
                data.ambientColorStyle
            )
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        scope.cancel("CanvasRenderer scope cancel request")
        super.onDestroy()
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)

        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        val backgroundColor = if (renderParameters.drawMode == DrawMode.AMBIENT) {
            watchFaceColors.ambientBackgroundColor
        } else {
            watchFaceColors.activeBackgroundColor
        }

        canvas.drawColor(backgroundColor)

        // CanvasComplicationDrawable already obeys rendererParameters.
        drawComplications(canvas, zonedDateTime)

        if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.COMPLICATIONS_OVERLAY)) {
            drawClockHands(canvas, bounds, zonedDateTime)
        }

        if (renderParameters.drawMode == DrawMode.INTERACTIVE &&
            renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE) &&
            data.drawHourPips
        ) {
            drawNumberStyleOuterElement(
                canvas,
                bounds,
                data.numberRadiusFraction,
                data.numberStyleOuterCircleRadiusFraction,
                watchFaceColors.activeOuterElementColor,
                data.numberStyleOuterCircleRadiusFraction,
                data.gapBetweenOuterCircleAndBorderFraction
            )
        }
    }

    // ----- All drawing functions -----
    private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.render(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    private fun drawClockHands(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime
    ) {
        // Only recalculate bounds (watch face size/surface) has changed or the arm of one of the
        // clock hands has changed (via user input in the settings).
        // NOTE: Watch face surface usually only updates one time (when the size of the device is
        // initially broadcasted).
        if (currentWatchFaceSize != bounds || armLengthChangedRecalculateClockHands) {
            armLengthChangedRecalculateClockHands = false
            currentWatchFaceSize = bounds
            recalculateClockHands(bounds)
        }

        // Retrieve current time to calculate location/rotation of watch arms.
        val secondOfDay = zonedDateTime.toLocalTime().toSecondOfDay()

        // Determine the rotation of the hour and minute hand.

        // Determine how many seconds it takes to make a complete rotation for each hand
        // It takes the hour hand 12 hours to make a complete rotation
        val secondsPerHourHandRotation = Duration.ofHours(12).seconds
        // It takes the minute hand 1 hour to make a complete rotation
        val secondsPerMinuteHandRotation = Duration.ofHours(1).seconds

        // Determine the angle to draw each hand expressed as an angle in degrees from 0 to 360
        // Since each hand does more than one cycle a day, we are only interested in the remainder
        // of the secondOfDay modulo the hand interval
        val hourRotation = secondOfDay.rem(secondsPerHourHandRotation) * 360.0f /
                secondsPerHourHandRotation
        val minuteRotation = secondOfDay.rem(secondsPerMinuteHandRotation) * 360.0f /
                secondsPerMinuteHandRotation

        canvas.withScale(
            x = WATCH_HAND_SCALE,
            y = WATCH_HAND_SCALE,
            pivotX = bounds.exactCenterX(),
            pivotY = bounds.exactCenterY()
        ) {
            val drawAmbient = renderParameters.drawMode == DrawMode.AMBIENT

            clockHandPaint.style = if (drawAmbient) Paint.Style.STROKE else Paint.Style.FILL
            clockHandPaint.color = if (drawAmbient) {
                watchFaceColors.ambientPrimaryColor
            } else {
                watchFaceColors.activePrimaryColor
            }

            // Draw hour hand.
            withRotation(hourRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
                drawPath(hourHandBorder, clockHandPaint)
            }

            // Draw minute hand.
            withRotation(minuteRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
                drawPath(minuteHandBorder, clockHandPaint)
            }

            // Draw second hand if not in ambient mode
            if (!drawAmbient) {
                clockHandPaint.color = watchFaceColors.activeSecondaryColor

                // Second hand has a different color style (secondary color) and is only drawn in
                // active mode, so we calculate it here (not above with others).
                val secondsPerSecondHandRotation = Duration.ofMinutes(1).seconds
                val secondsRotation = secondOfDay.rem(secondsPerSecondHandRotation) * 360.0f /
                        secondsPerSecondHandRotation
                clockHandPaint.color = watchFaceColors.activeSecondaryColor

                withRotation(secondsRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
                    drawPath(secondHand, clockHandPaint)
                }
            }
        }
    }

    /*
     * Rarely called (only when watch face surface changes; usually only once) from the
     * drawClockHands() method.
     */
    private fun recalculateClockHands(bounds: Rect) {
        Log.d(TAG, "recalculateClockHands()")
        hourHandBorder =
            createClockHand(
                bounds,
                data.hourHandDimensions.lengthFraction,
                data.hourHandDimensions.widthFraction,
                data.gapBetweenHandAndCenterFraction,
                data.hourHandDimensions.xRadiusRoundedCorners,
                data.hourHandDimensions.yRadiusRoundedCorners
            )
        hourHandFill = hourHandBorder

        minuteHandBorder =
            createClockHand(
                bounds,
                data.minuteHandDimensions.lengthFraction,
                data.minuteHandDimensions.widthFraction,
                data.gapBetweenHandAndCenterFraction,
                data.minuteHandDimensions.xRadiusRoundedCorners,
                data.minuteHandDimensions.yRadiusRoundedCorners
            )
        minuteHandFill = minuteHandBorder

        secondHand =
            createClockHand(
                bounds,
                data.secondHandDimensions.lengthFraction,
                data.secondHandDimensions.widthFraction,
                data.gapBetweenHandAndCenterFraction,
                data.secondHandDimensions.xRadiusRoundedCorners,
                data.secondHandDimensions.yRadiusRoundedCorners
            )
    }

    /**
     * Returns a round rect clock hand if {@code rx} and {@code ry} equals to 0, otherwise return a
     * rect clock hand.
     *
     * @param bounds The bounds use to determine the coordinate of the clock hand.
     * @param length Clock hand's length, in fraction of {@code bounds.width()}.
     * @param thickness Clock hand's thickness, in fraction of {@code bounds.width()}.
     * @param gapBetweenHandAndCenter Gap between inner side of arm and center.
     * @param roundedCornerXRadius The x-radius of the rounded corners on the round-rectangle.
     * @param roundedCornerYRadius The y-radius of the rounded corners on the round-rectangle.
     */
    private fun createClockHand(
        bounds: Rect,
        length: Float,
        thickness: Float,
        gapBetweenHandAndCenter: Float,
        roundedCornerXRadius: Float,
        roundedCornerYRadius: Float
    ): Path {
        val width = bounds.width()
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val left = centerX - thickness / 2 * width
        val top = centerY - (gapBetweenHandAndCenter + length) * width
        val right = centerX + thickness / 2 * width
        val bottom = centerY - gapBetweenHandAndCenter * width
        val path = Path()

        if (roundedCornerXRadius != 0.0f || roundedCornerYRadius != 0.0f) {
            path.addRoundRect(
                left,
                top,
                right,
                bottom,
                roundedCornerXRadius,
                roundedCornerYRadius,
                Path.Direction.CW
            )
        } else {
            path.addRect(
                left,
                top,
                right,
                bottom,
                Path.Direction.CW
            )
        }
        return path
    }

    private fun drawNumberStyleOuterElement(
        canvas: Canvas,
        bounds: Rect,
        numberRadiusFraction: Float,
        outerCircleStokeWidthFraction: Float,
        outerElementColor: Int,
        numberStyleOuterCircleRadiusFraction: Float,
        gapBetweenOuterCircleAndBorderFraction: Float
    ) {
        // Draws text hour indicators (12, 3, 6, and 9).
        val textBounds = Rect()
        textPaint.color = outerElementColor
        for (i in 0 until 4) {
            val rotation = 0.5f * (i + 1).toFloat() * Math.PI
            val dx = sin(rotation).toFloat() * numberRadiusFraction * bounds.width().toFloat()
            val dy = -cos(rotation).toFloat() * numberRadiusFraction * bounds.width().toFloat()
            textPaint.getTextBounds(HOUR_MARKS[i], 0, HOUR_MARKS[i].length, textBounds)
            canvas.drawText(
                HOUR_MARKS[i],
                bounds.exactCenterX() + dx - textBounds.width() / 2.0f,
                bounds.exactCenterY() + dy + textBounds.height() / 2.0f,
                textPaint
            )
        }

        // Draws dots for the remain hour indicators between the numbers above.
        outerElementPaint.strokeWidth = outerCircleStokeWidthFraction * bounds.width()
        outerElementPaint.color = outerElementColor
        canvas.save()
        for (i in 0 until 12) {
            if (i % 3 != 0) {
                drawTopMiddleCircle(
                    canvas,
                    bounds,
                    numberStyleOuterCircleRadiusFraction,
                    gapBetweenOuterCircleAndBorderFraction
                )
            }
            canvas.rotate(360.0f / 12.0f, bounds.exactCenterX(), bounds.exactCenterY())
        }
        canvas.restore()
    }

    /** Draws the outer circle on the top middle of the given bounds. */
    private fun drawTopMiddleCircle(
        canvas: Canvas,
        bounds: Rect,
        radiusFraction: Float,
        gapBetweenOuterCircleAndBorderFraction: Float
    ) {
        outerElementPaint.style = Paint.Style.FILL_AND_STROKE

        // X and Y coordinates of the center of the circle.
        val centerX = 0.5f * bounds.width().toFloat()
        val centerY = bounds.width() * (gapBetweenOuterCircleAndBorderFraction + radiusFraction)

        canvas.drawCircle(
            centerX,
            centerY,
            radiusFraction * bounds.width(),
            outerElementPaint
        )
    }

    companion object {
        private const val TAG = "CanvasRenderer"

        // Painted between pips on watch face for hour marks.
        private val HOUR_MARKS = arrayOf("3", "6", "9", "12")

        // Used to canvas.scale() to scale watch hands in proper bounds. This will always be 1.0.
        private const val WATCH_HAND_SCALE = 1.0f
    }
}