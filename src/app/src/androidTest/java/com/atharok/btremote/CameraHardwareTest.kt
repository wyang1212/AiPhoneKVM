// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atharok.btremote.automation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real camera plumbing only: the framing below is NOT target-screen or optical-action acceptance. */
@RunWith(AndroidJUnit4::class)
class CameraHardwareTest {
    @Test fun realCameraFreshFramesPerspectiveAndCancellation()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        assertEquals("Explicit camera permission required",PackageManager.PERMISSION_GRANTED,ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA))
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/com.atharok.btremote.presentation.activities.MainActivity")).use { it.readBytes() }
        delay(3000)
        val runtime=AutomationRuntime.get(context)
        try {
            instrumentation.runOnMainSync {
                runtime.selectCamera(true)
                @Suppress("DEPRECATION")
                runtime.camera.start { context.getSystemService(WindowManager::class.java).defaultDisplay.rotation*90 }
            }
            val raw=withTimeout(15000) { runtime.camera.raw.filterNotNull().first() }
            File(context.filesDir,"camera-hardware-raw.jpg").writeBytes(raw.bytes)
            assertTrue(raw.width>=100 && raw.height>=100)
            runtime.camera.chooseScreen(CameraQuad(listOf(CameraPoint(.05f,.05f),CameraPoint(.95f,.05f),CameraPoint(.95f,.95f),CameraPoint(.05f,.95f))))
            val a=runtime.capture.capture();val b=runtime.capture.capture()
            assertEquals("camera",a.source);assertEquals(720,a.width);assertEquals(1600,a.height)
            assertTrue(b.receivedAt>a.receivedAt)
            assertEquals(0L,runtime.capture.companionSeen.value)
            val decoded=BitmapFactory.decodeByteArray(b.bytes,0,b.bytes.size)
            assertNotNull(decoded);assertEquals(720,decoded.width);assertEquals(1600,decoded.height);decoded.recycle()
            File(context.filesDir,"camera-hardware-rectified.jpg").writeBytes(b.bytes)
            val pending=async { runtime.capture.capture() }
            yield();delay(30)
            instrumentation.runOnMainSync { runtime.camera.stop("camera test stop") }
            runCatching { pending.await() }
            assertFalse(runtime.capture.capturing);assertFalse(runtime.camera.open.value);assertFalse(runtime.camera.ready.value)
        } finally {
            instrumentation.runOnMainSync { runtime.camera.stop(notify=false);runtime.selectCamera(false) }
        }
    }
}
