package com.hiburger.discordwear.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectBackoffTest {

    @Test
    fun `first attempt waits the base interval`() {
        assertEquals(5_000L, ReconnectBackoff.delayMs(1))
    }

    @Test
    fun `delay doubles every attempt`() {
        assertEquals(10_000L, ReconnectBackoff.delayMs(2))
        assertEquals(20_000L, ReconnectBackoff.delayMs(3))
        assertEquals(40_000L, ReconnectBackoff.delayMs(4))
    }

    @Test
    fun `delay is capped at fifteen minutes`() {
        assertEquals(900_000L, ReconnectBackoff.delayMs(10))
        assertEquals(900_000L, ReconnectBackoff.delayMs(30))
        assertEquals(900_000L, ReconnectBackoff.delayMs(1_000))
    }

    @Test
    fun `non-positive attempts fall back to the base interval`() {
        assertEquals(5_000L, ReconnectBackoff.delayMs(0))
        assertEquals(5_000L, ReconnectBackoff.delayMs(-3))
    }

    @Test
    fun `jitter stays within half to full schedule`() {
        val schedule = ReconnectBackoff.delayMs(3)
        repeat(100) { i ->
            val delay = ReconnectBackoff.jitteredDelayMs(3, random = i / 100.0)
            assertTrue(
                "delay $delay outside [${schedule / 2}, $schedule]",
                delay >= schedule / 2 && delay <= schedule
            )
        }
    }
}
