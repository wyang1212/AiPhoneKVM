// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atharok.btremote.common.utils.*
import com.atharok.btremote.domain.repositories.BluetoothRepository
import com.atharok.btremote.presentation.viewmodel.RemoteViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** Hardware-only report acceptance, independently checked by the receiver's event probe. */
@RunWith(AndroidJUnit4::class)
class GamepadHidReportTest {
    @Test fun controllerReportsAndDisconnectRelease() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val address=InstrumentationRegistry.getArguments().getString("peerAddress") ?: error("peerAddress required")
        val pkg=instrumentation.targetContext.packageName
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n $pkg/com.atharok.btremote.presentation.activities.MainActivity"
        )).use { it.readBytes() }
        Thread.sleep(2500)
        val repo=GlobalContext.get().get<BluetoothRepository>()
        fun waitState(timeout:Long, predicate:()->Boolean) {
            val end=System.currentTimeMillis()+timeout
            while(!predicate() && System.currentTimeMillis()<end) Thread.sleep(100)
            assertTrue("Bluetooth state reached",predicate())
        }
        waitState(12000) { repo.isBluetoothHidProfileRegistered().value }
        if(repo.getDeviceHidConnectionState().value.state != 2) assertTrue(repo.connectDevice(address))
        waitState(20000) { repo.getDeviceHidConnectionState().value.state == 2 }
        Thread.sleep(1000)
        fun send(s:GamepadState) { assertTrue(repo.sendReport(GamepadReport.ID,GamepadReport.encode(s)));Thread.sleep(120) }
        try {
            send(GamepadState())
            for (button in GamepadButton.entries) {
                send(GamepadState(buttons=button.mask,leftTrigger=if(button==GamepadButton.LT)255 else 0,rightTrigger=if(button==GamepadButton.RT)255 else 0))
                send(GamepadState())
            }
            for (hat in 0..7) { send(GamepadState(hat=hat));send(GamepadState()) }
            for (s in listOf(GamepadState(leftX=-1f),GamepadState(leftX=1f),GamepadState(leftY=-1f),GamepadState(leftY=1f),GamepadState(rightX=-1f),GamepadState(rightX=1f),GamepadState(rightY=-1f),GamepadState(rightY=1f))) { send(s);send(GamepadState()) }
            send(GamepadState(buttons=GamepadButton.A.mask or GamepadButton.RB.mask,leftX=1f,rightY=-1f,rightTrigger=255))
            instrumentation.runOnMainSync { GlobalContext.get().get<RemoteViewModel>().releaseAllInputs() }
            Thread.sleep(150)
            assertTrue(repo.sendReport(KEYBOARD_REPORT_ID,byteArrayOf(0,4)))
            Thread.sleep(100);assertTrue(repo.sendReport(KEYBOARD_REPORT_ID,byteArrayOf(0,0)))
            assertTrue(repo.sendReport(MOUSE_REPORT_ID,byteArrayOf(0,12,6,0)))
            Thread.sleep(100);assertTrue(repo.sendReport(MOUSE_REPORT_ID,byteArrayOf(0,0,0,0)))
            assertTrue(repo.sendReport(REMOTE_REPORT_ID,byteArrayOf(0xcd.toByte(),0)))
            Thread.sleep(100);assertTrue(repo.sendReport(REMOTE_REPORT_ID,byteArrayOf(0,0)))
            send(GamepadState(buttons=GamepadButton.B.mask,leftX=-1f))
            assertTrue(repo.disconnectDevice())
            Thread.sleep(300)
        } finally {
            repo.sendReport(GamepadReport.ID,GamepadReport.neutral())
            repo.disconnectDevice()
        }
    }
}
