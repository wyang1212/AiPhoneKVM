// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import android.bluetooth.BluetoothProfile
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class DeviceEntryControllerTest {
    private val a = "00:11:22:33:44:55"
    private val b = "AA:BB:CC:DD:EE:FF"
    private fun connected(address: String) = DeviceHidConnectionState(BluetoothProfile.STATE_CONNECTED, "same name", address)
    @Test fun existingSelectedConnectionIsReusedWithoutReconnect() = runBlocking {
        val state = MutableStateFlow(connected(a)); var commands = 0
        DeviceEntryController(state, { commands++; false }, { commands++; false }).select(a)
        assertEquals(0, commands)
    }
    @Test fun anotherDeviceIsDisconnectedBeforeConnectingTheSelectedOne() = runBlocking {
        val state = MutableStateFlow(connected(a)); val events = mutableListOf<String>()
        val controller = DeviceEntryController(state, { address ->
            events += "connect:$address"; state.value = connected(address); true
        }, { events += "disconnect"; state.value = DeviceHidConnectionState(); true })
        controller.select(b)
        assertEquals(listOf("disconnect", "connect:$b"), events)
        assertTrue(state.value.isConnectedTo(b))
    }
    @Test fun anUnexpectedHostCannotCompleteSelection() = runBlocking {
        val state = MutableStateFlow(DeviceHidConnectionState()); var returned = false
        val controller = DeviceEntryController(state, { true }, { true }, timeoutMs = 2000)
        val incoming = launch {
            delay(20); state.value = connected(b)
            delay(20); assertFalse(returned)
            state.value = connected(a)
        }
        controller.select(a); returned = true; incoming.join()
        assertTrue(state.value.isConnectedTo(a))
    }
    @Test fun failedConnectIsReportedWithoutBlindDisconnectRetry() = runBlocking {
        var connects = 0; var disconnects = 0
        val state = MutableStateFlow(DeviceHidConnectionState())
        val controller = DeviceEntryController(state, { connects++; false }, { disconnects++; true })
        try { controller.select(a); fail("Expected failed connection") } catch (_: IllegalStateException) { }
        assertEquals(1, connects); assertEquals(0, disconnects)
    }
    @Test fun timeoutCancelsTheConnectionAttempt() = runBlocking {
        val state = MutableStateFlow(DeviceHidConnectionState()); var disconnected = false
        val controller = DeviceEntryController(state, { address ->
            state.value = DeviceHidConnectionState(BluetoothProfile.STATE_CONNECTING, "B", address); true
        }, { disconnected = true; state.value = DeviceHidConnectionState(); true }, timeoutMs = 60)
        try { controller.select(a); fail("Expected timeout") } catch (_: TimeoutCancellationException) { }
        assertTrue(disconnected)
    }
    @Test fun cancelStopsOnlyThePendingOutboundAttempt() = runBlocking {
        val state = MutableStateFlow(DeviceHidConnectionState()); var disconnected = false
        val controller = DeviceEntryController(state, { address ->
            state.value = DeviceHidConnectionState(BluetoothProfile.STATE_CONNECTING, "B", address); true
        }, { disconnected = true; true })
        val job = launch { controller.select(a) }; yield(); job.cancelAndJoin()
        assertTrue(disconnected)
    }
    @Test fun hostInitiatedConnectionInProgressIsNotRestarted() = runBlocking {
        val state = MutableStateFlow(DeviceHidConnectionState(BluetoothProfile.STATE_CONNECTING, "B", a)); var commands = 0
        val incoming = launch { delay(20); state.value = connected(a) }
        DeviceEntryController(state, { commands++; false }, { commands++; false }).select(a)
        incoming.join(); assertEquals(0, commands)
    }
    @Test fun invalidAddressHasNoBluetoothSideEffects() = runBlocking {
        var commands = 0
        val controller = DeviceEntryController(MutableStateFlow(DeviceHidConnectionState()), { commands++; false }, { commands++; false })
        try { controller.select("not a device"); fail("Expected invalid address") } catch (_: IllegalArgumentException) { }
        assertEquals(0, commands)
    }
    @Test fun targetIdentityRequiresConnectedStateAndAddressRatherThanName() {
        assertTrue(connected(b).isConnectedTo(b.lowercase()))
        assertFalse(connected(a).isConnectedTo(b))
        assertFalse(DeviceHidConnectionState(BluetoothProfile.STATE_DISCONNECTED, "same name", a).isConnectedTo(a))
        assertFalse(DeviceHidConnectionState(BluetoothProfile.STATE_DISCONNECTING, "same name", a).isConnectedTo(a))
        assertFalse(DeviceHidConnectionState(BluetoothProfile.STATE_CONNECTED, "same name").isConnectedTo(""))
    }
}
