// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CameraCommandTest {
    private val reports=mutableListOf<Pair<Int,List<Int>>>()
    private val positions=mutableListOf<Pair<Int,Int>>()
    private var invalid=false
    private var enhancedCalls=0
    private val engine=CommandEngine(object:HidOutput {
        override fun connected()=true
        override fun report(id:Int,bytes:ByteArray):Boolean { reports+=id to bytes.map { it.toInt() };return true }
    },{AutomationConfig(screenWidth=720,screenHeight=1600)},{},{buildJsonObject {}},pause={},enhanced=object:EnhancedControl {
        override fun available()=true
        override suspend fun execute(action:String,params:JsonObject):JsonObject { enhancedCalls++;return buildJsonObject {} }
    },cameraPointer=object:CameraPointer {
        override fun enabled()=true
        override suspend fun position(x:Int,y:Int) { check(!invalid) { "校准已失效" };positions+=x to y }
    })
    private fun send(action:String,params:JsonObject)=runBlocking { engine.execute(buildJsonObject { put("action",action);put("params",params) }.toString()) }
    @Test fun cameraTapBypassesOnlineCompanionAndUsesMeasuredPointer() {
        assertEquals("SUCCESS",send("TAP",buildJsonObject { put("x",123);put("y",456) }).stringValue("status"))
        assertEquals(listOf(123 to 456),positions);assertEquals(0,enhancedCalls)
        assertTrue(reports.any { (id,r)->id==2&&r[0]==1 })
    }
    @Test fun invalidCalibrationCannotEmitMouseClick() {
        invalid=true
        assertEquals("FAILED",send("TAP",buildJsonObject { put("x",10);put("y",20) }).stringValue("status"))
        assertFalse(reports.any { (id,r)->id==2&&r[0]!=0 })
    }
    @Test fun verticalSwipeUsesWheelWithoutMouseDragOrCompanion() {
        assertEquals("SUCCESS",send("SWIPE",buildJsonObject { put("from_x",300);put("from_y",1300);put("to_x",300);put("to_y",300) }).stringValue("status"))
        assertTrue(reports.any { (id,r)->id==2&&r.size==4&&r[3]<0 })
        assertFalse(reports.any { (id,r)->id==2&&r[0]!=0 });assertEquals(0,enhancedCalls)
    }
    @Test fun unsupportedHorizontalSwipeHasNoPositionOrClick() {
        assertEquals("FAILED",send("SWIPE",buildJsonObject { put("from_x",600);put("from_y",300);put("to_x",100);put("to_y",300) }).stringValue("status"))
        assertTrue(positions.isEmpty());assertFalse(reports.any { (id,r)->id==2&&r[0]!=0 })
    }
    @Test fun asciiReplaceUsesHidSelectAllAndNeverCompanion() {
        assertEquals("SUCCESS",send("INPUT",buildJsonObject { put("text","pinyin");put("replace",true) }).stringValue("status"))
        assertTrue(reports.any { (id,r)->id==1&&r==listOf(1,4) });assertEquals(0,enhancedCalls)
    }
    @Test fun unicodeFailsBeforeSelectAllOrPartialInput() {
        assertEquals("FAILED",send("INPUT",buildJsonObject { put("text","test中文");put("replace",true) }).stringValue("status"))
        assertFalse(reports.any { (id,r)->id==1&&r.any { it!=0 } });assertEquals(0,enhancedCalls)
    }
}
