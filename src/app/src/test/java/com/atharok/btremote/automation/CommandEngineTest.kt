// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CommandEngineTest {
    private class Output: HidOutput {
        var online=true
        var accepted=true
        val reports=mutableListOf<Pair<Int,List<Int>>>()
        override fun connected()=online
        override fun report(id: Int,bytes: ByteArray): Boolean { reports+=id to bytes.map { it.toInt() and 255 };return accepted }
        fun active()=reports.filter { (id,b)-> if(id==5) b!=listOf(0,0,8,0,0,0,0,0,0) else b.any { it!=0 } }
    }
    private val out=Output()
    private var config=AutomationConfig(screenWidth=720,screenHeight=1600)
    private val engine=CommandEngine(out,{config},{config=it},{buildJsonObject { put("target","B") }},pause={})
    private fun execute(raw: String)=runBlocking { engine.execute(raw) }
    private fun action(a: String,p: String="{}",id: String="test")=execute("""{"id":"$id","action":"$a","params":$p}""")
    private fun success(r: JsonObject)=assertEquals("SUCCESS",r["status"]!!.jsonPrimitive.content)
    private fun fail(r: JsonObject)=assertEquals("FAILED",r["status"]!!.jsonPrimitive.content)

    @Test fun newAndOldProtocolAndCase() {
        success(execute("""{"message_id":"old","action":"key_press","params":{"key":"a"}}"""))
        assertTrue(out.active().contains(1 to listOf(0,4)))
    }
    @Test fun duplicateIdDoesNotRepeatInput() {
        success(action("KEY_PRESS","""{"key":"A"}"""));val before=out.active().size
        success(action("KEY_PRESS","""{"key":"A"}"""));assertEquals(before,out.active().size)
    }
    @Test fun duplicateIdDifferentPayloadRejected() {
        success(action("HOME"));val before=out.active().size
        val response=action("BACK");fail(response);assertEquals("ID_CONFLICT",response["code"]!!.jsonPrimitive.content)
        assertEquals(before,out.active().size)
    }
    @Test fun comboHasModifierAndRelease() {
        success(action("KEY_COMBO","""{"key":"C","modifiers":["ctrl","shift"]}"""))
        assertTrue(out.reports.contains(1 to listOf(3,6)))
        assertEquals(3 to listOf(0,0),out.reports.last())
    }
    @Test fun mediaHomeAndBackUseConsumerReports() {
        success(action("HOME",id="home"));success(action("BACK",id="back"))
        assertTrue(out.active().contains(3 to listOf(0x23,2)))
        assertTrue(out.active().contains(3 to listOf(0x24,2)))
    }
    @Test fun invalidKeyRejectedBeforeAnyInput() { fail(action("KEY_PRESS","""{"key":"UNKNOWN"}"""));assertTrue(out.active().isEmpty()) }
    @Test fun invalidModifierRejectedBeforeAnyInput() { fail(action("KEY_COMBO","""{"key":"C","modifiers":["oops"]}"""));assertTrue(out.active().isEmpty()) }
    @Test fun asciiTextHasShiftAndPunctuation() {
        success(action("INPUT","""{"text":"aA!?0","confirm":"enter"}"""))
        assertTrue(out.active().contains(1 to listOf(0,4)));assertTrue(out.active().contains(1 to listOf(2,4)))
        assertTrue(out.active().contains(1 to listOf(2,30)));assertTrue(out.active().contains(1 to listOf(2,56)))
        assertTrue(out.active().contains(1 to listOf(0,39)));assertTrue(out.active().contains(1 to listOf(0,40)))
    }
    @Test fun unsupportedTextRejectsWholeString() { fail(action("INPUT","""{"text":"abc中文"}"""));assertTrue(out.active().isEmpty()) }
    @Test fun invalidConfirmationHasNoSideEffects() { fail(action("INPUT","""{"text":"abc","confirm":"oops"}"""));assertTrue(out.active().isEmpty()) }
    @Test fun inputLimitRejected() { fail(action("INPUT",buildJsonObject { put("text","x".repeat(257)) }.toString()));assertTrue(out.active().isEmpty()) }
    @Test fun disconnectedHidFails() { out.online=false;val r=action("HOME");fail(r);assertEquals("HID_UNAVAILABLE",r["code"]!!.jsonPrimitive.content) }
    @Test fun sendFailureIsNotSuccess() { out.accepted=false;fail(action("HOME")) }
    @Test fun invalidCoordinatesDoNotClick() { fail(action("TAP","""{"x":720,"y":200}"""));assertTrue(out.active().isEmpty()) }
    @Test fun missingCoordinatesDoNotClick() { fail(action("TAP","""{"x":50}"""));assertTrue(out.active().isEmpty()) }
    @Test fun tapHasPointerMotionAndPressRelease() {
        success(action("TAP","""{"x":120,"y":250}"""))
        assertTrue(out.reports.contains(2 to listOf(1,0,0,0)))
        val reports=out.reports.filter { it.first==2 }
        assertEquals(listOf(0,0,0,0),reports.last().second)
        assertTrue(reports.all { it.second.size==4 })
    }
    @Test fun swipeKeepsButtonHeldDuringMovement() {
        success(action("SWIPE","""{"from_x":100,"from_y":500,"to_x":200,"to_y":200,"duration":300}"""))
        val movements=out.reports.filter { it.first==2 && it.second[0]==1 && (it.second[1]!=0 || it.second[2]!=0) }
        assertTrue(movements.isNotEmpty());assertEquals(100,movements.sumOf { it.second[1].toByte().toInt() })
        assertEquals(-300,movements.sumOf { it.second[2].toByte().toInt() })
    }
    @Test fun directionSwipeStaysInsideScreen() { listOf("SWIPE_UP","SWIPE_DOWN","SWIPE_LEFT","SWIPE_RIGHT").forEach { success(action(it,id=it)) } }
    @Test fun calibrateChangesConfigAndTestMovement() {
        success(action("CALIBRATE","""{"sensitivity":2.5,"move_delay":20,"hid_x":100,"hid_y":0}"""))
        assertEquals(2.5,config.sensitivity,0.0);assertEquals(20,config.moveDelayMs)
        assertEquals(100,out.reports.filter { it.first==2 }.sumOf { it.second[1].toByte().toInt() })
    }
    @Test fun invalidCalibrationIsAtomic() { val before=config;fail(action("CALIBRATE","""{"sensitivity":2,"hid_x":9000}"""));assertEquals(before,config) }
    @Test fun logicalModeSwitchNeedsNoRestart() { val r=action("SWITCH_MODE","""{"mode":"mouse"}""");success(r);assertEquals(false,r["data"]!!.jsonObject["restartRequired"]!!.jsonPrimitive.boolean) }
    @Test fun statusWorksWithoutBluetooth() { out.online=false;val r=action("GET_STATUS");success(r);assertEquals("B",r["data"]!!.jsonObject["target"]!!.jsonPrimitive.content) }
    @Test fun configMasksSecrets() {
        config=config.copy(mqttPassword="dummy_secret-password",token="dummy_private-token-01234567890123456789")
        val r=action("GET_CONFIG");assertFalse(r.toString().contains("dummy_secret-password"));assertFalse(r.toString().contains("dummy_private-token"))
    }
    @Test fun remoteNetworkConfigRequiresSeparateEndpoint() { fail(action("SET_CONFIG","""{"mqtt":{"uri":"tcp://example.com:1883"}}""")) }
    @Test fun unknownCommandAndDisabledCameraAreExplicitFailures() { fail(action("BOGUS",id="1"));fail(action("CAPTURE",id="2")) }
    @Test fun invalidJsonAndProtocolRejected() { fail(execute("{broken"));fail(execute("""{"version":"9","action":"HOME"}""")) }
    @Test fun negativeOrExcessiveDurationRejectedBeforeInput() { fail(action("TAP","""{"x":1,"y":1,"duration":-1}"""));assertTrue(out.active().isEmpty()) }
    @Test fun cancelledSwipeReleasesAllReportsAndDoesNotReplay()=runBlocking {
        var pauses=0
        val engine=CommandEngine(out,{config},{config=it},{JsonObject(emptyMap())},pause={if(++pauses==3) throw CancellationException()})
        val raw="""{"id":"cancel","action":"TAP","params":{"x":200,"y":200}}"""
        try { engine.execute(raw);org.junit.Assert.fail("Expected cancellation") } catch(_: CancellationException) {}
        assertEquals(listOf(5 to listOf(0,0,8,0,0,0,0,0,0),1 to listOf(0,0),2 to listOf(0,0,0,0),3 to listOf(0,0)),out.reports.takeLast(4))
        val before=out.active().size;val r=engine.execute(raw);fail(r);assertEquals(before,out.active().size)
    }
    @Test fun brokerValidationRequiresExplicitPortAndNoEmbeddedCredentials() {
        listOf("http://localhost:1883","tcp://user:pass@host:1883","ssl://host","tcp://host:1883/path").forEach { uri ->
            assertThrows(IllegalArgumentException::class.java) { config.copy(mqttUri=uri).validate() }
        }
        config.copy(mqttUri="ssl://broker.example.com:8883").validate()
    }
    @Test fun secretsPersistOnlyWhenExplicitlyRequested() { assertFalse(config.json().containsKey("token"));assertEquals(config,AutomationConfig().merge(config.json(true),true)) }
    @Test fun failedCommandCacheIsBoundedToo() {
        fail(action("BOGUS",id="old"))
        repeat(128) { fail(action("BOGUS",id="failure-$it")) }
        success(action("STATUS",id="old"))
    }

    @Test fun modifierIsReleasedAfterTheOrdinaryKey() {
        success(action("KEY_COMBO","""{"key":"A","modifiers":["shift"]}"""))
        val reports=out.reports.filter { it.first==1 }.map { it.second }
        val down=reports.indexOf(listOf(2,4))
        val keyUp=reports.withIndex().first { it.index>down && it.value==listOf(2,0) }.index
        val modifierUp=reports.withIndex().first { it.index>keyUp && it.value==listOf(0,0) }.index
        assertTrue(down>=0 && keyUp>down && modifierUp>keyUp)
    }

}
