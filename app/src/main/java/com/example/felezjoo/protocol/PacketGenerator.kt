package com.example.felezjoo.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketGenerator {

    /**
     * Builds a real RAW_BLOCK packet with valid CRC-16-CCITT little endian.
     * Generates Protocol V2 (166 bytes) by default, or V1 (162 bytes) if specified.
     */
    fun createRawBlockPacket(
        sequence: Long,
        timestamp: Long,
        delayTicks: Int,
        sampleCount: Int = 70,
        samples: IntArray,
        flags: Int = 0,
        frequencyHz: Int = 200,
        pulseUs: Int = 150,
        version: Byte = PacketConstants.PROTOCOL_VERSION_2
    ): ByteArray {
        val isV2 = (version == PacketConstants.PROTOCOL_VERSION_2)
        val headerMetaLen = if (isV2) 16 else 12
        val payloadLen = headerMetaLen + (sampleCount * 2) + 2
        val totalLen = 6 + payloadLen + 2
        val bb = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)

        // Header (6 bytes)
        bb.put(PacketConstants.SYNC_BYTE_0)
        bb.put(PacketConstants.SYNC_BYTE_1)
        bb.put(version)
        bb.put(PacketConstants.TYPE_RAW_BLOCK)
        bb.putShort(payloadLen.toShort())

        // Payload
        bb.putInt(sequence.toInt())
        bb.putInt(timestamp.toInt())
        bb.putShort(delayTicks.toShort())
        if (isV2) {
            bb.putShort(frequencyHz.toShort())
            bb.putShort(pulseUs.toShort())
        }
        bb.putShort(sampleCount.toShort())
        for (i in 0 until sampleCount) {
            val s = if (i < samples.size) samples[i] else 0
            bb.putShort(s.toShort())
        }
        bb.putShort(flags.toShort())

        // CRC-16 over all previous bytes
        val dataBeforeCrc = bb.array()
        val crc = Crc16Ccitt.compute(dataBeforeCrc, 0, totalLen - 2)
        bb.putShort(crc.toShort())

        return bb.array()
    }

    fun createRawBlockPacketV1(
        sequence: Long,
        timestamp: Long,
        delayTicks: Int,
        sampleCount: Int = 70,
        samples: IntArray,
        flags: Int = 0
    ): ByteArray = createRawBlockPacket(
        sequence = sequence,
        timestamp = timestamp,
        delayTicks = delayTicks,
        sampleCount = sampleCount,
        samples = samples,
        flags = flags,
        version = PacketConstants.PROTOCOL_VERSION_1
    )
}
