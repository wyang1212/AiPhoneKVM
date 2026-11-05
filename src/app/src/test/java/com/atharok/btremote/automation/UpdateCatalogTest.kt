// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import com.atharok.btremote.update.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*

class UpdateCatalogTest {
    private fun item(name:String="AiPhoneKVM-2.3-debug.apk",hash:String="a".repeat(64))=buildJsonObject {
        put("id","38fd2b89-ffa7-4f98-b2f7-d3851b5ec4eb");put("name",name);put("size",78000000);put("sha256",hash)
    }
    @Test fun selectsNumericLatestMainApkOnly() {
        val raw=JsonArray(listOf(item("AiPhoneKVM-2.9-debug.apk"),item("AiPhoneKVM-2.10-debug.apk"),
            item("AiPhoneKVM-Capture-8.0-debug.apk"),item("AiPhoneKVM-20-source.zip"),item("PhoneKVM-99-debug.apk"))).toString()
        assertEquals("2.10",UpdateCatalog.latest(raw)!!.version)
    }
    @Test fun currentAndroidDeliveriesParticipateAlongsideLegacyDebugApks() {
        val legacy=item("AiPhoneKVM-2.9-debug.apk")
        val current=item("AiPhoneKVM-2.9.3-android.apk")
        val raw=JsonArray(listOf(legacy,current,
            item("AiPhoneKVM-Capture-99.0-android.apk"),
            item("AiPhoneKVM-Capture-99.0-debug.apk"),
            item("AiPhoneKVM-99.0-source.zip"),
            item("AiPhoneKVM-99.0-release.apk"))).toString()
        val selected=UpdateCatalog.latest(raw)!!
        assertEquals("2.9.3",selected.version)
        assertEquals("AiPhoneKVM-2.9.3-android.apk",selected.name)
        assertEquals("2.9",UpdateCatalog.latest(JsonArray(listOf(legacy)).toString())!!.version)
    }
    @Test fun companionsNeverBecomeMainAppUpdates() {
        for(name in listOf("AiPhoneKVM-Capture-99.0-android.apk","AiPhoneKVM-Capture-99.0-debug.apk")) {
            assertNull(UpdateCatalog.latest(JsonArray(listOf(item(name))).toString()))
            assertTrue(runCatching { UpdateCatalog.release(item(name)) }.isFailure)
        }
    }
    @Test fun supportsCatalogEnvelopeAndEmptyList() {
        assertEquals("2.3",UpdateCatalog.latest(buildJsonObject { put("files",JsonArray(listOf(item()))) }.toString())!!.version)
        assertNull(UpdateCatalog.latest("[]"))
        assertEquals(0,UpdateCatalog.compare("2.3","2.3.0"))
        assertTrue(UpdateCatalog.compare("2.10","2.9")>0)
    }
    @Test fun rejectsInvalidIntegrityMetadata() {
        assertTrue(runCatching { UpdateCatalog.release(item(hash="wrong")) }.isFailure)
        val wrong=JsonObject(item().toMutableMap().apply { put("size",JsonPrimitive(UpdateCatalog.MAX_APK_BYTES+1)) })
        assertTrue(runCatching { UpdateCatalog.release(wrong) }.isFailure)
        val id=JsonObject(item().toMutableMap().apply { put("id",JsonPrimitive("../other")) })
        assertTrue(runCatching { UpdateCatalog.release(id) }.isFailure)
    }
    @Test fun rejectsMalformedAndOversizedCatalogs() {
        assertTrue(runCatching { UpdateCatalog.latest("not json") }.isFailure)
        assertTrue(runCatching { UpdateCatalog.latest(" ".repeat(1024*1024+1)) }.isFailure)
    }
    private val installed=ApkIdentity("vip.wyang.aiphonekvm.debug","2.2",23,setOf("trusted"))
    private fun candidate()=installed.copy(versionName="2.3",versionCode=24)
    @Test fun onlyAcceptsMatchingHigherVersionAndExistingSigner() {
        val r=UpdateCatalog.release(item());UpdateIntegrity.identity(candidate(),installed,r)
        for(c in listOf(candidate().copy(packageName="other"),candidate().copy(versionCode=23),
            candidate().copy(versionName="9.0"),candidate().copy(signerHashes=setOf("other")),candidate().copy(signerHashes=emptySet())))
            assertTrue(runCatching { UpdateIntegrity.identity(c,installed,r) }.isFailure)
    }
    @Test fun hashesExactBytes() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",UpdateIntegrity.sha256("abc".toByteArray()))
    }
    @Test fun selfHostedCatalogAndDownloadUseSameOrigin() {
        val origin="https://releases.example:8443/"
        assertEquals("https://releases.example:8443/api/files", UpdateCatalog.endpoint(origin))
        val id="38fd2b89-ffa7-4f98-b2f7-d3851b5ec4eb"
        assertEquals("https://releases.example:8443/download/$id", UpdateCatalog.downloadUrl(id, origin))
        assertThrows(IllegalArgumentException::class.java) { UpdateCatalog.downloadUrl("../escape", origin) }
        assertThrows(IllegalArgumentException::class.java) { UpdateCatalog.endpoint("http://releases.example") }
        assertTrue(runCatching { UpdateCatalog.endpoint("") }.isFailure)
    }
}
