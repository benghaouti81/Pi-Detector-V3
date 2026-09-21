package com.example.felezjoo.storage

import android.content.Context
import android.content.Intent
import com.example.felezjoo.models.DspProfile
import com.example.felezjoo.models.HardwareBoardProfile
import com.example.felezjoo.models.OperationalPreset
import com.example.felezjoo.models.PolarityMode
import com.example.felezjoo.models.WaveformPolarity
import org.json.JSONObject

/**
 * Model representing an exportable/importable settings profile package.
 */
data class ProfileExportPackage(
    val profileName: String,
    val description: String,
    val exportTimestamp: Long = System.currentTimeMillis(),
    val pulseRateHz: Int,
    val pulseWidthUs: Int,
    val delayTicks: Int,
    val sensitivity: Int,
    val targetThreshold: Double,
    val confidenceThreshold: Double,
    val ironRejectThreshold: Double,
    val audioThreshold: Double,
    val polarity: String,
    val polarityMode: String,
    val hardwareProfileId: String,
    val operationalPresetId: String,
    val appVersion: String = "2.0"
)

object ProfileJsonSerializer {

    fun serialize(pkg: ProfileExportPackage): String {
        val root = JSONObject()
        root.put("format", "FelezJoo_PI_Settings_Profile_v1")
        root.put("profileName", pkg.profileName)
        root.put("description", pkg.description)
        root.put("timestamp", pkg.exportTimestamp)
        root.put("appVersion", pkg.appVersion)

        val timing = JSONObject()
        timing.put("pulseRateHz", pkg.pulseRateHz)
        timing.put("pulseWidthUs", pkg.pulseWidthUs)
        timing.put("delayTicks", pkg.delayTicks)
        root.put("timing", timing)

        val detection = JSONObject()
        detection.put("sensitivity", pkg.sensitivity)
        detection.put("targetThreshold", pkg.targetThreshold)
        detection.put("confidenceThreshold", pkg.confidenceThreshold)
        detection.put("ironRejectThreshold", pkg.ironRejectThreshold)
        detection.put("audioThreshold", pkg.audioThreshold)
        root.put("detection", detection)

        val polarity = JSONObject()
        polarity.put("polarity", pkg.polarity)
        polarity.put("polarityMode", pkg.polarityMode)
        root.put("polarity", polarity)

        root.put("hardwareProfileId", pkg.hardwareProfileId)
        root.put("operationalPresetId", pkg.operationalPresetId)

        return root.toString(2)
    }

    fun deserialize(jsonStr: String): ProfileExportPackage {
        val root = JSONObject(jsonStr)
        val timing = root.getJSONObject("timing")
        val detection = root.getJSONObject("detection")
        val polarity = root.optJSONObject("polarity")

        return ProfileExportPackage(
            profileName = root.optString("profileName", "Imported Profile"),
            description = root.optString("description", ""),
            exportTimestamp = root.optLong("timestamp", System.currentTimeMillis()),
            pulseRateHz = timing.getInt("pulseRateHz"),
            pulseWidthUs = timing.getInt("pulseWidthUs"),
            delayTicks = timing.getInt("delayTicks"),
            sensitivity = detection.optInt("sensitivity", 5),
            targetThreshold = detection.optDouble("targetThreshold", 35.0),
            confidenceThreshold = detection.optDouble("confidenceThreshold", 45.0),
            ironRejectThreshold = detection.optDouble("ironRejectThreshold", 60.0),
            audioThreshold = detection.optDouble("audioThreshold", 25.0),
            polarity = polarity?.optString("polarity", "POSITIVE") ?: "POSITIVE",
            polarityMode = polarity?.optString("polarityMode", "AUTO") ?: "AUTO",
            hardwareProfileId = root.optString("hardwareProfileId", "leonardo_atmega32u4"),
            operationalPresetId = root.optString("operationalPresetId", "fast_scan"),
            appVersion = root.optString("appVersion", "2.0")
        )
    }

    fun serializeHardwareProfile(p: HardwareBoardProfile): String {
        val obj = JSONObject()
        obj.put("format", "FelezJoo_Hardware_Board_Profile_v1")
        obj.put("id", p.id)
        obj.put("name", p.name)
        obj.put("mcu", p.mcu)
        obj.put("clockFrequencyMhz", p.clockFrequencyMhz)
        obj.put("minFrequencyHz", p.minFrequencyHz)
        obj.put("maxFrequencyHz", p.maxFrequencyHz)
        obj.put("minPulseUs", p.minPulseUs)
        obj.put("maxPulseUs", p.maxPulseUs)
        obj.put("minDelayTicks", p.minDelayTicks)
        obj.put("maxDelayTicks", p.maxDelayTicks)
        obj.put("delayUnitUs", p.delayUnitUs)
        obj.put("sampleSpacingUs", p.sampleSpacingUs)
        obj.put("nominalAdcResolutionBits", p.nominalAdcResolutionBits)
        obj.put("effectiveAdcResolutionBits", p.effectiveAdcResolutionBits)
        obj.put("adcInputVoltageRange", p.adcInputVoltageRange)
        obj.put("digitalTxOutputLevel", p.digitalTxOutputLevel)
        obj.put("sampleCount", p.sampleCount)
        obj.put("notes", p.notes)
        return obj.toString(2)
    }

    fun deserializeHardwareProfile(jsonStr: String): HardwareBoardProfile {
        val obj = JSONObject(jsonStr)
        return HardwareBoardProfile(
            id = obj.optString("id", "custom_" + System.currentTimeMillis()),
            name = obj.getString("name"),
            mcu = obj.optString("mcu", "Custom MCU"),
            clockFrequencyMhz = obj.getInt("clockFrequencyMhz"),
            minFrequencyHz = obj.getInt("minFrequencyHz"),
            maxFrequencyHz = obj.getInt("maxFrequencyHz"),
            minPulseUs = obj.getInt("minPulseUs"),
            maxPulseUs = obj.getInt("maxPulseUs"),
            minDelayTicks = obj.getInt("minDelayTicks"),
            maxDelayTicks = obj.getInt("maxDelayTicks"),
            delayUnitUs = obj.optDouble("delayUnitUs", 1.6),
            sampleSpacingUs = obj.optDouble("sampleSpacingUs", 1.6),
            nominalAdcResolutionBits = obj.optInt("nominalAdcResolutionBits", 10),
            effectiveAdcResolutionBits = obj.optDouble("effectiveAdcResolutionBits", 9.0),
            adcInputVoltageRange = obj.optString("adcInputVoltageRange", "0V - 5V"),
            digitalTxOutputLevel = obj.optString("digitalTxOutputLevel", "5V TTL"),
            sampleCount = obj.optInt("sampleCount", 70),
            notes = obj.optString("notes", "User imported hardware board"),
            isUserEditable = true
        )
    }

    fun shareText(context: Context, title: String, textContent: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, textContent)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    }
}
