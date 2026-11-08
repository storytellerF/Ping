package com.storyteller_f.ping.shader

import org.junit.Assert.*
import org.junit.Test

class OutputHandoffTest {
    private class Output(val name: String, var valid: Boolean = true)
    private val events = mutableListOf<String>()
    private val handoff = OutputHandoff<Output>(
        acquire = { it.valid },
        attach = { events += "attach:${it?.name}" },
        release = { events += "release:${it.name}" },
    )

    @Test fun replacementAttachesBeforeReleasingPreviousSurface() {
        val first = Output("first")
        val second = Output("second")
        handoff.offer(first)
        events.clear()
        handoff.offer(second)
        assertEquals(listOf("attach:second", "release:first"), events)
        assertSame(second, handoff.current)
    }

    @Test fun delayedRevocationDoesNotDetachReplacement() {
        val first = Output("first")
        val second = Output("second")
        handoff.offer(first)
        handoff.offer(second)
        events.clear()
        handoff.revoke(first)
        assertTrue(events.isEmpty())
        assertSame(second, handoff.current)
    }

    @Test fun invalidatedBeforeDeliveryIsReleasedWithoutAttachment() {
        handoff.offer(Output("stale", valid = false))
        assertEquals(listOf("release:stale"), events)
        assertNull(handoff.current)
    }

    @Test fun revocationDetachesBeforeReleaseExactlyOnce() {
        val output = Output("current")
        handoff.offer(output)
        events.clear()
        handoff.revoke(output)
        handoff.revoke(output)
        assertEquals(listOf("attach:null", "release:current"), events)
        assertNull(handoff.current)
    }

    @Test fun shutdownReleasesCurrentAndLateDeliveryExactlyOnce() {
        val output = Output("current")
        handoff.offer(output)
        events.clear()
        handoff.close()
        handoff.close()
        handoff.revoke(output)
        handoff.offer(Output("late"))
        assertEquals(listOf("attach:null", "release:current", "release:late"), events)
        assertNull(handoff.current)
    }
}
