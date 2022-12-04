package com.musilitar.enigmatum

import android.content.Context
import android.graphics.drawable.Icon
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ListUserStyleSetting

const val DISPLAY_TWENTY_FOUR_HOURS_DEFAULT = true

data class Data(
    val displayTwentyFourHours: Boolean = DISPLAY_TWENTY_FOUR_HOURS_DEFAULT,
)

data class ColorPalette(
    val interactiveHourColor: Int,
    val interactiveMinuteColor: Int,
    val interactiveSecondColor: Int,
    val ambientHourColor: Int,
    val ambientMinuteColor: Int,
    val ambientSecondColor: Int,
) {
    companion object {
        fun buildColorPalette(
            context: Context,
            interactiveStyle: StyleResource,
            ambientStyle: StyleResource
        ): ColorPalette {
            return ColorPalette(
                // Active colors
                interactiveHourColor = context.getColor(interactiveStyle.hourColorId),
                interactiveMinuteColor = context.getColor(interactiveStyle.minuteColorId),
                interactiveSecondColor = context.getColor(interactiveStyle.secondColorId),
                ambientHourColor = context.getColor(ambientStyle.hourColorId),
                ambientMinuteColor = context.getColor(ambientStyle.minuteColorId),
                ambientSecondColor = context.getColor(ambientStyle.secondColorId),
            )
        }
    }
}

const val DEFAULT_STYLE_ID = "default_style_id"
private const val DEFAULT_STYLE_NAME_RESOURCE_ID = R.string.default_style_name
private const val DEFAULT_STYLE_ICON_ID = R.drawable.default_style

const val AMBIENT_STYLE_ID = "ambient_style_id"
private const val AMBIENT_STYLE_NAME_RESOURCE_ID = R.string.ambient_style_name
private const val AMBIENT_STYLE_ICON_ID = R.drawable.ambient_style

enum class StyleResource(
    val id: String,
    @StringRes val nameId: Int,
    @DrawableRes val iconId: Int,
    @ColorRes val hourColorId: Int,
    @ColorRes val minuteColorId: Int,
    @ColorRes val secondColorId: Int,
) {
    DEFAULT(
        id = DEFAULT_STYLE_ID,
        nameId = DEFAULT_STYLE_NAME_RESOURCE_ID,
        iconId = DEFAULT_STYLE_ICON_ID,
        hourColorId = R.color.default_hour,
        minuteColorId = R.color.default_minute,
        secondColorId = R.color.default_second,
    ),
    AMBIENT(
        id = AMBIENT_STYLE_ID,
        nameId = AMBIENT_STYLE_NAME_RESOURCE_ID,
        iconId = AMBIENT_STYLE_ICON_ID,
        hourColorId = R.color.ambient_hour,
        minuteColorId = R.color.ambient_minute,
        secondColorId = R.color.ambient_second,
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
            val colorStyleIdAndResourceIdsList = enumValues<StyleResource>()

            return colorStyleIdAndResourceIdsList.map { styleResource ->
                ListUserStyleSetting.ListOption(
                    UserStyleSetting.Option.Id(styleResource.id),
                    context.resources,
                    styleResource.nameId,
                    Icon.createWithResource(
                        context,
                        styleResource.iconId
                    )
                )
            }
        }
    }
}