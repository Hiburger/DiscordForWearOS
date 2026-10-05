package com.zaffox.discordwear.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.UUID

class ClientUniqueMetadataTest {

    // Client-mod detection bit positions from the reference client-properties spec
    private val detectionBits = setOf(11L, 24L, 38L, 48L, 55L, 61L, 75L, 84L, 91L, 100L, 108L, 119L)

    private fun signatureBits(signature: String): BigInteger {
        val uuid = UUID.fromString(signature)
        val bytes = ByteBuffer.allocate(16)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
        return BigInteger(1, bytes)
    }

    @Test
    fun `launch signature is a valid version 4 UUID`() {
        val uuid = UUID.fromString(ClientUniqueMetadata.currentLaunchSignature)
        assertEquals(4, uuid.version())
    }

    @Test
    fun `launch signature has every client-mod detection bit cleared`() {
        val bits = signatureBits(ClientUniqueMetadata.currentLaunchSignature)
        detectionBits.forEach { bit -> assertFalse(bits.testBit(bit.toInt())) }
    }

    @Test
    fun `generated signatures always clear detection bits`() {
        repeat(64) {
            val bits = signatureBits(ClientUniqueMetadata.newLaunchSignature())
            detectionBits.forEach { bit -> assertFalse(bits.testBit(bit.toInt())) }
        }
    }
}
