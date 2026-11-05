// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.common

import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardTextSender
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KeyboardTextSenderTest {
    private fun encode(character: Char) = byteArrayOf(0, (character - 'a' + 4).toByte())

    @Test fun normalTextAndEnterFinishWithReleasedKeyboard() = runBlocking {
        val reports = mutableListOf<List<Byte>>()
        val sender = KeyboardTextSender({ reports += it.toList(); true }, pause = {})
        assertTrue(sender.send("ab", ::encode, true))
        assertEquals(listOf(4, 5, 0x28), reports.filter { it[1] != 0.toByte() }.map { it[1].toInt() })
        assertEquals(listOf<Byte>(0, 0), reports.last())
    }

    @Test fun cancellingWhileAKeyIsDownReleasesItAndStopsFollowingCharacters() = runBlocking {
        val reports = mutableListOf<List<Byte>>()
        val pressed = CompletableDeferred<Unit>()
        val sender = KeyboardTextSender({ reports += it.toList(); true }, pause = {
            pressed.complete(Unit)
            awaitCancellation()
        })
        val job = launch { sender.send("ab", ::encode, true) }
        withTimeout(3000) { pressed.await(); job.cancelAndJoin() }
        assertEquals(listOf(listOf<Byte>(0, 4), listOf<Byte>(0, 0)), reports)
    }

    @Test fun aKeyDownFinishingAfterCancellationStillGetsItsOwnFinalRelease() = runBlocking {
        val reports = Collections.synchronizedList(mutableListOf<List<Byte>>())
        val started = CountDownLatch(1)
        val finishKeyDown = CountDownLatch(1)
        val sender = KeyboardTextSender({ report ->
            if (report[1] != 0.toByte()) {
                started.countDown()
                check(finishKeyDown.await(3, TimeUnit.SECONDS))
            }
            reports += report.toList()
            true
        })
        val job = launch(Dispatchers.Default) { sender.send("ab", ::encode, true) }
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS))
            job.cancel()
            reports += listOf<Byte>(0, 0) // Immediate UI cleanup can precede the blocked HID call.
            finishKeyDown.countDown()
            withTimeout(3000) { job.join() }
            assertEquals(listOf(listOf<Byte>(0, 0), listOf<Byte>(0, 4), listOf<Byte>(0, 0)), reports.toList())
        } finally {
            finishKeyDown.countDown()
            job.cancelAndJoin()
        }
    }

    @Test fun failedDeliveryDoesNotContinueTextOrSubmitEnter() = runBlocking {
        val reports = mutableListOf<List<Byte>>()
        val sender = KeyboardTextSender({ report -> reports += report.toList(); report[1] == 0.toByte() }, pause = {})
        assertFalse(sender.send("ab", ::encode, true))
        assertEquals(listOf(listOf<Byte>(0, 4), listOf<Byte>(0, 0)), reports)
    }
}
