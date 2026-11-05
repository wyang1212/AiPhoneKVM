// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.common.utils

import org.junit.Assert.*
import org.junit.Test

class HttpsOriginTest {
    @Test fun normalizesHostSlashAndDefaultPort() {
        assertEquals("https://control.example", httpsOrigin(" HTTPS://CONTROL.example:443/ "))
        assertEquals("https://control.example:8443", httpsOrigin("https://control.example:8443/"))
        assertEquals("https://[::1]:8443", httpsOrigin("https://[::1]:8443"))
    }
    @Test fun rejectsCredentialsPathsQueriesFragmentsAndInvalidPorts() {
        for (value in listOf("http://host", "https://user:pass@host", "https://host/path", "https://host?token=x",
            "https://host#token=x", "https://host:0", "https://host:65536", "https://host:", "https://", "//host", "")) {
            assertTrue(value, runCatching { httpsOrigin(value) }.isFailure)
        }
    }
}
