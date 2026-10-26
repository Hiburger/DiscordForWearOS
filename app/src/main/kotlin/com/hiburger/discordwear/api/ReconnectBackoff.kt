package com.hiburger.discordwear.api

/**
 * Exponential reconnect backoff for the gateway socket.
 *
 * Without it, a watch that is offline (away from the phone, Wi-Fi gone at
 * night) retried the connection every 5 seconds around the clock: DNS + TLS
 * handshake + radio wake, hundreds of times per hour. The delay now doubles
 * per failed attempt up to a 15 minute cap, and jitter keeps retries from
 * firing in lockstep with heartbeat timers.
 */
object ReconnectBackoff {
    private const val BASE_MS = 5_000L
    private const val MAX_MS = 15 * 60 * 1000L

    /** Pure schedule for attempt n (1-based): 5s, 10s, 20s, 40s, ... capped. */
    fun delayMs(attempt: Int): Long {
        if (attempt <= 1) return BASE_MS
        var ms = BASE_MS
        repeat(minOf(attempt, 30) - 1) {
            ms = (ms * 2).coerceAtMost(MAX_MS)
        }
        return ms
    }

    /** Schedule delay shrunk by a random factor in [0.5, 1]. */
    fun jitteredDelayMs(attempt: Int, random: Double = Math.random()): Long {
        val factor = 0.5 + random.coerceIn(0.0, 1.0) * 0.5
        return (delayMs(attempt) * factor).toLong().coerceAtLeast(1L)
    }
}
