// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class EnhancedCommandTest {
    private val reports=mutableListOf<Pair<Int,List<Int>>>()
    private val received=mutableListOf<Pair<String,JsonObject>>()
    private var verified=true
    private val enhanced=object:EnhancedControl {
        override fun available()=true
        override suspend fun execute(action: String,params: JsonObject): JsonObject {
            received+=action to params
            return buildJsonObject {put("delivery","companion_accessibility");put("textVerified",verified)}
        }
    }
    private val engine=CommandEngine(object:HidOutput {
        override fun connected()=true
        override fun report(id: Int,bytes: ByteArray): Boolean {reports+=id to bytes.map {it.toInt()};return true}
    },{AutomationConfig(screenWidth=720,screenHeight=1600)},{},{buildJsonObject {}},pause={},enhanced=enhanced)
    private fun command(action:String,params:JsonObject)=runBlocking {engine.execute(buildJsonObject {put("action",action);put("params",params)}.toString())}
    @Test fun unicodeAndMixedTextUseVerifiedDirectInputWithoutTypingKeys() {
        val p=buildJsonObject {put("text","中文 AiPhone42！😃");put("replace",true)}
        assertEquals("SUCCESS",command("INPUT",p).stringValue("status"));assertEquals(p.stringValue("text"),received.single().second.stringValue("text"));assertTrue(reports.all {(id,r)->if(id==5) r==listOf(0,0,8,0,0,0,0,0,0) else r.all {it==0}})
    }
    @Test fun unverifiedInputIsFailure() {
        verified=false;assertEquals("FAILED",command("INPUT",buildJsonObject {put("text","test")}).stringValue("status"))
    }
    @Test fun invalidConfirmHasNoCompanionSideEffects() {
        assertEquals("FAILED",command("INPUT",buildJsonObject {put("text","abc");put("confirm","bad")}).stringValue("status"));assertTrue(received.isEmpty())
    }
    @Test fun swipeUsesTouchGestureRatherThanMouseButtonDrag() {
        assertEquals("SUCCESS",command("SWIPE_UP",buildJsonObject {}).stringValue("status"));val request=received.single();assertEquals("SWIPE",request.first)
        assertEquals(1280,request.second.intValue("from_y"));assertEquals(320,request.second.intValue("to_y"));assertTrue(reports.all {(id,r)->if(id==5) r==listOf(0,0,8,0,0,0,0,0,0) else r.all {it==0}})
    }
    @Test fun gestureBoundsAndDurationValidatedBeforeDispatch() {
        assertEquals("FAILED",command("TAP",buildJsonObject {put("x",720);put("y",100)}).stringValue("status"))
        assertEquals("FAILED",command("TAP",buildJsonObject {put("x",100);put("y",100);put("duration",2001)}).stringValue("status"));assertTrue(received.isEmpty())
    }
    @Test fun screenshotCoordinatesSurviveAllEnhancedCommandRoutes() {
        val screen=ControlledScreen(720,1600,0,320).json()
        for((action,params) in listOf(
            "TAP" to buildJsonObject {put("x",100);put("y",200)},
            "SWIPE_UP" to buildJsonObject {},
            "INPUT" to buildJsonObject {put("text","显示");put("replace",true)}
        )) {
            received.clear()
            val request=buildJsonObject {params.forEach {(k,v)->put(k,v)};put("screen",screen)}
            assertEquals("SUCCESS",command(action,request).stringValue("status"))
            assertEquals(screen,received.single().second["screen"])
        }
    }
}
