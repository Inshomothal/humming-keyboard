package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VoiceMemoryTest {
    @Test fun useCountNeverGoesNegative() {
        val u = UseCount()
        assertTrue(u.idle)
        u.acquire(); u.acquire()
        assertFalse(u.idle)
        u.release()
        assertEquals(1, u.count)
        u.release(); u.release()
        assertEquals(0, u.count)
        assertTrue(u.idle)
    }

    @Test fun unloadsOnlyUnderMemoryPressure() {
        assertFalse(CoreLoader.shouldUnloadOnTrim(5)) // RUNNING_MODERATE
        assertTrue(CoreLoader.shouldUnloadOnTrim(10)) // RUNNING_LOW
        assertTrue(CoreLoader.shouldUnloadOnTrim(15)) // RUNNING_CRITICAL
        assertTrue(CoreLoader.shouldUnloadOnTrim(40)) // BACKGROUND
    }

    @Test fun nothingToUnloadWhenNoModelIsLoaded() {
        assertFalse(CoreLoader.releaseIfIdle())
    }

    @Test fun teardownWaitsForEveryLeaseAndDoesNotAcceptNewUsersWhilePending() {
        val u = UseCount()
        var closed = 0
        u.acquire(); u.acquire()
        assertFalse(u.releaseWhenIdle { closed++ })
        assertTrue(u.releasePending)
        try {
            u.acquire()
            fail("must not acquire an engine awaiting teardown")
        } catch (_: IllegalStateException) { }
        u.release()
        assertEquals(0, closed)
        assertFalse(u.releaseWhenIdle { closed++ })
        u.release()
        assertEquals(1, closed)
        assertFalse(u.releasePending)
        u.release()
        assertEquals(1, closed)
        u.acquire()
        assertEquals(1, u.count)
        u.release()
    }

    @Test fun anInterruptedInferenceKeepsItsLeaseUntilTheWorkerReallyTerminates() {
        val worker = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val released = CountDownLatch(1)
        try {
            worker.execute {
                started.countDown()
                while (finish.count > 0) {
                    try { finish.await() } catch (_: InterruptedException) { }
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            worker.shutdownNow()
            afterWorkerTermination(worker) { released.countDown() }
            assertFalse(released.await(50, TimeUnit.MILLISECONDS))
            finish.countDown()
            assertTrue(released.await(5, TimeUnit.SECONDS))
            assertTrue(worker.isTerminated)
        } finally {
            finish.countDown()
            worker.shutdownNow()
            worker.awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
