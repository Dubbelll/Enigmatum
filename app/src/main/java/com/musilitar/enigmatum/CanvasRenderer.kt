package com.musilitar.enigmatum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.DimenRes
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.WatchFaceLayer
import com.musilitar.enigmatum.ColorPalette.Companion.buildColorPalette
import kotlinx.coroutines.*
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin

class CanvasRenderer(
    context: Context,
    surfaceHolder: SurfaceHolder,
    currentUserStyleRepository: CurrentUserStyleRepository,
    watchState: WatchState,
    canvasType: Int
) : Renderer.CanvasRenderer2<CanvasRenderer.SharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    16L,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {
    private val context = context
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var data: Data = Data()
    private var colorPalette = buildColorPalette(
        context,
        data.interactiveStyle,
        data.ambientStyle,
    )

    private lateinit var hourHandFill: Path
    private lateinit var minuteHandFill: Path
    private lateinit var secondHandFill: Path

    // Default size of watch face drawing area, that is, a no size rectangle.
    // Will be replaced with valid dimensions from the system.
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
                    val option =
                        entry.value as UserStyleSetting.BooleanUserStyleSetting.BooleanOption

                    updatedData = updatedData.copy(
                        displayTwentyFourHours = option.value
                    )
                }
            }
        }

        if (data != updatedData) {
            data = updatedData
            colorPalette = buildColorPalette(
                context,
                data.interactiveStyle,
                data.ambientStyle
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
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        val backgroundColor = colorPalette.backgroundColor(renderParameters.drawMode)
        canvas.drawColor(backgroundColor)

        if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE)) {
            drawHourMarks(
                canvas,
                bounds,
                data.dayHourMarks,
                R.dimen.hour_mark_size
            )
            // drawClockHands(canvas, bounds, zonedDateTime)
        }
    }

    private fun drawHourMarks(
        canvas: Canvas,
        bounds: Rect,
        marks: List<String>,
        @DimenRes textDimension: Int,
    ) {
        val textBounds = Rect()
        val textPaint = Paint().apply {
            isAntiAlias = true
            textSize = context.resources.getDimensionPixelSize(textDimension).toFloat()
            color = colorPalette.textColor(renderParameters.drawMode)
        }

        for (i in marks.indices) {
            val mark = marks[i]
            val rotation = 0.5f * (i + 1).toFloat() * Math.PI
            val dx = sin(rotation).toFloat() * 0.45f * bounds.width().toFloat()
            val dy = -cos(rotation).toFloat() * 0.45f * bounds.height().toFloat()
            textPaint.getTextBounds(mark, 0, mark.length, textBounds)
            canvas.drawText(
                mark,
                bounds.exactCenterX() + dx - textBounds.width() / 2.0f,
                bounds.exactCenterY() + dy + textBounds.height() / 2.0f,
                textPaint
            )
        }
    }

