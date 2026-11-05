// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import com.atharok.btremote.common.utils.ConsumerReportCompatibility
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/** Exercise automation commands through the same consumer router used by BluetoothHidCore. */
class AutomationMediaCompatibilityTest {
    @Test fun automationMediaCommandsReachBitmapReportsAndReleaseBothPaths() = runBlocking {
        val sent=mutableListOf<Pair<Int,List<Int>>>()
        val output=object: HidOutput {
            override fun connected()=true
            override fun report(id: Int,bytes: ByteArray): Boolean {
                ConsumerReportCompatibility.route(id,bytes).forEach { report ->
                    sent+=report.id to report.bytes.map { it.toInt() and 255 }
                }
                return true
            }
        }
        val config=AutomationConfig()
        val engine=CommandEngine(output,{config},{},{JsonObject(emptyMap())},pause={})
        val masks=mapOf("PLAY_PAUSE" to listOf(1,0),"NEXT_TRACK" to listOf(2,0),"PREV_TRACK" to listOf(4,0),
            "MUTE" to listOf(64,0),"VOLUME_UP" to listOf(128,0),"VOLUME_DOWN" to listOf(0,1))
        for((action,mask) in masks) {
            sent.clear()
            val result=engine.execute("{\"id\":\"$action\",\"action\":\"$action\"}")
            assertEquals("SUCCESS",result["status"]!!.jsonPrimitive.content)
            assertTrue("$action must use Report4",sent.contains(4 to mask))
            assertFalse(sent.any { it.first==3 && it.second.any { byte -> byte!=0 } })
            assertEquals(listOf(3 to listOf(0,0),4 to listOf(0,0)),sent.takeLast(2))
        }
        sent.clear()
        engine.execute("{\"id\":\"home\",\"action\":\"HOME\"}")
        assertTrue(sent.contains(3 to listOf(0x23,2)))
    }
    @Test fun rejectedMediaPressStillReleasesTheNewReport() = runBlocking {
        val sent=mutableListOf<Pair<Int,List<Int>>>()
        val output=object: HidOutput {
            override fun connected()=true
            override fun report(id: Int,bytes: ByteArray): Boolean {
                val reports=ConsumerReportCompatibility.route(id,bytes)
                reports.forEach { sent+=it.id to it.bytes.map { byte -> byte.toInt() and 255 } }
                return reports.none { it.id==4 && it.bytes.any { byte -> byte.toInt()!=0 } }
            }
        }
        val config=AutomationConfig()
        val engine=CommandEngine(output,{config},{},{JsonObject(emptyMap())},pause={})
        val result=engine.execute("{\"action\":\"VOLUME_UP\"}")
        assertEquals("HID_UNAVAILABLE",result["code"]!!.jsonPrimitive.content)
        assertEquals(listOf(3 to listOf(0,0),4 to listOf(0,0)),sent.takeLast(2))
    }
}
