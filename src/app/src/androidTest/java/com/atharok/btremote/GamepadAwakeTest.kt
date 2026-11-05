// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.atharok.btremote.ui.views.GamepadControlPanel
import org.junit.*
import org.junit.Assert.*
class GamepadAwakeTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun pageKeepsScreenOnAndRestoresPreviousViewFlagOnExit() {
        var shown by mutableStateOf(true)
        lateinit var view:android.view.View
        var before=false
        compose.setContent { MaterialTheme {
            view=LocalView.current
            if(shown)GamepadControlPanel({true})
        } }
        compose.runOnIdle { assertTrue(view.keepScreenOn);shown=false }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(view.keepScreenOn) }
    }
}
