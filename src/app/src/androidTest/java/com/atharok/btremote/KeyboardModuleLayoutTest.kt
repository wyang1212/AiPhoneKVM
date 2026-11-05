// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.atharok.btremote.domain.entities.settings.RemoteSettings
import com.atharok.btremote.ui.views.EnglishKeyboardPanel
import org.junit.*

class KeyboardModuleLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun portraitKeyboardMouseAndLandscapeKeyboardRemainDistinctAcrossReentry() {
        val orientation=mutableStateOf(Configuration.ORIENTATION_PORTRAIT)
        compose.setContent { MaterialTheme {
            val config=Configuration(LocalConfiguration.current).apply { this.orientation=orientation.value }
            CompositionLocalProvider(LocalConfiguration provides config) {
                EnglishKeyboardPanel({},RemoteSettings(),{_,_,_,_->},Modifier.fillMaxSize())
            }
        } }
        compose.onNodeWithContentDescription("键鼠操作区").assertIsDisplayed()
        compose.onNodeWithText("上方触控板").assertIsDisplayed()
        compose.runOnIdle { orientation.value=Configuration.ORIENTATION_LANDSCAPE }
        compose.onNodeWithContentDescription("横屏键盘操作区").assertIsDisplayed()
        compose.onNodeWithContentDescription("键鼠操作区").assertDoesNotExist()
        compose.onNodeWithText("Esc").assertExists()
        compose.onNodeWithText("Ctrl").assertExists()
        compose.runOnIdle { orientation.value=Configuration.ORIENTATION_PORTRAIT }
        compose.onNodeWithContentDescription("键鼠操作区").assertIsDisplayed()
        compose.onNodeWithText("Esc").assertDoesNotExist()
    }
}
