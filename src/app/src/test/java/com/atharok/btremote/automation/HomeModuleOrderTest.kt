// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import org.junit.Assert.*
import org.junit.Test

class HomeModuleOrderTest {
    @Test fun repairsUnknownLegacyAndDuplicateEntriesWithoutLosingNewModules() {
        val result = HomeModuleOrder.decode("TV,MANUAL,TV,unknown,AI")
        assertEquals(listOf(PhoneControlMode.TV, PhoneControlMode.AI), result.take(2))
        assertEquals(HomeModuleOrder.defaults.toSet(), result.toSet())
        assertEquals(8, result.size)
        assertEquals(HomeModuleOrder.defaults, HomeModuleOrder.decode(null))
    }
    @Test fun movesByStableIdentityAndRoundTrips() {
        val result = HomeModuleOrder.move(HomeModuleOrder.defaults, "AI", "KEY_MOUSE")
        assertEquals(PhoneControlMode.AI, result[3])
        assertEquals(8, result.distinct().size)
        assertEquals(result, HomeModuleOrder.decode(HomeModuleOrder.encode(result)))
        assertEquals(result, HomeModuleOrder.move(result, "unknown", "AI"))
        assertEquals(result, HomeModuleOrder.move(result, "AI", "AI"))
    }
}
