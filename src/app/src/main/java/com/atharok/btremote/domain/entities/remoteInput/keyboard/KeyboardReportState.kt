package com.atharok.btremote.domain.entities.remoteInput.keyboard

/** The existing HID descriptor carries one key usage and one modifier mask. */
class KeyboardReportState {
    private val modifiers = mutableSetOf<Byte>()
    private var key: Byte = 0

    fun press(value: Byte, modifier: Boolean): ByteArray {
        if (modifier) modifiers.add(value) else key = value
        return report()
    }

    fun release(value: Byte, modifier: Boolean): ByteArray {
        if (modifier) modifiers.remove(value) else if (key == value) key = 0
        return report()
    }

    fun reset(): ByteArray {
        modifiers.clear()
        key = 0
        return report()
    }

    private fun report(): ByteArray = byteArrayOf(
        modifiers.fold(0) { result, value -> result or (value.toInt() and 0xff) }.toByte(), key
    )
}
