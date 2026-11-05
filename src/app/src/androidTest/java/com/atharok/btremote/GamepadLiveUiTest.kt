// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import android.content.Intent
import android.os.SystemClock
import android.view.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.atharok.btremote.domain.entities.remoteInput.GamepadLayout
import com.atharok.btremote.domain.entities.remoteInput.FcGamepadLayout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.min

/** Opt-in actual MainActivity + native MotionEvents; validate independently on the Bluetooth receiver. */
class GamepadLiveUiTest {
    @Test fun normalPageDualSticksBackgroundLeaveAndDisconnect() {
        val ins=InstrumentationRegistry.getInstrumentation()
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveGamepad")=="true")
        val peer=args.getString("peerName")?:error("peerName required")
        val fc=args.getString("layout")=="fc"
        val padDescription=if(fc) "FC 手柄操作区" else "Xbox 手柄操作区"
        val controls=if(fc) FcGamepadLayout.controls else GamepadLayout.controls
        val context=ins.targetContext
        val trace=File(context.filesDir,"gamepad-live-trace.txt")
        trace.writeText("")
        fun note(s:String) { trace.appendText(s+"\n") }
        fun launch() {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand(
                "am start -W -n "+context.packageName+"/com.atharok.btremote.presentation.activities.MainActivity"
            )).use { it.readBytes() }
        }
        note("interactive before wake="+context.getSystemService(android.os.PowerManager::class.java).isInteractive)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP")).use { it.readBytes() }
        launch();Thread.sleep(1800)
        assertTrue(context.getSystemService(android.os.PowerManager::class.java).isInteractive)
        lateinit var activity:ComponentActivity
        fun <T> main(block:()->T):T {
            if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())return block()
            var value:Any?=null;var error:Throwable?=null
            ins.runOnMainSync { try { value=block() }catch(e:Throwable) { error=e } }
            error?.let { throw it }
            @Suppress("UNCHECKED_CAST") return value as T
        }
        fun owner():SemanticsOwner=main {
            activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ComponentActivity>().first { it.javaClass.name.endsWith("MainActivity") }
            fun find(v:View):View? {
                if(v.javaClass.name=="androidx.compose.ui.platform.AndroidComposeView")return v
                if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let { return it }
                return null
            }
            val v=find(activity.window.decorView)?:error("Compose view not visible")
            v.javaClass.getMethod("getSemanticsOwner").invoke(v) as SemanticsOwner
        }
        fun nodes():List<SemanticsNode> = main {
            val result=mutableListOf<SemanticsNode>()
            fun visit(n:SemanticsNode) { result.add(n);n.children.forEach(::visit) }
            visit(owner().unmergedRootSemanticsNode);result
        }
        fun labels(n:SemanticsNode)=(n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }?:emptyList())+
            (n.config.getOrNull(SemanticsProperties.ContentDescription)?:emptyList())
        fun click(label:String) {
            val deadline=SystemClock.uptimeMillis()+12000
            while(SystemClock.uptimeMillis()<deadline) {
                val done=try { main {
                    val n=nodes().firstOrNull { label in labels(it) }
                    var p=n
                    while(p!=null) {
                        val action=p.config.getOrNull(SemanticsActions.OnClick)?.action
                        if(action!=null)return@main action.invoke()
                        p=p.parent
                    }
                    false
                } }catch(_:NoSuchElementException) { false }
                if(done) { note("click $label");Thread.sleep(700);return }
                Thread.sleep(150)
            }
            error("Control not found: $label")
        }
        fun canvas():FloatArray=main {
            val n=nodes().first { padDescription in labels(it) }
            val r=n.boundsInWindow
            val loc=IntArray(2);activity.window.decorView.getLocationOnScreen(loc)
            floatArrayOf(r.left+loc[0],r.top+loc[1],r.width,r.height)
        }
        var previousTouchMode:Boolean?=null
        val pointers=linkedMapOf<Int,Pair<Float,Float>>()
        var downAt=0L
        fun point(label:String,dx:Float=0f,dy:Float=0f):Pair<Float,Float> {
            val b=canvas();val s=min(b[2]/GamepadLayout.WIDTH,b[3]/GamepadLayout.HEIGHT)
            val c=controls.single { it.label==label }
            return b[0]+(b[2]-GamepadLayout.WIDTH*s)/2+(c.x+dx)*s to
                b[1]+(b[3]-GamepadLayout.HEIGHT*s)/2+(c.y+dy)*s
        }
        fun event(action:Int) {
            val props=pointers.keys.map { id->MotionEvent.PointerProperties().apply { this.id=id;toolType=MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
            val coords=pointers.values.map { p->MotionEvent.PointerCoords().apply { x=p.first;y=p.second;pressure=1f;size=.1f } }.toTypedArray()
            val e=MotionEvent.obtain(downAt,SystemClock.uptimeMillis(),action,props.size,props,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
            try { ins.sendPointerSync(e) } finally { e.recycle() }
            Thread.sleep(220)
        }
        fun down(id:Int,p:Pair<Float,Float>) {
            if(pointers.isEmpty())downAt=SystemClock.uptimeMillis()
            pointers[id]=p
            event(if(pointers.size==1)MotionEvent.ACTION_DOWN else MotionEvent.ACTION_POINTER_DOWN or ((pointers.size-1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT))
        }
        fun move(id:Int,p:Pair<Float,Float>) { pointers[id]=p;event(MotionEvent.ACTION_MOVE) }
        fun up(id:Int) {
            val i=pointers.keys.indexOf(id)
            event(if(pointers.size==1)MotionEvent.ACTION_UP else MotionEvent.ACTION_POINTER_UP or (i shl MotionEvent.ACTION_POINTER_INDEX_SHIFT))
            pointers.remove(id)
        }
        fun cancel() { if(pointers.isNotEmpty())event(MotionEvent.ACTION_CANCEL);pointers.clear() }
        try {
            click("选择设备");click(peer)
            assertTrue("Choosing a device returns to home",nodes().any { "AI 自动化" in labels(it) })
            assertFalse("Choosing is not control entry",nodes().any { padDescription in labels(it) })
            note("device selection returned home without control entry")
            click(if(fc) "FC 红白机" else "Xbox 手柄");Thread.sleep(1200)
            val b=canvas();assertTrue("Landscape page",b[2]>b[3]);note("landscape "+b.joinToString())
            Thread.sleep(2500)
            previousTouchMode=main { activity.window.decorView.isInTouchMode }
            note("touch mode before="+previousTouchMode)
            ins.setInTouchMode(true)
            Thread.sleep(250)
            note("touch mode active="+main { activity.window.decorView.isInTouchMode })
            fun shot(name:String) { val bitmap=ins.uiAutomation.takeScreenshot();if(bitmap!=null) {
                File(context.filesDir,name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            } }
            if (fc) {
                for (button in listOf("A","B","SELECT","START")) { down(0,point(button));up(0) }
                down(0,point("方向键"))
                for ((dx,dy) in listOf(0f to -50f,35f to -35f,50f to 0f,35f to 35f,0f to 50f,-35f to 35f,-50f to 0f,-35f to -35f)) move(0,point("方向键",dx,dy))
                move(0,point("方向键",50f,-50f));down(1,point("A"));down(2,point("B"));shot("gamepad-fc-held.png");note("FC diagonal + A + B")
                up(1);note("FC A released, B and direction retained")
                up(2);cancel();note("FC cancel neutral")
                down(0,point("START"));main { activity.moveTaskToBack(true) };Thread.sleep(600);pointers.clear();note("FC background releases START")
                launch();Thread.sleep(900)
                down(0,point("B"));ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);cancel();note("leaving FC releases B")
                click("FC 红白机");Thread.sleep(900)
                down(0,point("A"));main { org.koin.core.context.GlobalContext.get().get<com.atharok.btremote.presentation.viewmodel.RemoteViewModel>().disconnectDevice() };cancel();note("FC disconnect releases A")
            } else {
            note("left start "+point("左摇杆"))
            down(0,point("左摇杆"));move(0,point("左摇杆",72f));shot("gamepad-left-held.png")
            down(1,point("右摇杆"));move(1,point("右摇杆",0f,-68f))
            down(2,point("A"));shot("gamepad-dual-held.png");note("dual sticks + A")
            up(0);note("left released, right + A retained")
            up(2);note("A released, right retained")
            cancel();note("cancel neutral")
            down(0,point("LB"));main { activity.moveTaskToBack(true) };Thread.sleep(600)
            pointers.clear();note("background releases LB")
            launch();Thread.sleep(900)
            down(0,point("RB"));ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);cancel();note("leaving Xbox panel releases RB")
            click("Xbox 手柄");Thread.sleep(900)
            down(0,point("Y"));main { org.koin.core.context.GlobalContext.get().get<com.atharok.btremote.presentation.viewmodel.RemoteViewModel>().disconnectDevice() };cancel();note("disconnect releases Y")
            }
            Thread.sleep(500);note("completed")
        } finally {
            cancel()
            previousTouchMode?.let { ins.setInTouchMode(it) }
            main { runCatching { org.koin.core.context.GlobalContext.get().get<com.atharok.btremote.presentation.viewmodel.RemoteViewModel>().releaseAllInputs() } }
        }
    }
}
