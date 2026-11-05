// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.common.utils

data class HidInputReport(val id: Int, val bytes: ByteArray)

/** Explicit consumer usages avoid the large usage-array reports ignored by some HID hosts. */
object ConsumerReportCompatibility {
    private val usages = intArrayOf(
        0xcd, 0xb5, 0xb6, 0xb7, 0xb4, 0xb3, 0xe2, 0xe9, 0xea, 0x6f, 0x70
    )

    // Two-byte, one-bit-per-key consumer report. Keep the legacy TV collection alongside it.
    val descriptor: ByteArray = byteArrayOf(
        0x05, 0x0c, 0x09, 0x01, 0xa1.toByte(), 0x01,
        0x85.toByte(), MEDIA_REPORT_ID.toByte(),
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x0b
    ) + usages.flatMap { listOf(0x09.toByte(), it.toByte()) }.toByteArray() + byteArrayOf(
        0x81.toByte(), 0x02, 0x75, 0x05, 0x95.toByte(), 0x01, 0x81.toByte(), 0x01, 0xc0.toByte()
    )

    fun route(id: Int, bytes: ByteArray): List<HidInputReport> {
        if (id != REMOTE_REPORT_ID || bytes.size != 2) return listOf(HidInputReport(id, bytes))
        val usage = (bytes[0].toInt() and 0xff) or ((bytes[1].toInt() and 0xff) shl 8)
        if (usage == 0) {
            // Release both paths on key-up, cancellation, mode change and backgrounding.
            return listOf(
                HidInputReport(REMOTE_REPORT_ID, byteArrayOf(0, 0)),
                HidInputReport(MEDIA_REPORT_ID, byteArrayOf(0, 0))
            )
        }
        val bit = usages.indexOf(usage)
        if (bit < 0) return listOf(HidInputReport(id, bytes))
        val mask = 1 shl bit
        return listOf(HidInputReport(MEDIA_REPORT_ID, byteArrayOf(mask.toByte(), (mask shr 8).toByte())))
    }
}
