package com.example.felezjoo.models

import java.io.Serializable

/**
 * Immutable/pre-configured Hardware Board Profile representing a specific MCU/detector board.
 * Holds physical safe operating boundaries, ADC characteristics, and I/O logic levels.
 *
 * NOTE: The app NEVER queries the board to auto-detect profiles. Selection is strictly manual.
 */
data class HardwareBoardProfile(
    val id: String,
    val name: String,
    val mcu: String,
    val clockFrequencyMhz: Int,
    val minFrequencyHz: Int,
    val maxFrequencyHz: Int,
    val minPulseUs: Int,
    val maxPulseUs: Int,
    val minDelayTicks: Int,
    val maxDelayTicks: Int,
    val delayUnitUs: Double,
    val sampleSpacingUs: Double,
    val nominalAdcResolutionBits: Int,
    val effectiveAdcResolutionBits: Double,
    val adcInputVoltageRange: String,
    val digitalTxOutputLevel: String,
    val sampleCount: Int = 70,
    val notes: String = "",
    val isUserEditable: Boolean = false
) : Serializable {

    fun clampFrequency(freqHz: Int): Int = freqHz.coerceIn(minFrequencyHz, maxFrequencyHz)
    fun clampPulse(pulseUs: Int): Int = pulseUs.coerceIn(minPulseUs, maxPulseUs)
    fun clampDelay(delayTicks: Int): Int = delayTicks.coerceIn(minDelayTicks, maxDelayTicks)

    companion object {
        val LEONARDO_DEFAULT = HardwareBoardProfile(
            id = "leonardo_atmega32u4",
            name = "Arduino Leonardo (ATmega32U4)",
            mcu = "ATmega32U4 @ 16 MHz",
            clockFrequencyMhz = 16,
            minFrequencyHz = 50,
            maxFrequencyHz = 500,
            minPulseUs = 40,
            maxPulseUs = 300,
            minDelayTicks = 4,
            maxDelayTicks = 60,
            delayUnitUs = 1.6,
            sampleSpacingUs = 1.6,
            nominalAdcResolutionBits = 10,
            effectiveAdcResolutionBits = 9.0, // At 1 MHz ADC clock effective resolution is ~9 bits
            adcInputVoltageRange = "0V - 5V (AVcc)",
            digitalTxOutputLevel = "5V TTL (Direct PB5/D9)",
            sampleCount = 70,
            notes = "Standard reference hardware. Single TX output (PB5) and ADC single-channel input (ADC7/A0).",
            isUserEditable = false
        )

        val PICO_RP2040_PREVIEW = HardwareBoardProfile(
            id = "pico_rp2040",
            name = "Raspberry Pi Pico (RP2040 - Future/Preview)",
            mcu = "RP2040 Dual ARM Cortex-M0+ @ 125/133 MHz",
            clockFrequencyMhz = 125,
            minFrequencyHz = 30,
            maxFrequencyHz = 1000,
            minPulseUs = 20,
            maxPulseUs = 350,
            minDelayTicks = 2,
            maxDelayTicks = 100,
            delayUnitUs = 1.0,
            sampleSpacingUs = 1.0,
            nominalAdcResolutionBits = 12,
            effectiveAdcResolutionBits = 8.7, // RP2040 ADC has known DNL/INL non-linearity
            adcInputVoltageRange = "0V - 3.3V (VREF)",
            digitalTxOutputLevel = "3.3V LVCMOS (Requires 5V MOSFET driver for standard power stage)",
            sampleCount = 70,
            notes = "Preview profile. High clock rate allows sub-microsecond ETS resolution.",
            isUserEditable = false
        )

        val STM32F4_PREVIEW = HardwareBoardProfile(
            id = "stm32f401",
            name = "STM32F401 BlackPill (ARM Cortex-M4 - Future/Preview)",
            mcu = "STM32F401CCU6 @ 84 MHz",
            clockFrequencyMhz = 84,
            minFrequencyHz = 40,
            maxFrequencyHz = 800,
            minPulseUs = 25,
            maxPulseUs = 300,
            minDelayTicks = 2,
            maxDelayTicks = 80,
            delayUnitUs = 1.0,
            sampleSpacingUs = 1.0,
            nominalAdcResolutionBits = 12,
            effectiveAdcResolutionBits = 11.2,
            adcInputVoltageRange = "0V - 3.3V (VDDA)",
            digitalTxOutputLevel = "3.3V (Requires level translation to 5V for FET gate driver)",
            sampleCount = 70,
            notes = "Preview profile. Fast hardware SAR ADC with DMA pipeline support.",
            isUserEditable = false
        )

        val BUILT_IN_PROFILES = listOf(
            LEONARDO_DEFAULT,
            PICO_RP2040_PREVIEW,
            STM32F4_PREVIEW
        )
    }
}

/**
 * Functional Operational Preset.
 * Provides tested safe combinations of timing parameters for common field tasks.
 */
data class OperationalPreset(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val descriptionAr: String,
    val frequencyHz: Int,
    val pulseWidthUs: Int,
    val delayTicks: Int
) : Serializable {
    companion object {
        val FAST_SCAN = OperationalPreset(
            id = "fast_scan",
            nameAr = "مسح سريع",
            nameEn = "Fast Scan",
            descriptionAr = "تردد مرتفع ونبضة متوسطة لتغطية سريعة والتقاط الأهداف الضحلة",
            frequencyHz = 200,
            pulseWidthUs = 150,
            delayTicks = 10
        )

        val DEEP_SEARCH = OperationalPreset(
            id = "deep_search",
            nameAr = "بحث عميق",
            nameEn = "Deep Search",
            descriptionAr = "تردد منخفض ونبضة عريضة مشبعة لتوليد مجال كهرومغناطيسي قوي للأعماق",
            frequencyHz = 100,
            pulseWidthUs = 250,
            delayTicks = 12
        )

        val IRON_DISCRIM = OperationalPreset(
            id = "iron_discrim",
            nameAr = "تمييز الحديد",
            nameEn = "Iron Discrimination",
            descriptionAr = "توقيت دقيق مع تأخير منخفض لالتقاط منحنى الاضمحلال المبكر وتمييز الخواص المغناطيسية",
            frequencyHz = 300,
            pulseWidthUs = 100,
            delayTicks = 8
        )

        val PRESETS = listOf(FAST_SCAN, DEEP_SEARCH, IRON_DISCRIM)
    }
}
