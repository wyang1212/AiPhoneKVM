// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class ControlSessionTest {
    private val address = "AA:BB:CC:DD:EE:FF"
    private val oldToken = "dummy_previous-pairing-token-0123456789"
    @Test fun firstTargetBindingInvalidatesAnUnboundCompanion() {
        val token=nextCapturePairingToken(null,address,oldToken)
        assertNotEquals(oldToken,token);assertTrue(token.matches(Regex("[0-9a-f]{32}")))
    }
    @Test fun reopeningTheSameTargetPreservesItsScreenshotPairing() {
        assertEquals(oldToken,nextCapturePairingToken(address.lowercase(),address,oldToken))
    }
    @Test fun aDifferentTargetCannotUseThePreviousCompanionCode() {
        assertNotEquals(oldToken,nextCapturePairingToken("00:11:22:33:44:55",address,oldToken))
    }
    @Test fun commandIdsAreScopedToOneControlSession() = runBlocking {
        val sent=mutableListOf<List<Int>>()
        val output=object: HidOutput {
            override fun connected()=true
            override fun report(id: Int,bytes: ByteArray): Boolean {
                if(id==1 && bytes.any { it.toInt()!=0 }) sent+=bytes.map { it.toInt() and 255 }
                return true
            }
        }
        val config=AutomationConfig()
        val engine=CommandEngine(output,{config},{},{JsonObject(emptyMap())},pause={})
        val first="""{"id":"session-command","action":"KEY_PRESS","params":{"key":"A"}}"""
        engine.execute(first);val count=sent.size;engine.execute(first)
        assertEquals(count,sent.size)
        engine.resetSession()
        val next=engine.execute("""{"id":"session-command","action":"KEY_PRESS","params":{"key":"B"}}""")
        assertEquals("SUCCESS",next["status"]!!.jsonPrimitive.content)
        assertTrue(sent.size>count);assertEquals(listOf(0,5),sent.last())
    }
}