//    private fun drawClockHands(
//        canvas: Canvas,
//        bounds: Rect,
//        zonedDateTime: ZonedDateTime
//    ) {
//        // Only recalculate bounds (watch face size/surface) has changed or the arm of one of the
//        // clock hands has changed (via user input in the settings).
//        // NOTE: Watch face surface usually only updates one time (when the size of the device is
//        // initially broadcasted).
//        if (currentWatchFaceSize != bounds) {
//            currentWatchFaceSize = bounds
//            recalculateClockHands(bounds)
//        }
//
//        // Retrieve current time to calculate location/rotation of watch arms.
//        val secondOfDay = zonedDateTime.toLocalTime().toSecondOfDay()
//
//        // Determine the rotation of the hour and minute hand.
//
//        // Determine how many seconds it takes to make a complete rotation for each hand
//        // It takes the hour hand 12 hours to make a complete rotation
//        val secondsPerHourHandRotation = Duration.ofHours(12).seconds
//        // It takes the minute hand 1 hour to make a complete rotation
//        val secondsPerMinuteHandRotation = Duration.ofHours(1).seconds
//
//        // Determine the angle to draw each hand expressed as an angle in degrees from 0 to 360
//        // Since each hand does more than one cycle a day, we are only interested in the remainder
//        // of the secondOfDay modulo the hand interval
//        val hourRotation = secondOfDay.rem(secondsPerHourHandRotation) * 360.0f /
//                secondsPerHourHandRotation
//        val minuteRotation = secondOfDay.rem(secondsPerMinuteHandRotation) * 360.0f /
//                secondsPerMinuteHandRotation
//
//        canvas.withScale(
//            x = WATCH_HAND_SCALE,
//            y = WATCH_HAND_SCALE,
//            pivotX = bounds.exactCenterX(),
//            pivotY = bounds.exactCenterY()
//        ) {
//            val drawAmbient = renderParameters.drawMode == DrawMode.AMBIENT
//
//            clockHandPaint.style = if (drawAmbient) Paint.Style.STROKE else Paint.Style.FILL
//            clockHandPaint.color = if (drawAmbient) {
//                colorPalette.ambientPrimaryColor
//            } else {
//                colorPalette.activePrimaryColor
//            }
//
//            // Draw hour hand.
//            withRotation(hourRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
//                drawPath(hourHandBorder, clockHandPaint)
//            }
//
//            // Draw minute hand.
//            withRotation(minuteRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
//                drawPath(minuteHandBorder, clockHandPaint)
//            }
//
//            // Draw second hand if not in ambient mode
//            if (!drawAmbient) {
//                clockHandPaint.color = colorPalette.activeSecondaryColor
//
//                // Second hand has a different color style (secondary color) and is only drawn in
//                // active mode, so we calculate it here (not above with others).
//                val secondsPerSecondHandRotation = Duration.ofMinutes(1).seconds
//                val secondsRotation = secondOfDay.rem(secondsPerSecondHandRotation) * 360.0f /
//                        secondsPerSecondHandRotation
//                clockHandPaint.color = colorPalette.activeSecondaryColor
//
//                withRotation(secondsRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
//                    drawPath(secondHand, clockHandPaint)
//                }
//            }
//        }
//    }
//
//    /*
//     * Rarely called (only when watch face surface changes; usually only once) from the
//     * drawClockHands() method.
//     */
//    private fun recalculateClockHands(bounds: Rect) {
//        Log.d(TAG, "recalculateClockHands()")
//        hourHandBorder =
//            createClockHand(
//                bounds,
//                data.hourHandDimensions.lengthFraction,
//                data.hourHandDimensions.widthFraction,
//                data.gapBetweenHandAndCenterFraction,
//                data.hourHandDimensions.xRadiusRoundedCorners,
//                data.hourHandDimensions.yRadiusRoundedCorners
//            )
//        hourHandFill = hourHandBorder
//
//        minuteHandBorder =
//            createClockHand(
//                bounds,
//                data.minuteHandDimensions.lengthFraction,
//                data.minuteHandDimensions.widthFraction,
//                data.gapBetweenHandAndCenterFraction,
//                data.minuteHandDimensions.xRadiusRoundedCorners,
//                data.minuteHandDimensions.yRadiusRoundedCorners
//            )
//        minuteHandFill = minuteHandBorder
//
//        secondHand =
//            createClockHand(
//                bounds,
//                data.secondHandDimensions.lengthFraction,
//                data.secondHandDimensions.widthFraction,
//                data.gapBetweenHandAndCenterFraction,
//                data.secondHandDimensions.xRadiusRoundedCorners,
//                data.secondHandDimensions.yRadiusRoundedCorners
//            )
//    }

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

    override suspend fun createSharedAssets(): SharedAssets {
        return SharedAssets()
    }

    class SharedAssets : Renderer.SharedAssets {
        override fun onDestroy() {
        }
    }

    companion object {
        private const val TAG = "CanvasRenderer"
    }
}