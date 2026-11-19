package com.humming.keyboard

import dev.patrickgold.florisboard.lib.devtools.Devtools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BoundedReadTest {
    @Test
    fun stopsOneBytePastTheLimit() {
        assertEquals(65, BoundedRead.read(ByteArrayInputStream(ByteArray(100)), 64).size)
        assertEquals(64, BoundedRead.read(ByteArrayInputStream(ByteArray(64)), 64).size)
        assertEquals(0, BoundedRead.read(ByteArrayInputStream(ByteArray(0)), 64).size)
    }

    @Test
    fun anEndlessStreamIsCut() {
        val endless = object : InputStream() { override fun read(): Int = 0 }
        assertEquals(65, BoundedRead.read(endless, 64).size)
    }

    @Test
    fun theUpdaterUsesIt() {
        val t = File("src/main/kotlin/com/humming/keyboard/AppUpdate.kt").readText()
        assertTrue(t.contains("BoundedRead.read(it, maxBytes)"))
    }

    @Test
    fun logcatCapsItsOutputAndDestroysTheProcess() {
        val process = LogProcess(ByteArrayInputStream(ByteArray(100) { 'x'.code.toByte() }))
        val text = Devtools.collectLogcat(process, maxBytes = 16)
        assertTrue(text.startsWith("x".repeat(16) + "\n"))
        assertTrue(text.contains("truncated at 16 bytes"))
        assertTrue(process.destroyed)
    }

    @Test
    fun logcatReportsProcessFailuresInsteadOfAnApparentlySuccessfulEmptyLog() {
        val process = LogProcess(ByteArrayInputStream("permission denied".toByteArray()), code = 7)
        val text = Devtools.collectLogcat(process)
        assertTrue(text.contains("permission denied"))
        assertTrue(text.contains("failed with exit code 7"))
        assertTrue(process.destroyed)
    }

    @Test
    fun logcatTimesOutEvenWhenItsOutputStreamNeverEnds() {
        val closed = CountDownLatch(1)
        val input = object : InputStream() {
            override fun read(): Int {
                while (closed.count > 0) {
                    try {
                        closed.await()
                    } catch (_: InterruptedException) {}
                }
                return -1
            }
            override fun close() {
                closed.countDown()
            }
        }
        val process = LogProcess(input)
        val text = Devtools.collectLogcat(process, timeoutMs = 50)
        assertTrue(text.contains("timed out"))
        assertTrue(process.destroyed)
        assertEquals(0L, closed.count)
    }

    private class LogProcess(private val input: InputStream, private val code: Int = 0) : Process() {
        var destroyed = false
            private set
        private val output = ByteArrayOutputStream()
        private val error = ByteArrayInputStream(ByteArray(0))
        override fun getInputStream() = input
        override fun getOutputStream() = output
        override fun getErrorStream() = error
        override fun waitFor() = code
        override fun waitFor(timeout: Long, unit: TimeUnit) = true
        override fun exitValue() = code
        override fun destroy() {
            destroyed = true
        }
    }
}
