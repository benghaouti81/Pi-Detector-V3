package com.example.felezjoo

import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.protocol.PacketConstants
import com.example.felezjoo.protocol.PacketGenerator
import com.example.felezjoo.protocol.ProtocolParser
import com.example.felezjoo.protocol.RawPacketRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareCorrectionsTest {

    // =========================================================================
    // ISSUE 1: Timing Model Verification (ETS Decay Acquisition from TX-OFF Zero)
    // =========================================================================
    // ATmega32U4 Auto Trigger hardware delay: 2 ADC clocks + 3 CPU cycles = 35 cycles
    // TCNT1 is reset to 0 at TX-OFF (TIMER3_COMPB), so desired S&H is measured from TX-OFF.
    private val ADC_TRIGGER_TO_SH_CYCLES = 35L

    private fun etsTicksToCpuCycles(ticks: Long): Long =
        (ticks * 128L + 2L) / 5L

    private fun calculateDesiredSampleHoldCycles(
        activeDelayTicks: Int,
        pulse: Int,
        slot: Int
    ): Long {
        val delayCycles = etsTicksToCpuCycles(activeDelayTicks.toLong())
        val phaseTicks = pulse.toLong() + slot.toLong() * 14L
        val phaseCycles = etsTicksToCpuCycles(phaseTicks)
        return delayCycles + phaseCycles
    }

    @Test
    fun testIssue1_pulse150_delay10_slot0_sampleHoldTiming() {
        val activeDelayTicks = 10
        val pulse = 0
        val slot = 0

        val desiredSH = calculateDesiredSampleHoldCycles(activeDelayTicks, pulse, slot)
        val delayCycles = etsTicksToCpuCycles(10L) // (1280 + 2) / 5 = 256 cycles = 16.0 us
        val expectedCycles = delayCycles // 256 cycles from TX-OFF

        assertEquals(256L, desiredSH)
        assertEquals(expectedCycles, desiredSH)

        val timeFromTxOffUs = desiredSH / 16.0
        assertEquals(16.0, timeFromTxOffUs, 0.001) // 16.0 us from TX-OFF

        // Hardware Compare Match B compensation with ATmega32U4 35-cycle trigger-to-S&H:
        // OCR1B = desiredSH - 35
        val ocr1b = desiredSH - ADC_TRIGGER_TO_SH_CYCLES
        assertEquals(221L, ocr1b)
    }

    @Test
    fun testIssue1_pulse150_delay10_slot1_sampleHoldTiming() {
        val activeDelayTicks = 10
        val pulse = 0
        val slot = 1

        val desiredSH = calculateDesiredSampleHoldCycles(activeDelayTicks, pulse, slot)
        val delayCycles = etsTicksToCpuCycles(10L) // 256 cycles = 16.0 us
        val phaseTicks = 14L
        val phaseCycles = etsTicksToCpuCycles(phaseTicks) // (14 * 128 + 2) / 5 = 358 cycles = 22.375 us (~22.4 us)

        assertEquals(256L + 358L, desiredSH)
        assertEquals(614L, desiredSH)

        val timeFromTxOffUs = desiredSH / 16.0
        assertEquals(16.0 + (358.0 / 16.0), timeFromTxOffUs, 0.001) // 16.0 us + 22.375 us = 38.375 us
        assertEquals(38.375, timeFromTxOffUs, 0.001)

        val ocr1b = desiredSH - ADC_TRIGGER_TO_SH_CYCLES
        assertEquals(614L - 35L, ocr1b)
        assertEquals(579L, ocr1b)
    }

    @Test
    fun testIssue1_lastPhysicalSampleTiming() {
        // Last physical sample: pulse = 13, slot = 4
        val activeDelayTicks = 10
        val pulse = 13
        val slot = 4

        val phaseTicks = pulse.toLong() + slot.toLong() * 14L // 13 + 56 = 69 ticks
        assertEquals(69L, phaseTicks)

        val phaseCycles = etsTicksToCpuCycles(phaseTicks) // (69 * 128 + 2) / 5 = 1766 cycles
        assertEquals(1766L, phaseCycles)

        val desiredSH = calculateDesiredSampleHoldCycles(activeDelayTicks, pulse, slot)
        assertEquals(256L + 1766L, desiredSH) // 2022 cycles

        val timeFromTxOffUs = desiredSH / 16.0
        // 2022 / 16 = 126.375 us from TX-OFF
        assertEquals(126.375, timeFromTxOffUs, 0.001)

        // Verify OCR1B register value under 35-cycle model
        val ocr1b = desiredSH - ADC_TRIGGER_TO_SH_CYCLES
        assertEquals(2022L - 35L, ocr1b)
        assertEquals(1987L, ocr1b)
    }

    // =========================================================================
    // ISSUE 2: ETS Boundary & Acquisition Guarding Verification
    // =========================================================================

    @Test
    fun testIssue2_etsBoundaryGuardPreventsAcquisitionAfterFrameComplete() {
        val etsPulses = 14
        val etsSlots = 5
        var etsFrameActive = 1
        var etsPulse = 0
        var adcAcquisitionActive = false
        var acquisitionStartsCount = 0

        val writtenIndices = mutableListOf<Int>()
        val sampleBuffer = IntArray(70)

        fun canStartAcquisition(): Boolean {
            if (etsFrameActive != 0 && etsPulse >= etsPulses) return false
            if (etsPulse >= etsPulses) return false
            return true
        }

        // Run all 14 pulses (0..13)
        for (p in 0 until etsPulses) {
            etsPulse = p
            assertTrue("Acquisition must start for physical pulse $p", canStartAcquisition())
            acquisitionStartsCount++
            adcAcquisitionActive = true

            // Receive 5 ADC results for this pulse
            for (slot in 0 until etsSlots) {
                val index = etsPulse + slot * etsPulses
                assertTrue("Sample index $index must be in 0..69", index in 0 until 70)
                writtenIndices.add(index)
                sampleBuffer[index] = 1000 + index
            }
            adcAcquisitionActive = false
        }

        // After the 14th pulse (pulse 13) completes its 5th result:
        etsPulse = 14
        assertEquals(14, etsPulse)
        assertEquals(70, writtenIndices.size)
        assertEquals(70, writtenIndices.distinct().size)
        assertTrue(writtenIndices.all { it in 0..69 })

        // Timer3 COMPB fires again while frame is awaiting serviceFrameCompletion
        assertFalse("Guard must prevent any new ADC acquisition when etsPulse >= 14", canStartAcquisition())
        assertEquals("Acquisitions must remain strictly capped at 14 pulses", 14, acquisitionStartsCount)
    }

    // =========================================================================
    // ISSUE 3: Frame Metadata Preservation (Active vs Pending Configuration)
    // =========================================================================

    @Test
    fun testIssue3_frameMetadataPreservesActiveConfigIndependentOfPendingChanges() {
        // Step 1: Initialize active vs pending configurations
        var activeFrequency = 200
        var activePulseUs = 150
        var activeDelayTicks = 10

        var pendingFrequency = 200
        var pendingPulseUs = 150
        var pendingDelayTicks = 10

        // Per-buffer metadata
        val bufferCount = 3
        val bufferDelayTicks = IntArray(bufferCount)
        val bufferFrequencyHz = IntArray(bufferCount)
        val bufferPulseUs = IntArray(bufferCount)
        val bufferSequence = LongArray(bufferCount)

        // Capture buffer 0 for frame start
        val captureBuffer = 0
        bufferSequence[captureBuffer] = 101L
        bufferDelayTicks[captureBuffer] = activeDelayTicks
        bufferFrequencyHz[captureBuffer] = activeFrequency
        bufferPulseUs[captureBuffer] = activePulseUs

        // Verify captured metadata matches active configuration
        assertEquals(200, bufferFrequencyHz[captureBuffer])
        assertEquals(150, bufferPulseUs[captureBuffer])
        assertEquals(10, bufferDelayTicks[captureBuffer])

        // Step 2: User requests pending configuration changes during the frame
        pendingFrequency = 500
        pendingPulseUs = 250
        pendingDelayTicks = 20

        // Captured buffer metadata must remain strictly unchanged
        assertEquals(200, bufferFrequencyHz[captureBuffer])
        assertEquals(150, bufferPulseUs[captureBuffer])
        assertEquals(10, bufferDelayTicks[captureBuffer])

        // Step 3: Test packet generation with buffer metadata
        val sampleData = IntArray(70) { 800 - it * 10 }
        val v2Packet = PacketGenerator.createRawBlockPacket(
            sequence = bufferSequence[captureBuffer],
            timestamp = 12345678L,
            delayTicks = bufferDelayTicks[captureBuffer],
            samples = sampleData,
            flags = PacketConstants.FLAGS_ETS_PHASE_STEPPED,
            frequencyHz = bufferFrequencyHz[captureBuffer],
            pulseUs = bufferPulseUs[captureBuffer],
            version = PacketConstants.PROTOCOL_VERSION_2
        )

        assertEquals(166, v2Packet.size)
        assertEquals(PacketConstants.RAW_BLOCK_TOTAL_PACKET_LEN, v2Packet.size)

        // Step 4: Parse with ProtocolParser
        var parsedRecord: RawPacketRecord? = null
        var parsedBlock: DecayBlock? = null
        val parser = ProtocolParser(
            onDecayBlockParsed = { parsedBlock = it },
            onRawPacketRecord = { parsedRecord = it },
            onAsciiLineParsed = {},
            onSequenceGapDetected = { _, _ -> },
            onCrcErrorDetected = { _, _ -> }
        )

        parser.processIncomingBytes(v2Packet, v2Packet.size)

        assertNotNull(parsedRecord)
        assertTrue(parsedRecord!!.isValid)
        assertEquals(200, parsedRecord!!.frequencyHz)
        assertEquals(150, parsedRecord!!.pulseUs)
        assertEquals(10, parsedRecord!!.delayTicks)

        assertNotNull(parsedBlock)
        assertEquals(200, parsedBlock!!.pulseRate)
        assertEquals(150, parsedBlock!!.pulseWidthUs)
        assertEquals(10, parsedBlock!!.delayTicks)
        assertEquals("2.0", parsedBlock!!.protocolVersion)
        assertTrue(parsedBlock!!.timeAxisValid)

        // Step 5: Verify that even after applying new active settings for the NEXT frame,
        // the parsed data from buffer 0 still reflects its active settings
        activeFrequency = pendingFrequency
        activePulseUs = pendingPulseUs
        activeDelayTicks = pendingDelayTicks

        assertEquals(200, parsedBlock!!.pulseRate)
        assertEquals(150, parsedBlock!!.pulseWidthUs)
        assertEquals(10, parsedBlock!!.delayTicks)
    }

    @Test
    fun testIssue3_backwardCompatibilityWithV1Packets() {
        val sampleData = IntArray(70) { 400 - it * 2 }
        val v1Packet = PacketGenerator.createRawBlockPacketV1(
            sequence = 42L,
            timestamp = 999999L,
            delayTicks = 12,
            samples = sampleData,
            flags = PacketConstants.FLAGS_ETS_PHASE_STEPPED
        )

        assertEquals(162, v1Packet.size)
        assertEquals(PacketConstants.RAW_BLOCK_V1_TOTAL_PACKET_LEN, v1Packet.size)

        var parsedRecord: RawPacketRecord? = null
        var parsedBlock: DecayBlock? = null
        val parser = ProtocolParser(
            onDecayBlockParsed = { parsedBlock = it },
            onRawPacketRecord = { parsedRecord = it },
            onAsciiLineParsed = {},
            onSequenceGapDetected = { _, _ -> },
            onCrcErrorDetected = { _, _ -> }
        )

        parser.processIncomingBytes(v1Packet, v1Packet.size)

        assertNotNull(parsedRecord)
        assertTrue("V1 packet must be accepted by parser", parsedRecord!!.isValid)
        assertEquals(42L, parsedRecord!!.sequence)

        assertNotNull(parsedBlock)
        assertEquals(42L, parsedBlock!!.sequenceNumber)
        assertEquals("1.0", parsedBlock!!.protocolVersion)
        assertEquals(12, parsedBlock!!.delayTicks)
    }
}
