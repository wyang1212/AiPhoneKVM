package com.atharok.btremote.domain.entities.settings

import com.atharok.btremote.common.utils.DEFAULT_THEME
import com.atharok.btremote.common.utils.DEFAULT_USE_BLACK_COLOR_FOR_DARK_THEME
import com.atharok.btremote.common.utils.DEFAULT_USE_FULL_SCREEN
import com.atharok.btremote.common.utils.isDynamicColorsAvailable

data class AppearanceSettings(
    val theme: ThemeEntity = DEFAULT_THEME,
    val useDynamicColors: Boolean = isDynamicColorsAvailable(),
    val useBlackColorForDarkTheme: Boolean = DEFAULT_USE_BLACK_COLOR_FOR_DARK_THEME,
    val useFullScreen: Boolean = DEFAULT_USE_FULL_SCREEN
)