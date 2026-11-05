// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.common.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ConsumerReportCompatibilityTest {
    @Test fun volumeUsesTheConsumerBitmapInsteadOfTheIgnoredUsageArray() {
        assertMedia(0xe9, 0x80, 0x00)
        assertMedia(0xea, 0x00, 0x01)
        assertMedia(0xe2, 0x40, 0x00)
    }

    @Test fun allPlaybackControlsUseDistinctConsumerBits() {
        assertMedia(0xcd, 0x01, 0x00)
        assertMedia(0xb5, 0x02, 0x00)
        assertMedia(0xb6, 0x04, 0x00)
        assertMedia(0xb7, 0x08, 0x00)
        assertMedia(0xb4, 0x10, 0x00)
        assertMedia(0xb3, 0x20, 0x00)
    }

    @Test fun brightnessBitsAreNotTruncatedToOneByte() {
        assertMedia(0x6f, 0x00, 0x02)
        assertMedia(0x70, 0x00, 0x04)
    }

    @Test fun keyUpReleasesBothConsumerReportPaths() {
        val result = ConsumerReportCompatibility.route(REMOTE_REPORT_ID, byteArrayOf(0, 0))
        assertEquals(listOf(REMOTE_REPORT_ID, MEDIA_REPORT_ID), result.map { it.id })
        result.forEach { assertArrayEquals(byteArrayOf(0, 0), it.bytes) }
    }

    @Test fun tvNavigationAndExtendedConsumerUsagesRetainTheOriginalPath() {
        listOf(byteArrayOf(0x40, 0), byteArrayOf(0x41, 0), byteArrayOf(0xe9.toByte(), 1)).forEach {
            val result = ConsumerReportCompatibility.route(REMOTE_REPORT_ID, it).single()
            assertEquals(REMOTE_REPORT_ID, result.id)
            assertArrayEquals(it, result.bytes)
        }
    }

    @Test fun keyboardMouseAndAlreadyEncodedMediaAreNotReinterpreted() {
        listOf(KEYBOARD_REPORT_ID, MOUSE_REPORT_ID, MEDIA_REPORT_ID).forEach { id ->
            val bytes = byteArrayOf(0xe9.toByte(), 0)
            val result = ConsumerReportCompatibility.route(id, bytes).single()
            assertEquals(id, result.id)
            assertArrayEquals(bytes, result.bytes)
        }
    }

    @Test fun malformedConsumerReportsAreNotConvertedIntoMediaPresses() {
        listOf(byteArrayOf(), byteArrayOf(0xe9.toByte()), byteArrayOf(0xe9.toByte(), 0, 1)).forEach {
            val result = ConsumerReportCompatibility.route(REMOTE_REPORT_ID, it).single()
            assertEquals(REMOTE_REPORT_ID, result.id)
            assertArrayEquals(it, result.bytes)
        }
    }

    @Test fun descriptorMatchesTheBitmapVerifiedOnTheHonorDevice() {
        // This fixture was sent to the real device before integrating the compatibility path.
        val hex = "050c0901a1018504150025017501950b09cd09b509b609b709b409b309e209e909ea096f09708102750595018101c0"
        val verified = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(verified, ConsumerReportCompatibility.descriptor)
        assertArrayEquals(verified, bluetoothHidDescriptor.dropLast(GamepadReport.descriptor.size).takeLast(verified.size).toByteArray())
        assertEquals(168, bluetoothHidDescriptor.size - GamepadReport.descriptor.size)
    }

    private fun assertMedia(usage: Int, low: Int, high: Int) {
        val result = ConsumerReportCompatibility.route(REMOTE_REPORT_ID, byteArrayOf(usage.toByte(), 0)).single()
        assertEquals(MEDIA_REPORT_ID, result.id)
        assertArrayEquals(byteArrayOf(low.toByte(), high.toByte()), result.bytes)
    }
}
