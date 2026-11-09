package com.storyteller_f.ping.wallpaper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WallpaperTestEnvironmentTest {
    private suspend fun failure(action: suspend () -> Unit): Throwable {
        val error = try { action(); null } catch (failure: Throwable) { failure }
        return checkNotNull(error) { "Expected restoration to report its failure" }
    }

    @Test fun stopTimeoutRestoresSelectionAndComponentBeforeRethrowingOriginal() = runBlocking<Unit> {
        val events = mutableListOf<String>()
        var timeout: TimeoutCancellationException? = null
        val environment = WallpaperTestEnvironment(
            stop = {
                events += "stop"
                try { withTimeout(1) { awaitCancellation() } }
                catch (error: TimeoutCancellationException) { timeout = error; throw error }
            },
            restoreSelection = { events += "selection" },
            restoreComponent = { events += "component" },
        )
        val reported = failure { environment.restore() }
        assertNotNull(timeout)
        assertSame(timeout, reported)
        assertEquals(listOf("stop", "selection", "component"), events)
        assertTrue(environment.selectionRestored)
    }

    @Test fun subsequentFailuresAreSuppressedInOrderWithoutSkippingComponent() = runBlocking<Unit> {
        val first = IOException("stop failed")
        val second = IOException("selection failed")
        val third = IOException("component failed")
        val events = mutableListOf<String>()
        val environment = WallpaperTestEnvironment(
            stop = { events += "stop"; throw first },
            restoreSelection = { events += "selection"; throw second },
            restoreComponent = { events += "component"; throw third },
        )
        val reported = failure { environment.restore() }
        assertSame(first, reported)
        assertArrayEquals(arrayOf(second, third), reported.suppressed)
        assertEquals(listOf("stop", "selection", "component"), events)
        assertFalse("Failed selection restore must retain fixtures", environment.selectionRestored)
    }

    @Test fun selectionFailureStillAttemptsComponentAndRetainsFixtures() = runBlocking<Unit> {
        val original = IOException("selection unavailable")
        var restoredComponent = false
        val environment = WallpaperTestEnvironment({}, { throw original }, { restoredComponent = true })
        assertSame(original, failure { environment.restore() })
        assertTrue(restoredComponent)
        assertFalse(environment.selectionRestored)
    }

    @Test fun componentFailureIsReportedAfterSuccessfulSelectionRestore() = runBlocking<Unit> {
        val original = IOException("component rejected")
        val environment = WallpaperTestEnvironment({}, {}, { throw original })
        assertSame(original, failure { environment.restore() })
        assertTrue(environment.selectionRestored)
        assertTrue(original.suppressed.isEmpty())
    }

    @Test fun successfulRestoreRunsAllStagesInOrder() = runBlocking<Unit> {
        val events = mutableListOf<String>()
        val environment = WallpaperTestEnvironment(
            { events += "stop" }, { events += "selection" }, { events += "component" },
        )
        environment.restore()
        assertEquals(listOf("stop", "selection", "component"), events)
        assertTrue(environment.selectionRestored)
    }

    @Test fun stopCancellationIsRethrownAfterRestoration() = runBlocking<Unit> {
        val cancellation = CancellationException("stop cancelled")
        val events = mutableListOf<String>()
        val environment = WallpaperTestEnvironment(
            { throw cancellation }, { events += "selection" }, { events += "component" },
        )
        assertSame(cancellation, failure { environment.restore() })
        assertEquals(listOf("selection", "component"), events)
    }

    @Test fun cancelledCallerStillRestoresEnvironmentAndRemainsCancelled() = runBlocking<Unit> {
        val events = mutableListOf<String>()
        val environment = WallpaperTestEnvironment(
            { events += "stop" }, { events += "selection" }, { events += "component" },
        )
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { environment.restore() }
        }
        task.cancelAndJoin()
        assertTrue(task.isCancelled)
        assertEquals(listOf("stop", "selection", "component"), events)
        assertTrue(environment.selectionRestored)
    }
}
