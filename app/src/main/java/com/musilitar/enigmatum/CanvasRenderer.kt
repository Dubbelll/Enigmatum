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
import com.musilitar.enigmatum.ColorPalette.Companion.buildColorPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.util.Collections
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class CanvasRenderer(
    private val context: Context,
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
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var data: Data = Data()
    private var colorPalette = buildColorPalette(
        context,
        data.interactiveStyle,
        data.ambientStyle,
    )
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = colorPalette.textColor(renderParameters.drawMode)
        typeface = context.resources.getFont(R.font.fira_mono_regular)
    }
    private val markPaint = Paint().apply {
        isAntiAlias = true
        color = colorPalette.backgroundColor(renderParameters.drawMode)
    }
    private val clockHandPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

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

        // Shift marks by a quarter because the drawing starts at the 3 o'clock position
        Collections.rotate(data.dayHourIntervals, -3)
        Collections.rotate(data.nightHourIntervals, -3)
        Collections.rotate(data.minuteSecondIntervals, -15)
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

        if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.COMPLICATIONS_OVERLAY)) {
            drawClockHands(canvas, bounds, zonedDateTime)
        }

        if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE)
        ) {
            drawHourMarks(
                canvas,
                bounds,
                zonedDateTime,
            )
            drawMinuteMarks(
                canvas,
                bounds,
                zonedDateTime,
            )

            if (renderParameters.drawMode == DrawMode.INTERACTIVE) {
                drawSecondMarks(
                    canvas,
                    bounds,
                    zonedDateTime,
                )
            }
        }
    }

    private fun drawHourMarks(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
    ) {
        textPaint.textSize =
            context.resources.getDimensionPixelSize(R.dimen.hour_mark_size).toFloat()
        textPaint.color = colorPalette.textColor(renderParameters.drawMode)

        val marks: List<Mark> = if (zonedDateTime.hour > 12) {
            data.buildOrUseNightHourMarks(bounds, textPaint)
        } else {
            data.buildOrUseDayHourMarks(bounds, textPaint)
        }
        for (mark in marks) {
            if (mark.interval == zonedDateTime.hour) {
                canvas.drawCircle(
                    mark.x + mark.bounds.exactCenterX(),
                    mark.y + mark.bounds.exactCenterY(),
                    max(mark.bounds.width(), mark.bounds.height()) / 2.0f + data.markPadding,
                    markPaint
                )
                canvas.drawText(
                    mark.label,
                    mark.x,
                    mark.y,
                    textPaint
                )
            }
        }
    }

    private fun drawMinuteMarks(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
    ) {
        textPaint.textSize =
            context.resources.getDimensionPixelSize(R.dimen.minute_mark_size).toFloat()
        textPaint.color = colorPalette.textColor(renderParameters.drawMode)

        val marks: List<Mark> = data.buildOrUseMinuteMarks(bounds, textPaint)
        for (mark in marks) {
            if (mark.interval == zonedDateTime.minute) {
                canvas.drawCircle(
                    mark.x + mark.bounds.exactCenterX(),
                    mark.y + mark.bounds.exactCenterY(),
                    max(mark.bounds.width(), mark.bounds.height()) / 2.0f + data.markPadding,
                    markPaint
                )
                canvas.drawText(
                    mark.label,
                    mark.x,
                    mark.y,
                    textPaint
                )
            }
        }
    }

    private fun drawSecondMarks(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
    ) {
        textPaint.textSize =
            context.resources.getDimensionPixelSize(R.dimen.second_mark_size).toFloat()
        textPaint.color = colorPalette.textColor(renderParameters.drawMode)

        val marks: List<Mark> = data.buildOrUseSecondMarks(bounds, textPaint)
        for (mark in marks) {
            if (mark.interval == zonedDateTime.second) {
                canvas.drawCircle(
                    mark.x + mark.bounds.exactCenterX(),
                    mark.y + mark.bounds.exactCenterY(),
                    max(mark.bounds.width(), mark.bounds.height()) / 2.0f + data.markPadding,
                    markPaint
                )
                canvas.drawText(
                    mark.label,
                    mark.x,
                    mark.y,
                    textPaint
                )
            }
        }
    }

    private fun drawClockHands(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime
    ) {
        if (currentWatchFaceSize != bounds) {
            currentWatchFaceSize = bounds
            recalculateClockHands(bounds)
        }

        val hourRotation = zonedDateTime.hour * 30.0f
        val minuteRotation = zonedDateTime.minute * 6.0f

        canvas.withScale(
            x = WATCH_HAND_SCALE,
            y = WATCH_HAND_SCALE,
            pivotX = bounds.exactCenterX(),
            pivotY = bounds.exactCenterY()
        ) {
            clockHandPaint.color = colorPalette.hourColor(renderParameters.drawMode)
            withRotation(hourRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
                drawPath(hourHandFill, clockHandPaint)
            }

            clockHandPaint.color = colorPalette.minuteColor(renderParameters.drawMode)
            withRotation(minuteRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
                drawPath(minuteHandFill, clockHandPaint)
            }

            if (renderParameters.drawMode != DrawMode.AMBIENT) {
                val secondsRotation = zonedDateTime.second * 6.0f
                clockHandPaint.color = colorPalette.secondColor(renderParameters.drawMode)
                withRotation(secondsRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
                    drawPath(secondHandFill, clockHandPaint)
                }
            }
        }
    }

    private fun recalculateClockHands(bounds: Rect) {
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val radius = min(bounds.width(), bounds.height()) / 2.0f

        hourHandFill =
            traceTriangle(
                centerX,
                centerY,
                radius,
            )
        minuteHandFill =
            traceTriangle(
                centerX,
                centerY,
                radius / 2,
            )
        secondHandFill =
            traceTriangle(
                centerX,
                centerY,
                radius / 4,
            )
    }

    private fun traceTriangle(centerX: Float, centerY: Float, radius: Float): Path {
        val x = (cos(Math.PI / 6) * radius).toFloat()
        val y = (sin(Math.PI / 6) * radius).toFloat()
        val path = Path()

        path.moveTo(centerX, centerY - radius)
        path.lineTo(centerX + x, centerY + y)
        path.lineTo(centerX - x, centerY + y)
        path.lineTo(centerX, centerY - radius)
        path.close()

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
        private const val WATCH_HAND_SCALE = 1.0f
    }
}