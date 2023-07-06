package com.musilitar.enigmatum

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ListUserStyleSetting
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val DISPLAY_TWENTY_FOUR_HOURS_DEFAULT = true

data class Data(
    val interactiveStyle: StyleResource = StyleResource.DEFAULT,
    val ambientStyle: StyleResource = StyleResource.AMBIENT,
    val dayHourLabels: List<String> = List(12) {
        (if (it == 0) 12 else it).toString().padStart(2, '0')
    },
    val nightHourLabels: List<String> = List(12) {
        (if (it == 0) it else it + 12).toString().padStart(2, '0')
    },
    val minuteSecondLabels: List<String> = List(60) { (it + 1).toString().padStart(2, '0') },
    var dayHourMarks: List<Mark> = emptyList(),
    var nightHourMarks: List<Mark> = emptyList(),
    val minuteSecondMarks: List<Mark> = emptyList(),
    val displayTwentyFourHours: Boolean = DISPLAY_TWENTY_FOUR_HOURS_DEFAULT,
) {
    fun buildOrUseDayHourMarks(
        bounds: Rect,
        textPaint: Paint,
    ): List<Mark> {
        if (dayHourMarks.isEmpty()) {
            dayHourMarks = buildHourMarks(bounds, textPaint, nightHourLabels)
        }
        return dayHourMarks
    }

    fun buildOrUseNightHourMarks(
        bounds: Rect,
        textPaint: Paint,
    ): List<Mark> {
        if (nightHourMarks.isEmpty()) {
            nightHourMarks = buildHourMarks(bounds, textPaint, nightHourLabels)
        }
        return nightHourMarks
    }

    companion object {
        fun buildHourMarks(
            bounds: Rect,
            textPaint: Paint,
            labels: List<String>,
        ): List<Mark> {
            val textBounds = Rect()
            val padding = 20f
            val diameter = min(bounds.width(), bounds.height()) - (2 * padding)
            val radius = diameter / 2.0f
            val centerX = bounds.exactCenterX()
            val centerY = bounds.exactCenterY()
            val slice = 2 * Math.PI / labels.size

            return List(labels.size) {
                val label = labels[it]
                val angle = slice * it
                val x = centerX + (radius * cos(angle))
                val y = centerY + (radius * sin(angle))

                textPaint.getTextBounds(label, 0, label.length, textBounds)

                val textX = x.toFloat() - (textBounds.width() / 2.0f)
                val textY = y.toFloat() + (textBounds.height() / 2.0f)

                Mark(label, textX, textY)
            }
        }
    }
}

data class Mark(
    val label: String,
    val x: Float,
    val y: Float
)

data class ColorPalette(
    val interactiveHourColor: Int,
    val interactiveMinuteColor: Int,
    val interactiveSecondColor: Int,
    val interactiveBackgroundColor: Int,
    val interactiveTextColor: Int,
    val ambientHourColor: Int,
    val ambientMinuteColor: Int,
    val ambientSecondColor: Int,
    val ambientBackgroundColor: Int,
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

    fun backgroundColor(drawMode: DrawMode): Int {
        return if (drawMode == DrawMode.AMBIENT) ambientBackgroundColor
        else interactiveBackgroundColor
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
                interactiveBackgroundColor = context.getColor(interactiveStyle.backgroundColorId),
                interactiveTextColor = context.getColor(interactiveStyle.textColorId),
                ambientHourColor = context.getColor(ambientStyle.hourColorId),
                ambientMinuteColor = context.getColor(ambientStyle.minuteColorId),
                ambientSecondColor = context.getColor(ambientStyle.secondColorId),
                ambientBackgroundColor = context.getColor(ambientStyle.backgroundColorId),
                ambientTextColor = context.getColor(ambientStyle.textColorId),
            )
        }
    }
}

const val DEFAULT_STYLE_ID = "default_style_id"
private const val DEFAULT_STYLE_NAME_RESOURCE_ID = R.string.default_style_name

const val AMBIENT_STYLE_ID = "ambient_style_id"
private const val AMBIENT_STYLE_NAME_RESOURCE_ID = R.string.ambient_style_name

enum class StyleResource(
    val id: String,
    @StringRes val nameId: Int,
    @ColorRes val hourColorId: Int,
    @ColorRes val minuteColorId: Int,
    @ColorRes val secondColorId: Int,
    @ColorRes val backgroundColorId: Int,
    @ColorRes val textColorId: Int,
) {
    DEFAULT(
        id = DEFAULT_STYLE_ID,
        nameId = DEFAULT_STYLE_NAME_RESOURCE_ID,
        hourColorId = R.color.default_hour,
        minuteColorId = R.color.default_minute,
        secondColorId = R.color.default_second,
        backgroundColorId = R.color.default_background,
        textColorId = R.color.default_text,
    ),
    AMBIENT(
        id = AMBIENT_STYLE_ID,
        nameId = AMBIENT_STYLE_NAME_RESOURCE_ID,
        hourColorId = R.color.ambient_hour,
        minuteColorId = R.color.ambient_minute,
        secondColorId = R.color.ambient_second,
        backgroundColorId = R.color.ambient_background,
        textColorId = R.color.ambient_text,
    );

    companion object {
        fun findStyleResourceById(id: String): StyleResource {
            return when (id) {
                DEFAULT.id -> DEFAULT
                AMBIENT.id -> AMBIENT
                else -> DEFAULT
            }
        }

        fun buildUserStyleOptions(context: Context): List<ListUserStyleSetting.ListOption> {
            val styleResources = enumValues<StyleResource>()

            return styleResources.map { styleResource ->
                ListUserStyleSetting.ListOption(
                    UserStyleSetting.Option.Id(styleResource.id),
                    context.resources,
                    styleResource.nameId,
                    null,
                )
            }
        }
    }
}