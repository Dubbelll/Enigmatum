package com.musilitar.enigmatum

import android.content.Context
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.WatchFaceLayer

const val DISPLAY_TWENTY_FOUR_HOURS_SETTING = "display_twenty_four_hours_setting"

fun createUserStyleSchema(context: Context): UserStyleSchema {
    val hourModeSetting = UserStyleSetting.BooleanUserStyleSetting(
        UserStyleSetting.Id(DISPLAY_TWENTY_FOUR_HOURS_SETTING),
        context.resources,
        R.string.hour_mode_setting,
        R.string.hour_mode_setting_description,
        null,
        listOf(WatchFaceLayer.BASE),
        DISPLAY_TWENTY_FOUR_HOURS_DEFAULT
    )

    return UserStyleSchema(
        listOf(
            hourModeSetting
        )
    )
}