// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atharok.btremote.automation.*
import com.atharok.btremote.domain.repositories.BluetoothRepository
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File

/** Explicit opt-in physical acceptance. Inputs must be measured from the saved REAL camera photos. */
@RunWith(AndroidJUnit4::class)
class CameraOpticalAcceptanceTest {
    @Test fun interactiveRealCameraCalibrationAndGoal()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue("Physical rig and opt-in required",args.getString("opticalAcceptance")=="true")
        val address=args.getString("peerAddress")?:error("peerAddress required")
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        val runtime=AutomationRuntime.get(context)
        val directory=File(context.filesDir,"optical-acceptance").apply { mkdirs() }
        File(directory,"input.json").delete()
        File(directory,"finished.json").delete()
        var ack="";var error="";var finished=false;var rawSequence=-1L;var calibrationVersion=-1L;var observedVersion=-1L
        fun atomic(name:String,bytes:ByteArray) {
            val temp=File(directory,"$name.tmp");temp.writeBytes(bytes);check(temp.renameTo(File(directory,name)))
        }
        fun status() {
            atomic("status.json",buildJsonObject {
                put("ack",ack);put("error",error);put("cameraOpen",runtime.camera.open.value)
                put("ready",runtime.camera.ready.value);put("step",runtime.camera.calibrationStep.value)
                put("busy",runtime.camera.busy.value);put("message",runtime.camera.message.value)
                put("source",if(runtime.cameraMode.value)"camera" else "companion")
                put("enhancedActive",runtime.usesEnhancedControl());put("companionAvailable",runtime.companion.available())
                put("running",runtime.running.value);put("task",runtime.goalLoop.state.value.json())
                put("logs",JsonArray(runtime.logs.value.map(::JsonPrimitive)))
                put("suggestion",runtime.vision.suggestion.value?:JsonNull)
            }.toString().toByteArray())
        }
        var activity:ComponentActivity?=null
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP)runtime.camera.stop("光学验收失去主控前台") }
        try {
            ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/com.atharok.btremote.presentation.activities.MainActivity")).use { it.readBytes() }
            delay(3000)
            ins.runOnMainSync {
                activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<ComponentActivity>().firstOrNull()
            }
            check(activity!=null) { "Controller activity must be visibly resumed" }
            val repo=GlobalContext.get().get<BluetoothRepository>()
            withTimeout(15000) { while(!repo.isBluetoothHidProfileRegistered().value)delay(100) }
            if(!repo.getDeviceHidConnectionState().value.isConnectedTo(address))ins.runOnMainSync { repo.connectDevice(address) }
            withTimeout(25000) { while(!repo.getDeviceHidConnectionState().value.isConnectedTo(address))delay(100) }
            ins.runOnMainSync {
                runtime.authorizeTarget(address);runtime.start();runtime.selectCamera(true)
                activity!!.lifecycle.addObserver(observer)
                activity!!.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                @Suppress("DEPRECATION")
                runtime.camera.start { activity!!.windowManager.defaultDisplay.rotation*90 }
            }
            withTimeout(15000) { while(runtime.camera.raw.value==null)delay(100) }
            val deadline=System.currentTimeMillis()+20*60_000L
            while(!finished && System.currentTimeMillis()<deadline) {
                runtime.camera.raw.value?.let { r -> if(r.sequence!=rawSequence) { rawSequence=r.sequence;atomic("raw.jpg",r.bytes) } }
                runtime.camera.calibrationImage.value?.let { f -> if(f.receivedAt!=calibrationVersion) {
                    calibrationVersion=f.receivedAt;atomic("calibration.jpg",f.bytes)
                    atomic("calibration-${runtime.camera.calibrationStep.value}.jpg",f.bytes)
                } }
                runtime.capture.lastFrame.value?.let { f -> if(f.receivedAt!=observedVersion) {
                    observedVersion=f.receivedAt;check(f.source=="camera") { "Non-camera frame encountered" }
                    atomic("observation-${f.receivedAt}.jpg",f.bytes);atomic("observation.jpg",f.bytes)
                } }
                status()
                val input=File(directory,"input.json")
                if(input.exists()) {
                    try {
                        val command=Json.parseToJsonElement(input.readText()).jsonObject
                        input.delete()
                        val id=command.stringValue("id")
                        if(id!=ack) {
                            ack=id;error=""
                            when(command.stringValue("action")) {
                                "quad" -> {
                                    val points=command.getValue("points").jsonArray.map { val xy=it.jsonArray;CameraPoint(xy[0].jsonPrimitive.float,xy[1].jsonPrimitive.float) }
                                    runtime.camera.chooseScreen(CameraQuad(points))
                                    atomic("rectified.jpg",runtime.camera.capture().bytes)
                                }
                                "calibrate" -> runtime.camera.beginCalibration()
                                "cursor" -> runtime.camera.recordCursor(CameraPoint(command.getValue("x").jsonPrimitive.float,command.getValue("y").jsonPrimitive.float))
                                "goal" -> {
                                    check(runtime.camera.ready.value && runtime.cameraMode.value && !runtime.usesEnhancedControl())
                                    runtime.startGoal(command.stringValue("goal"),GoalLimits(6,4))
                                }
                                "capture" -> atomic("observation-manual.jpg",runtime.capture.capture().bytes)
                                "stop" -> { runtime.goalLoop.stopAndAwait();finished=true }
                                else -> error("Unknown input action")
                            }
                        }
                    } catch(e:Exception) { error=e.message?:e.javaClass.simpleName }
                }
                if(!runtime.camera.open.value) { error="Camera closed or activity left foreground";status();break }
                delay(350)
            }
            status()
        } finally {
            runtime.stopAndAwait()
            context.stopService(android.content.Intent(context,AutomationService::class.java))
            runtime.camera.stop(notify=false)
            ins.runOnMainSync { activity?.lifecycle?.removeObserver(observer);activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            atomic("finished.json",buildJsonObject { put("closed",true);put("error",error) }.toString().toByteArray())
        }
    }
}
