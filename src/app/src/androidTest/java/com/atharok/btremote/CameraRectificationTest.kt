// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import android.graphics.*
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atharok.btremote.automation.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Synthetic geometry regression; never counted as optical phone-to-phone acceptance. */
@RunWith(AndroidJUnit4::class)
class CameraRectificationTest {
    @Test fun perspectiveWarpCropsCameraPixelsAndRequiresANewerFrame()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val camera=CameraObservation(context,{_,_->},{})
        val bitmap=Bitmap.createBitmap(400,800,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint()
        for((rect,color) in listOf(Rect(0,0,200,400) to Color.RED,Rect(200,0,400,400) to Color.GREEN,Rect(0,400,200,800) to Color.BLUE,Rect(200,400,400,800) to Color.YELLOW)) {
            paint.color=color;canvas.drawRect(rect,paint)
        }
        val output=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,95,output);bitmap.recycle()
        val bytes=output.toByteArray()
        camera.open.value=true
        camera.quad.value=CameraQuad(listOf(CameraPoint(.1f,.1f),CameraPoint(.9f,.15f),CameraPoint(.8f,.9f),CameraPoint(.15f,.8f))).validate()
        camera.raw.value=CameraObservation.Raw(bytes,400,800,1,SystemClock.elapsedRealtime(),0)
        try {
            val request=async { camera.capture() };yield()
            assertFalse("Old raw frame cannot satisfy a fresh capture",request.isCompleted)
            camera.raw.value=CameraObservation.Raw(bytes,400,800,2,SystemClock.elapsedRealtime(),0)
            val frame=request.await();assertEquals("camera",frame.source)
            val decoded=BitmapFactory.decodeByteArray(frame.bytes,0,frame.bytes.size)
            assertEquals(720,decoded.width);assertEquals(1600,decoded.height)
            val tl=decoded.getPixel(60,100);val tr=decoded.getPixel(660,100)
            val bl=decoded.getPixel(60,1500);val br=decoded.getPixel(660,1500)
            assertTrue(Color.red(tl)>200&&Color.green(tl)<40)
            assertTrue(Color.green(tr)>200&&Color.red(tr)<40)
            assertTrue(Color.blue(bl)>200&&Color.red(bl)<40)
            assertTrue(Color.red(br)>200&&Color.green(br)>200)
            decoded.recycle()
        } finally { camera.stop(notify=false) }
    }
}
