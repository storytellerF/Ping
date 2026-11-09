package com.storyteller_f.ping.shader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OutputLeaseTest {
    @Test fun retirementBeforeDeliveryRejectsAttachmentAndAllowsRelease() {
        val lease = OutputLease()
        lease.retire()
        assertFalse(lease.acquire())
        assertFalse(lease.isValid)
        assertTrue(lease.canRelease)
    }

    @Test fun retirementWaitsForAttachedProducerToDetach() {
        val lease = OutputLease()
        assertTrue(lease.acquire())
        lease.retire()
        assertFalse(lease.isValid)
        assertFalse(lease.canRelease)
        lease.detach()
        assertTrue(lease.canRelease)
    }

    @Test fun detachingBeforeContextDestructionAllowsRetirement() {
        val lease = OutputLease()
        lease.acquire()
        lease.detach()
        assertFalse(lease.canRelease)
        lease.retire()
        assertTrue(lease.canRelease)
    }

    @Test fun concurrentRetirementNeverReleasesAnAcquiredProducer() = runBlocking<Unit> {
        repeat(100) {
            val lease = OutputLease()
            val acquire = async(Dispatchers.Default) { lease.acquire() }
            val retire = async(Dispatchers.Default) { lease.retire() }
            val attached = acquire.await()
            retire.await()
            assertFalse(lease.isValid)
            assertEquals(!attached, lease.canRelease)
            lease.detach()
            assertTrue(lease.canRelease)
        }
    }
}
