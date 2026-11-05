// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.domain.entities.remoteInput.keyboard

import com.atharok.btremote.common.utils.DELAY_BETWEEN_KEY_PRESSES_IN_MILLIS
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** The caller binds output to one captured HID target for the complete text operation. */
internal class KeyboardTextSender(
    private val output: (ByteArray) -> Boolean,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    suspend fun send(text: String, encode: (Char) -> ByteArray, shouldSendEnter: Boolean): Boolean {
        var successful = true
        suspend fun stroke(report: ByteArray, waitAfterRelease: Boolean = true): Boolean {
            currentCoroutineContext().ensureActive()
            if (!output(report)) return false
            pause(DELAY_BETWEEN_KEY_PRESSES_IN_MILLIS)
            currentCoroutineContext().ensureActive()
            if (!output(byteArrayOf(0, 0))) return false
            if (waitAfterRelease) pause(DELAY_BETWEEN_KEY_PRESSES_IN_MILLIS)
            return true
        }
        try {
            for (character in text) {
                if (!stroke(encode(character))) { successful = false; break }
            }
            if (successful && shouldSendEnter) successful = stroke(byteArrayOf(0, 0x28), false)
        } finally {
            // No suspension: a late key-down must be followed by release even after cancellation.
            successful = runCatching { output(byteArrayOf(0, 0)) }.getOrDefault(false) && successful
        }
        return successful
    }
}
