package com.storyteller_f.ping.shader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FrameSignalTest {
    @Test fun concurrentNotificationsRequestOneRender() = runBlocking {
        val signal = FrameSignal()
        val requests = (1..100).map { async(Dispatchers.Default) { signal.offer() } }.awaitAll()
        assertEquals(1, requests.count { it })
        assertTrue(signal.consume())
        assertFalse(signal.consume())
    }

    @Test fun notificationAfterConsumptionRequestsAnotherRender() {
        val signal = FrameSignal()
        assertTrue(signal.offer())
        assertTrue(signal.consume())
        assertTrue(signal.offer())
        assertTrue(signal.consume())
    }

    @Test fun closingDiscardsPendingAndLateNotifications() {
        val signal = FrameSignal()
        signal.offer()
        signal.close()
        assertFalse(signal.consume())
        assertFalse(signal.offer())
        assertTrue(signal.isClosed)
    }
}
