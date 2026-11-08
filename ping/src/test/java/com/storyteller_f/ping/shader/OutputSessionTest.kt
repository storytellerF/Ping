package com.storyteller_f.ping.shader

import org.junit.Assert.*
import org.junit.Test

class OutputSessionTest {
    private val events = mutableListOf<String>()
    private inner class Output(override val sourceId: Long, val name: String, var valid: Boolean = true) : ProducerOutput {
        var finishes = 0
        override val isValid: Boolean get() = valid
        override fun acquire(): Boolean = valid
        override fun finishProducerUse() { finishes++; events += "release:$name" }
    }
    private val session = OutputSession<Output> { events += "attach:${it?.name}" }.apply { selectSource(1) }

    @Test fun replacementBindsBeforeReturningOldOutput() {
        val first = Output(1, "first")
        val second = Output(2, "second")
        session.offer(first)
        session.selectSource(2)
        events.clear()
        session.offer(second)
        assertEquals(listOf("attach:second", "release:first"), events)
        assertSame(second, session.current)
    }

    @Test fun lateInvalidationCannotPauseReplacement() {
        val first = Output(1, "first")
        val second = Output(1, "second")
        session.offer(first)
        session.offer(second)
        var pauses = 0
        session.ifCurrent(first) { pauses++ }
        assertEquals(0, pauses)
        session.ifCurrent(second) { pauses++ }
        assertEquals(1, pauses)
    }

    @Test fun obsoleteSourceIsReturnedWithoutBinding() {
        session.selectSource(2)
        val old = Output(1, "old")
        session.offer(old)
        assertEquals(listOf("release:old"), events)
        assertNull(session.current)
    }

    @Test fun retiredBeforeDeliveryIsReturnedWithoutBinding() {
        session.offer(Output(1, "stale", false))
        assertEquals(listOf("release:stale"), events)
        assertNull(session.current)
    }

    @Test fun selectingNewSourceRetainsPreviousUntilReplacementArrives() {
        val first = Output(1, "first")
        session.offer(first)
        events.clear()
        session.selectSource(2)
        assertSame(first, session.current)
        assertFalse(session.hasCurrentSource)
        assertTrue(events.isEmpty())
    }

    @Test fun shutdownReturnsCurrentAndLateOutputOnce() {
        val current = Output(1, "current")
        val late = Output(1, "late")
        session.offer(current)
        events.clear()
        session.close()
        session.close()
        session.offer(late)
        assertEquals(listOf("attach:null", "release:current", "release:late"), events)
        assertEquals(1, current.finishes)
        assertEquals(1, late.finishes)
        assertNull(session.current)
    }

    @Test fun failedAttachmentReturnsCandidateAndRetainsPreviousOwnership() {
        var failing = false
        val session = OutputSession<Output> {
            if (failing) throw IllegalStateException("producer rejected output")
        }.apply { selectSource(1) }
        val current = Output(1, "current")
        val candidate = Output(1, "candidate")
        session.offer(current)
        failing = true
        assertThrows(IllegalStateException::class.java) { session.offer(candidate) }
        assertSame(current, session.current)
        assertEquals(0, current.finishes)
        assertEquals(1, candidate.finishes)
    }
    @Test fun shutdownStillReturnsOutputWhenProducerDetachReportsFailure() {
        var closing = false
        val session = OutputSession<Output> { if (closing) throw IllegalStateException("detach failed") }
            .apply { selectSource(1) }
        val current = Output(1, "current")
        val late = Output(1, "late")
        session.offer(current)
        closing = true
        assertThrows(IllegalStateException::class.java) { session.close() }
        session.close()
        session.offer(late)
        assertNull(session.current)
        assertEquals(1, current.finishes)
        assertEquals(1, late.finishes)
    }

}
