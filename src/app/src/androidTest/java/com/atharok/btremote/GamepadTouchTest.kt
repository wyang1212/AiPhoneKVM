// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atharok.btremote.common.utils.GamepadButton
import com.atharok.btremote.common.utils.GamepadReport
import com.atharok.btremote.common.utils.GamepadState
import com.atharok.btremote.domain.entities.remoteInput.GamepadLayout
import com.atharok.btremote.domain.repositories.BluetoothRepository
import com.atharok.btremote.ui.views.GamepadControlPanel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.util.Collections
import kotlin.math.min

/** Exercises real Compose MotionEvents. Optional peer address also sends each frame over Bluetooth. */
@RunWith(AndroidJUnit4::class)
class GamepadTouchTest {
    @get:Rule val compose = createComposeRule()
    private val reports = Collections.synchronizedList(mutableListOf<ByteArray>())
    private val visible = mutableStateOf(true)
    private var peer: BluetoothRepository? = null
    private var rejectNextReport = false

    @Before fun prepare() {
        compose.setContent {
            MaterialTheme { if (visible.value) GamepadControlPanel({ bytes ->
                reports.add(bytes.copyOf())
                if (rejectNextReport) { rejectNextReport = false; false } else peer?.sendReport(GamepadReport.ID, bytes) ?: true
            }, Modifier.fillMaxSize()) }
        }
        val address = InstrumentationRegistry.getArguments().getString("peerAddress")
        if (!address.isNullOrBlank()) {
            val repository = GlobalContext.get().get<BluetoothRepository>()
            peer = repository
            compose.runOnUiThread { repository.startHidProfile(address) }
            compose.waitUntil(20000) { repository.getDeviceHidConnectionState().value.state == 2 }
        }
    }
    @After fun cleanup() {
        peer?.sendReport(GamepadReport.ID, GamepadReport.neutral())
        peer?.disconnectDevice()
        peer?.stopHidProfile()
    }
    private fun TouchInjectionScope.point(label: String, dx: Float = 0f, dy: Float = 0f): Offset {
        val c = GamepadLayout.controls.single { it.label == label }
        val s = min(width / GamepadLayout.WIDTH, height / GamepadLayout.HEIGHT)
        return Offset((width - GamepadLayout.WIDTH * s) / 2 + (c.x + dx) * s,
            (height - GamepadLayout.HEIGHT * s) / 2 + (c.y + dy) * s)
    }
    private fun has(button: GamepadButton, p: ByteArray) = ((p[0].toInt() and 255) or ((p[1].toInt() and 255) shl 8)) and button.mask != 0

    @Test fun simultaneousControlsAndCancelRelease() {
        compose.onNodeWithContentDescription("Xbox 手柄操作区").performTouchInput {
            down(0, point("左摇杆")); moveTo(0, point("左摇杆",72f))
            down(1, point("A")); down(2, point("RT")); advanceEventTime(180)
            moveTo(0, point("左摇杆",72f,30f)); up(1); advanceEventTime(100); cancel()
        }
        compose.runOnIdle {
            assertTrue(reports.any { has(GamepadButton.A,it) && has(GamepadButton.RT,it) && it[3] > 80 && (it[8].toInt() and 255) == 255 })
            assertTrue(reports.any { !has(GamepadButton.A,it) && has(GamepadButton.RT,it) && it[3] > 80 })
            assertArrayEquals(GamepadReport.neutral(), reports.last())
        }
    }
    @Test fun allButtonsDpadAndSticksReachTheirOwnFields() {
        val node=compose.onNodeWithContentDescription("Xbox 手柄操作区")
        for (button in GamepadButton.entries) node.performTouchInput {
            down(0,point(button.name)); advanceEventTime(50);up(0)
        }
        node.performTouchInput {
            down(0,point("方向键"));moveTo(0,point("方向键",28f,-28f));advanceEventTime(50);up(0)
            down(0,point("右摇杆"));moveTo(0,point("右摇杆",0f,-62f));advanceEventTime(50);up(0)
        }
        compose.runOnIdle {
            for (button in GamepadButton.entries) assertTrue(button.name,reports.any { has(button,it) })
            assertTrue(reports.any { it[2].toInt() == 1 })
            assertTrue(reports.any { it[6].toInt() == -127 && it[4].toInt() == 0 })
            assertArrayEquals(GamepadReport.neutral(),reports.last())
        }
    }
    @Test fun failedPressStillAttemptsRelease() {
        compose.runOnIdle { rejectNextReport = true }
        compose.onNodeWithContentDescription("Xbox 手柄操作区").performTouchInput {
            down(0,point("A"));advanceEventTime(50);up(0)
        }
        compose.runOnIdle {
            assertTrue(reports.any { has(GamepadButton.A,it) })
            assertArrayEquals(GamepadReport.neutral(),reports.last())
        }
    }
    @Test fun leavingPanelWhileHoldingReturnsNeutral() {
        compose.onNodeWithContentDescription("Xbox 手柄操作区").performTouchInput { down(0,point("LB")) }
        compose.runOnIdle { assertTrue(reports.any { has(GamepadButton.LB,it) });visible.value=false }
        compose.waitForIdle()
        compose.runOnIdle { assertArrayEquals(GamepadReport.neutral(),reports.last()) }
    }
}
