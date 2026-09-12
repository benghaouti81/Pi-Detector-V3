package com.example.felezjoo.protocol

import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.models.SamplingConfiguration
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, memory-safe, fragmented binary protocol and ASCII parser.
 */
class ProtocolParser(
    private val onDecayBlockParsed: (DecayBlock) -> Unit,
    private val onRawPacketRecord: (RawPacketRecord) -> Unit,
    private val onAsciiLineParsed: (String) -> Unit,
    private val onSequenceGapDetected: (expected: Long, actual: Long) -> Unit,
    private val onCrcErrorDetected: (expected: Int, actual: Int) -> Unit
) {
    companion object {
        const val MAX_ASCII_LINE_LENGTH = 512
        const val MAX_ASCII_LINE_BYTES = 512
        const val MAX_PAYLOAD_LENGTH = 1024
        const val MAX_BINARY_PACKET_LENGTH = 6 + MAX_PAYLOAD_LENGTH + 2 // 1032
    }

    private val rxBuffer = ByteArrayOutputStream(MAX_BINARY_PACKET_LENGTH)
    private var lastSequenceNumber: Long = -1L
    private var activeSamplingConfig = SamplingConfiguration()

    val rxBufferSize: Int
        get() = synchronized(this) { rxBuffer.size() }

    var peakRxBufferSize: Int = 0
        private set

    fun resetPeakRxBufferSize() {
        synchronized(this) {
            peakRxBufferSize = rxBuffer.size()
        }
    }

    fun updateSamplingConfig(config: SamplingConfiguration) {
        this.activeSamplingConfig = config
    }

    private fun writeToRxBuffer(data: ByteArray, offset: Int, len: Int) {
        rxBuffer.write(data, offset, len)
        val cur = rxBuffer.size()
        if (cur > peakRxBufferSize) {
            peakRxBufferSize = cur
        }
    }

    private fun isBinaryStream(incoming: ByteArray, offset: Int, length: Int): Boolean {
        val currentSize = rxBuffer.size()
        if (currentSize >= 2) {
            val buf = rxBuffer.toByteArray()
            return buf[0] == PacketConstants.SYNC_BYTE_0 && buf[1] == PacketConstants.SYNC_BYTE_1
        } else if (currentSize == 1) {
            val buf = rxBuffer.toByteArray()
            return buf[0] == PacketConstants.SYNC_BYTE_0 &&
                    offset < length &&
                    incoming[offset] == PacketConstants.SYNC_BYTE_1
        } else {
            return offset + 1 < length &&
                    incoming[offset] == PacketConstants.SYNC_BYTE_0 &&
                    incoming[offset + 1] == PacketConstants.SYNC_BYTE_1
        }
    }

    private fun getExpectedBinaryPacketLength(incoming: ByteArray, offset: Int, length: Int): Int {
        val currentSize = rxBuffer.size()
        if (currentSize >= 6) {
            val buf = rxBuffer.toByteArray()
            val payloadLen = ((buf[4].toInt() and 0xFF) or ((buf[5].toInt() and 0xFF) shl 8))
            if (payloadLen in 0..MAX_PAYLOAD_LENGTH) {
                return 6 + payloadLen + 2
            }
        } else if (currentSize == 0 && offset + 6 <= length) {
            val payloadLen = ((incoming[offset + 4].toInt() and 0xFF) or ((incoming[offset + 5].toInt() and 0xFF) shl 8))
            if (payloadLen in 0..MAX_PAYLOAD_LENGTH) {
                return 6 + payloadLen + 2
            }
        }
        return MAX_BINARY_PACKET_LENGTH
    }

    @Synchronized
    fun processIncomingBytes(incoming: ByteArray, length: Int) {
        if (length <= 0) return

        var offset = 0
        while (offset < length) {
            val isBinary = isBinaryStream(incoming, offset, length)
            val maxCap = if (isBinary) {
                getExpectedBinaryPacketLength(incoming, offset, length)
            } else {
                MAX_ASCII_LINE_BYTES
            }

            var spaceLeft = maxCap - rxBuffer.size()
            if (spaceLeft <= 0) {
                parseRxBuffer()
                spaceLeft = maxCap - rxBuffer.size()
                if (spaceLeft <= 0) {
                    // Buffer full without parsing progress: force reset to prevent deadlock
                    rxBuffer.reset()
                    spaceLeft = maxCap
                }
            }

            val toWrite = minOf(length - offset, spaceLeft)
            if (toWrite <= 0) break

            writeToRxBuffer(incoming, offset, toWrite)
            offset += toWrite

            parseRxBuffer()
        }
    }

    private fun parseRxBuffer() {
        val bufferBytes = rxBuffer.toByteArray()
        var readIndex = 0
        val bufferLen = bufferBytes.size

        while (readIndex < bufferLen) {
            // Look for binary sync 0xF5 0x5A
            if (readIndex + 1 < bufferLen &&
                bufferBytes[readIndex] == PacketConstants.SYNC_BYTE_0 &&
                bufferBytes[readIndex + 1] == PacketConstants.SYNC_BYTE_1
            ) {
                // Header is at least 6 bytes: sync(2) + ver(1) + type(1) + len(2)
                if (readIndex + 6 > bufferLen) {
                    // Incomplete header, wait for more data
                    break
                }

                val version = bufferBytes[readIndex + 2]
                val packetType = bufferBytes[readIndex + 3]
                val payloadLen = ((bufferBytes[readIndex + 4].toInt() and 0xFF) or
                        ((bufferBytes[readIndex + 5].toInt() and 0xFF) shl 8))

                // Sanity check length (max reasonable detector payload is 1024 bytes)
                if (payloadLen < 0 || payloadLen > MAX_PAYLOAD_LENGTH) {
                    // Corrupted header, skip sync and resynchronize
                    val badHeaderRecord = RawPacketRecord(
                        packetType = packetType,
                        packetLength = payloadLen,
                        isValid = false,
                        errorReason = PacketErrorReason.BAD_LENGTH,
                        rawBytes = bufferBytes.copyOfRange(readIndex, readIndex + 6)
                    )
                    onRawPacketRecord(badHeaderRecord)
                    readIndex += 2
                    continue
                }

                val totalExpectedLen = 6 + payloadLen + 2 // header(6) + payload + CRC(2)
                if (readIndex + totalExpectedLen > bufferLen) {
                    // Fragmented packet: entire packet not yet received, wait for next chunk
                    break
                }

                // Complete packet available
                val packetBytes = bufferBytes.copyOfRange(readIndex, readIndex + totalExpectedLen)
                parseCompletePacket(packetBytes, version, packetType, payloadLen)

                readIndex += totalExpectedLen
            } else if (bufferBytes[readIndex] == '#'.code.toByte()) {
                // Processing line starting with '#' (max 512 bytes)
                val newlineIdx = findNewline(bufferBytes, readIndex, bufferLen)
                if (newlineIdx != -1) {
                    val lineLen = newlineIdx - readIndex
                    if (lineLen <= MAX_ASCII_LINE_LENGTH) {
                        val line = String(bufferBytes, readIndex, lineLen).trim()
                        if (line.isNotEmpty()) {
                            handleAsciiLine(line)
                        }
                        readIndex = newlineIdx + 1
                        if (readIndex < bufferLen && bufferBytes[readIndex] == '\n'.code.toByte()) {
                            readIndex++
                        }
                        continue
                    } else {
                        // Line exceeded 512 bytes: skip corrupted '#' line and search for next SYNC
                        val nextSync = findNextSync(bufferBytes, readIndex + 1, bufferLen)
                        readIndex = nextSync
                        continue
                    }
                } else {
                    // No newline found yet
                    val nextSync = findNextSync(bufferBytes, readIndex + 1, bufferLen)
                    if (nextSync < bufferLen) {
                        // Binary sync appeared before newline: skip incomplete/corrupted '#' line and resume at SYNC
                        readIndex = nextSync
                        continue
                    } else if (bufferLen - readIndex >= MAX_ASCII_LINE_LENGTH) {
                        // Corrupt line exceeded 512 bytes without newline: skip corrupted line
                        readIndex = bufferLen
                        continue
                    } else {
                        // Incomplete line, wait for more data up to 512 bytes
                        break
                    }
                }
            } else {
                // Check if last byte is SYNC_BYTE_0: wait for possible SYNC_BYTE_1 in next chunk
                if (readIndex == bufferLen - 1 && bufferBytes[readIndex] == PacketConstants.SYNC_BYTE_0) {
                    break
                }

                // Not a sync byte: check if it's an ASCII line (e.g. text debug output)
                val newlineIdx = findNewline(bufferBytes, readIndex, bufferLen)
                if (newlineIdx != -1 && (newlineIdx - readIndex) < 128) {
                    // Check if all bytes are printable ASCII
                    var isAscii = true
                    for (k in readIndex until newlineIdx) {
                        val b = bufferBytes[k].toInt() and 0xFF
                        if (b !in 32..126 && b != '\t'.code) {
                            isAscii = false
                            break
                        }
                    }
                    if (isAscii && (newlineIdx - readIndex) > 0) {
                        val line = String(bufferBytes, readIndex, newlineIdx - readIndex).trim()
                        if (line.isNotEmpty()) {
                            handleAsciiLine(line)
                        }
                        readIndex = newlineIdx + 1
                        if (readIndex < bufferLen && bufferBytes[readIndex] == '\n'.code.toByte()) {
                            readIndex++
                        }
                        continue
                    }
                }

                // If binary sync is found later in buffer, jump directly to it
                val nextSync = findNextSync(bufferBytes, readIndex + 1, bufferLen)
                if (nextSync < bufferLen) {
                    readIndex = nextSync
                    continue
                }

                // Discard single garbage byte to search next
                readIndex++
            }
        }

        // Retain remaining unparsed bytes in rxBuffer
        rxBuffer.reset()
        if (readIndex < bufferLen) {
            rxBuffer.write(bufferBytes, readIndex, bufferLen - readIndex)
        }
    }

    private fun findNewline(bytes: ByteArray, start: Int, end: Int): Int {
        for (i in start until end) {
            if (bytes[i] == '\n'.code.toByte() || bytes[i] == '\r'.code.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun findNextSync(bytes: ByteArray, start: Int, end: Int): Int {
        for (i in start until (end - 1)) {
            if (bytes[i] == PacketConstants.SYNC_BYTE_0 && bytes[i + 1] == PacketConstants.SYNC_BYTE_1) {
                return i
            }
        }
        return end
    }

    private fun handleAsciiLine(line: String) {
        onAsciiLineParsed(line)
        if (line.startsWith("#CONFIG:") || line.startsWith("#CONFIG,")) {
            parseConfigLine(line)
        }
    }

    /**
     * Parses hardware configuration announcements:
     * Comma-delimited: #CONFIG:<sampleCount>,<sampleSpacingNs>,<adcBits>,<mode>,<pulses>,<samplesPerPulse>
     * e.g. #CONFIG:70,1600,10,ETS,14,5
     * Or key-value: #CONFIG,samples=70,spacing_ns=1600,adc=10,mode=ETS,pulses=14,samples_per_pulse=5
     */
    fun parseConfigLine(line: String) {
        try {
            val delimiter = if (line.startsWith("#CONFIG:")) ":" else ","
            val content = line.substringAfter(delimiter).trim()
            val tokens = content.split(",")

            var sampleCount = 70
            var spacingNs = 1600.0
            var adcBits = 10
            var mode = "ETS"
            var pulses = 14
            var samplesPerPulse = 5
            var explicitDelayUnitUs: Double? = null

            if (content.contains("=")) {
                // Key-value pairs
                for (token in tokens) {
                    val kv = token.split("=")
                    if (kv.size == 2) {
                        val k = kv[0].trim().lowercase()
                        val v = kv[1].trim()
                        when (k) {
                            "samples", "sample_count", "n" -> v.toIntOrNull()?.let { sampleCount = it }
                            "spacing_ns", "dt_ns", "step_ns" -> v.toDoubleOrNull()?.let { spacingNs = it }
                            "spacing_us", "dt_us" -> v.toDoubleOrNull()?.let { spacingNs = it * 1000.0 }
                            "adc", "adc_bits", "bits" -> v.toIntOrNull()?.let { adcBits = it }
                            "mode" -> mode = v
                            "pulses", "pulses_per_frame" -> v.toIntOrNull()?.let { pulses = it }
                            "samples_per_pulse", "spp" -> v.toIntOrNull()?.let { samplesPerPulse = it }
                            "delay_unit_us", "delay_unit" -> v.toDoubleOrNull()?.let { explicitDelayUnitUs = it }
                            "delay_unit_ns" -> v.toDoubleOrNull()?.let { explicitDelayUnitUs = it / 1000.0 }
                        }
                    }
                }
            } else if (tokens.size >= 2) {
                // Positional tokens: sampleCount, spacingNs, adcBits, mode, pulses, samplesPerPulse, [delayUnitUs]
                tokens.getOrNull(0)?.toIntOrNull()?.let { sampleCount = it }
                tokens.getOrNull(1)?.toDoubleOrNull()?.let { spacingNs = it }
                tokens.getOrNull(2)?.toIntOrNull()?.let { adcBits = it }
                tokens.getOrNull(3)?.let { if (it.isNotBlank()) mode = it.trim() }
                tokens.getOrNull(4)?.toIntOrNull()?.let { pulses = it }
                tokens.getOrNull(5)?.toIntOrNull()?.let { samplesPerPulse = it }
                tokens.getOrNull(6)?.toDoubleOrNull()?.let { explicitDelayUnitUs = it }
            }

            // Safe physical clamping
            sampleCount = sampleCount.coerceIn(10, 512)
            spacingNs = spacingNs.coerceIn(50.0, 1000000.0)
            adcBits = adcBits.coerceIn(8, 24)
            pulses = pulses.coerceIn(1, 128)
            samplesPerPulse = samplesPerPulse.coerceIn(1, 128)

            val sampleSpacingUs = spacingNs / 1000.0
            // Decoupled delayUnitUs: hardware timer tick duration in firmware (1.6 us in Leonardo)
            // Conceptually distinct from reconstructed sample spacing (sampleSpacingUs).
            val delayUnitUs = explicitDelayUnitUs?.coerceIn(0.1, 1000.0)
                ?: activeSamplingConfig.delayUnitUs

            val newConfig = activeSamplingConfig.copy(
                id = "device_reported_${sampleCount}",
                sampleCount = sampleCount,
                sampleSpacingUs = sampleSpacingUs,
                samplingMode = mode,
                pulsesPerFrame = pulses,
                samplesPerPulse = samplesPerPulse,
                adcResolution = adcBits,
                delayUnitUs = delayUnitUs
            )
            activeSamplingConfig = newConfig
            onAsciiLineParsed("ACK: Config updated ($sampleCount samples, ${sampleSpacingUs}us spacing, ${delayUnitUs}us delay unit, $mode)")
        } catch (e: Exception) {
            onAsciiLineParsed("ERR: Config parse error: ${e.message}")
        }
    }

    private fun parseCompletePacket(packetBytes: ByteArray, version: Byte, packetType: Byte, payloadLen: Int) {
        val totalLen = packetBytes.size
        val receivedCrc = ((packetBytes[totalLen - 2].toInt() and 0xFF) or
                ((packetBytes[totalLen - 1].toInt() and 0xFF) shl 8))
        val calculatedCrc = Crc16Ccitt.compute(packetBytes, 0, totalLen - 2)

        when (packetType) {
            PacketConstants.TYPE_RAW_BLOCK -> {
                parseRawBlock(packetBytes, version, payloadLen, receivedCrc, calculatedCrc)
            }
            else -> {
                if (receivedCrc != calculatedCrc) {
                    onCrcErrorDetected(calculatedCrc, receivedCrc)
                    val badCrcRecord = RawPacketRecord(
                        packetType = packetType,
                        packetLength = payloadLen,
                        receivedCrc = receivedCrc,
                        calculatedCrc = calculatedCrc,
                        isValid = false,
                        errorReason = PacketErrorReason.BAD_CRC,
                        rawBytes = packetBytes
                    )
                    onRawPacketRecord(badCrcRecord)
                    return
                }

                if (version != PacketConstants.CURRENT_PROTOCOL_VERSION) {
                    val badVersionRecord = RawPacketRecord(
                        packetType = packetType,
                        packetLength = payloadLen,
                        receivedCrc = receivedCrc,
                        calculatedCrc = calculatedCrc,
                        isValid = false,
                        errorReason = PacketErrorReason.UNKNOWN_VERSION,
                        rawBytes = packetBytes
                    )
                    onRawPacketRecord(badVersionRecord)
                    return
                }

                // General or command/status packet
                val record = RawPacketRecord(
                    packetType = packetType,
                    packetLength = payloadLen,
                    receivedCrc = receivedCrc,
                    calculatedCrc = calculatedCrc,
                    isValid = true,
                    errorReason = PacketErrorReason.OK,
                    rawBytes = packetBytes
                )
                onRawPacketRecord(record)
            }
        }
    }

    private fun parseRawBlock(packetBytes: ByteArray, version: Byte, payloadLen: Int, receivedCrc: Int, calculatedCrc: Int) {
        // Step 1: Validate basic payload length (at least 12 bytes metadata + 0 samples + 2 flags = 14 bytes)
        // Packet size must be at least 6 header + 14 payload + 2 CRC = 22 bytes
        if (payloadLen < 14 || packetBytes.size < 22) {
            val errRecord = RawPacketRecord(
                packetType = PacketConstants.TYPE_RAW_BLOCK,
                packetLength = payloadLen,
                receivedCrc = receivedCrc,
                calculatedCrc = calculatedCrc,
                isValid = false,
                errorReason = PacketErrorReason.BAD_LENGTH,
                rawBytes = packetBytes
            )
            onRawPacketRecord(errRecord)
            return
        }

        // Step 2: Read sampleCount from known location (bytes 16..17 in packetBytes, i.e. offset 10..11 in payload)
        val sampleCount = (packetBytes[16].toInt() and 0xFF) or
                ((packetBytes[17].toInt() and 0xFF) shl 8)

        // Step 3: Calculate expected payload length: 12 + sampleCount * 2 + 2
        val expectedPayloadLen = 12 + (sampleCount * 2) + 2

        // Step 4: Compare payloadLen and guard sampleCount against overflow
        if (sampleCount <= 0 || sampleCount > 512 || payloadLen != expectedPayloadLen || packetBytes.size != 6 + payloadLen + 2) {
            val errRecord = RawPacketRecord(
                packetType = PacketConstants.TYPE_RAW_BLOCK,
                packetLength = payloadLen,
                receivedCrc = receivedCrc,
                calculatedCrc = calculatedCrc,
                isValid = false,
                errorReason = PacketErrorReason.BAD_LENGTH,
                sampleCount = sampleCount,
                rawBytes = packetBytes
            )
            onRawPacketRecord(errRecord)
            return
        }

        // Step 5: Only then unpack metadata and samples
        val bb = ByteBuffer.wrap(packetBytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(6) // Skip sync(2), ver(1), type(1), len(2)
        val sequence = bb.getInt().toLong() and 0xFFFFFFFFL
        val timestamp = bb.getInt().toLong() and 0xFFFFFFFFL
        val delayTicks = bb.getShort().toInt() and 0xFFFF
        val bbSampleCount = bb.getShort().toInt() and 0xFFFF // guaranteed == sampleCount

        val samples = IntArray(sampleCount)
        for (i in 0 until sampleCount) {
            samples[i] = bb.getShort().toInt() and 0xFFFF
        }

        // Step 6: Read flags
        val flags = bb.getShort().toInt() and 0xFFFF

        // Step 7: CRC and Version validation
        if (receivedCrc != calculatedCrc) {
            onCrcErrorDetected(calculatedCrc, receivedCrc)
            val badCrcRecord = RawPacketRecord(
                packetType = PacketConstants.TYPE_RAW_BLOCK,
                packetLength = payloadLen,
                sequence = sequence,
                receivedCrc = receivedCrc,
                calculatedCrc = calculatedCrc,
                isValid = false,
                errorReason = PacketErrorReason.BAD_CRC,
                delayTicks = delayTicks,
                sampleCount = sampleCount,
                flags = flags,
                rawBytes = packetBytes
            )
            onRawPacketRecord(badCrcRecord)
            return
        }

        if (version != PacketConstants.CURRENT_PROTOCOL_VERSION) {
            val badVersionRecord = RawPacketRecord(
                packetType = PacketConstants.TYPE_RAW_BLOCK,
                packetLength = payloadLen,
                sequence = sequence,
                receivedCrc = receivedCrc,
                calculatedCrc = calculatedCrc,
                isValid = false,
                errorReason = PacketErrorReason.UNKNOWN_VERSION,
                delayTicks = delayTicks,
                sampleCount = sampleCount,
                flags = flags,
                rawBytes = packetBytes
            )
            onRawPacketRecord(badVersionRecord)
            return
        }

        // Sequence gap check
        if (lastSequenceNumber != -1L && sequence > lastSequenceNumber + 1L) {
            onSequenceGapDetected(lastSequenceNumber + 1L, sequence)
        }
        lastSequenceNumber = sequence

        // Step 8: Propagation of timeAxisValid directly from hardware flag
        val isTimeAxisValid = (flags and PacketConstants.FLAGS_ETS_PHASE_STEPPED) != 0

        val record = RawPacketRecord(
            packetType = PacketConstants.TYPE_RAW_BLOCK,
            packetLength = payloadLen,
            sequence = sequence,
            receivedCrc = receivedCrc,
            calculatedCrc = calculatedCrc,
            isValid = true,
            errorReason = PacketErrorReason.OK,
            delayTicks = delayTicks,
            sampleCount = sampleCount,
            flags = flags,
            rawBytes = packetBytes
        )
        onRawPacketRecord(record)

        val config = if (activeSamplingConfig.sampleCount == sampleCount &&
            activeSamplingConfig.timeAxisValid == isTimeAxisValid &&
            activeSamplingConfig.delayTicks == delayTicks
        ) {
            activeSamplingConfig
        } else {
            activeSamplingConfig.copy(
                sampleCount = sampleCount,
                timeAxisValid = isTimeAxisValid,
                delayTicks = delayTicks
            )
        }

        // Step 9: Create DecayBlock with timeAxisValid and computed delayUs
        val decayBlock = DecayBlock(
            sequenceNumber = sequence,
            timestamp = if (timestamp > 0) timestamp else System.currentTimeMillis(),
            delayTicks = delayTicks,
            sampleSpacingUs = config.sampleSpacingUs,
            sampleCount = sampleCount,
            rawSamples = samples,
            flags = flags,
            samplingConfiguration = config,
            timeAxisValid = isTimeAxisValid
        )

        onDecayBlockParsed(decayBlock)
    }

    @Synchronized
    fun reset() {
        rxBuffer.reset()
        lastSequenceNumber = -1L
        peakRxBufferSize = 0
    }
}
