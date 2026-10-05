package com.zaffox.discordwear.api

import java.util.UUID

object ClientUniqueMetadata {
    val currentLaunchSignature: String = newLaunchSignature()

    fun newLaunchSignature(): String {
        val bits =
            "00000000100000000001000000010000000010000001000000001000000000000010000010000001000000000100000000000001000000000000100000000000"

        val randomUuid = UUID.randomUUID()

        val maskHigh = bits.substring(0, 64).toULong(2)
        val maskLow = bits.substring(64).toULong(2)

        val high = randomUuid.mostSignificantBits.toULong() and maskHigh.inv()
        val low = randomUuid.leastSignificantBits.toULong() and maskLow.inv()

        val result = UUID(
            high.toLong(),
            low.toLong()
        )

        return result.toString()
    }
}
