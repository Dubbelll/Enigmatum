package com.musilitar.enigmatum

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import androidx.annotation.ColorRes
import androidx.wear.watchface.DrawMode
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val DISPLAY_TWENTY_FOUR_HOURS_DEFAULT = true

data class Data(
    val interactiveStyle: StyleResource = StyleResource.DEFAULT,
    val ambientStyle: StyleResource = StyleResource.AMBIENT,
    val dayHourIntervals: List<Int> = List(12) { if (it == 0) 12 else it },
    val nightHourIntervals: List<Int> = List(12) { if (it == 0) 0 else it + 12 },
    val minuteSecondIntervals: List<Int> = List(60) { it },
    var dayHourMarks: List<Mark> = emptyList(),
    var nightHourMarks: List<Mark> = emptyList(),
    var minuteMarks: List<Mark> = emptyList(),
    var secondMarks: List<Mark> = emptyList(),
    val clockPadding: Float = 20f,
    val markPadding: Int = 10,
    val borderThickness: Float = 5f,
    val displayTwentyFourHours: Boolean = DISPLAY_TWENTY_FOUR_HOURS_DEFAULT,
) {
    fun buildOrUseDayHourMarks(
        bounds: Rect,
        textPaint: Paint,
    ): List<Mark> {
        if (dayHourMarks.isEmpty()) {
            dayHourMarks = buildMarks(
                bounds,
                textPaint,
                dayHourIntervals,
                clockPadding,
                1f - calculatePaddingPercentageOfDiameter(bounds)
            )
        }
        return dayHourMarks
    }

    fun buildOrUseNightHourMarks(
        bounds: Rect,
        textPaint: Paint,
    ): List<Mark> {
        if (nightHourMarks.isEmpty()) {
            nightHourMarks =
                buildMarks(
                    bounds,
                    textPaint,
                    nightHourIntervals,
                    clockPadding,
                    1f - calculatePaddingPercentageOfDiameter(bounds)
                )
        }
        return nightHourMarks
    }

    fun buildOrUseMinuteMarks(
        bounds: Rect,
        textPaint: Paint,
    ): List<Mark> {
        if (minuteMarks.isEmpty()) {
            minuteMarks = buildMarks(bounds, textPaint, minuteSecondIntervals, clockPadding, 0.5f)
        }
        return minuteMarks
    }

    fun buildOrUseSecondMarks(
        bounds: Rect,
        textPaint: Paint,
    ): List<Mark> {
        if (secondMarks.isEmpty()) {
            secondMarks = buildMarks(bounds, textPaint, minuteSecondIntervals, clockPadding, 0.25f)
        }
        return secondMarks
    }

    fun calculatePaddingPercentageOfDiameter(
        bounds: Rect,
    ): Float {
        val diameter = min(bounds.width(), bounds.height())
        return (markPadding + borderThickness) / diameter
    }

    companion object {
        fun buildMarks(
            bounds: Rect,
            textPaint: Paint,
            intervals: List<Int>,
            clockPadding: Float,
            distanceFromCenterMultiplier: Float
        ): List<Mark> {
            val textBounds = Rect()
            val diameter = min(bounds.width(), bounds.height()) - (2 * clockPadding)
            val radius = (diameter / 2.0f) * distanceFromCenterMultiplier
            val centerX = bounds.exactCenterX()
            val centerY = bounds.exactCenterY()
            val slice = 2 * Math.PI / intervals.size

            return List(intervals.size) { index ->
                val interval = intervals[index]
                val label = interval.toString().padStart(2, '0')
                val angle = slice * index
                val x = centerX + (radius * cos(angle))
                val y = centerY + (radius * sin(angle))

                textPaint.getTextBounds(label, 0, label.length, textBounds)

                val textX = x.toFloat() - (textBounds.width() / 2.0f)
                val textY = y.toFloat() + (textBounds.height() / 2.0f)

                Mark(interval, label, textX, textY, textBounds)
            }
        }
    }
}

data class Mark(
    val interval: Int,
    val label: String,
    val x: Float,
    val y: Float,
    val bounds: Rect
)

data class ColorPalette(
    val interactiveHourColor: Int,
    val interactiveMinuteColor: Int,
    val interactiveSecondColor: Int,
    val interactiveMarkColor: Int,
    val interactiveBorderColor: Int,
    val interactiveTextColor: Int,
    val ambientHourColor: Int,
    val ambientMinuteColor: Int,
    val ambientSecondColor: Int,
    val ambientMarkColor: Int,
    val ambientBorderColor: Int,
    val ambientTextColor: Int,
) {
    fun hourColor(drawMode: DrawMode): Int {
        return if (drawMode == DrawMode.AMBIENT) ambientHourColor
        else interactiveHourColor
    }

    fun minuteColor(drawMode: DrawMode): Int {
        return if (drawMode == DrawMode.AMBIENT) ambientMinuteColor
        else interactiveMinuteColor
    }

    fun secondColor(drawMode: DrawMode): Int {
        return if (drawMode == DrawMode.AMBIENT) ambientSecondColor
        else interactiveSecondColor
    }

    fun markColor(drawMode: DrawMode): Int {
        return if (drawMode == DrawMode.AMBIENT) ambientMarkColor
        else interactiveMarkColor
    }

    fun borderColor(drawMode: DrawMode): Int {
        return if (drawMode == DrawMode.AMBIENT) ambientBorderColor
        else interactiveBorderColor
    }

    fun textColor(drawMode: DrawMode): Int {
        return if (drawMode == DrawMode.AMBIENT) ambientTextColor
        else interactiveTextColor
    }

    companion object {
        fun buildColorPalette(
            context: Context, interactiveStyle: StyleResource, ambientStyle: StyleResource
        ): ColorPalette {
            return ColorPalette(
                interactiveHourColor = context.getColor(interactiveStyle.hourColorId),
                interactiveMinuteColor = context.getColor(interactiveStyle.minuteColorId),
                interactiveSecondColor = context.getColor(interactiveStyle.secondColorId),
                interactiveMarkColor = context.getColor(interactiveStyle.markColorId),
                interactiveBorderColor = context.getColor(interactiveStyle.borderColorId),
                interactiveTextColor = context.getColor(interactiveStyle.textColorId),
                ambientHourColor = context.getColor(ambientStyle.hourColorId),
                ambientMinuteColor = context.getColor(ambientStyle.minuteColorId),
                ambientSecondColor = context.getColor(ambientStyle.secondColorId),
                ambientMarkColor = context.getColor(ambientStyle.markColorId),
                ambientBorderColor = context.getColor(ambientStyle.borderColorId),
                ambientTextColor = context.getColor(ambientStyle.textColorId),
            )
        }
    }
}

enum class StyleResource(
    @ColorRes val hourColorId: Int,
    @ColorRes val minuteColorId: Int,
    @ColorRes val secondColorId: Int,
    @ColorRes val markColorId: Int,
    @ColorRes val borderColorId: Int,
    @ColorRes val textColorId: Int,
) {
    DEFAULT(
        hourColorId = R.color.default_hour,
        minuteColorId = R.color.default_minute,
        secondColorId = R.color.default_second,
        markColorId = R.color.default_mark,
        borderColorId = R.color.default_border,
        textColorId = R.color.default_text,
    ),
    AMBIENT(
        hourColorId = R.color.ambient_hour,
        minuteColorId = R.color.ambient_minute,
        secondColorId = R.color.ambient_second,
        markColorId = R.color.ambient_mark,
        borderColorId = R.color.ambient_border,
        textColorId = R.color.ambient_text,
    );
}