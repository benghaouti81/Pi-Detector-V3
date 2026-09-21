package com.example.felezjoo.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.felezjoo.audio.AudioMode
import com.example.felezjoo.models.DspProfile
import com.example.felezjoo.models.HardwareBoardProfile
import com.example.felezjoo.models.OperationalPreset
import com.example.felezjoo.models.PolarityMode
import com.example.felezjoo.models.SamplingConfiguration
import com.example.felezjoo.models.WaveformPolarity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for persistent application state:
 * - Auto-saves on parameter adjustment so reopen stays exactly on user's configuration
 * - Exports & imports complete configuration profiles as shareable JSON bundles
 * - Exports & imports custom hardware board profiles
 */
class SettingsPreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("felezjoo_detector_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PULSE_RATE = "pulse_rate_hz"
        private const val KEY_PULSE_WIDTH = "pulse_width_us"
        private const val KEY_DELAY_TICKS = "delay_ticks"
        private const val KEY_SENSITIVITY = "simplified_sensitivity"
        private const val KEY_DEV_MODE = "is_developer_mode"
        private const val KEY_HARDWARE_PROFILE_ID = "active_hardware_profile_id"
        private const val KEY_OPERATIONAL_PRESET_ID = "active_operational_preset_id"
        private const val KEY_DSP_PROFILE_ID = "active_dsp_profile_id"
        private const val KEY_TARGET_THRESHOLD = "dsp_target_threshold"
        private const val KEY_CONFIDENCE_THRESHOLD = "dsp_confidence_threshold"
        private const val KEY_IRON_REJECT = "dsp_iron_reject"
        private const val KEY_AUDIO_THRESHOLD = "audio_threshold"
        private const val KEY_AUDIO_MODE = "audio_mode"
        private const val KEY_AUDIO_MUTED = "audio_muted"
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        private const val KEY_POLARITY = "waveform_polarity"
        private const val KEY_POLARITY_MODE = "waveform_polarity_mode"
        private const val KEY_CUSTOM_HARDWARE_JSON = "custom_hardware_profiles_json"
    }

    fun savePulseRate(rateHz: Int) = prefs.edit().putInt(KEY_PULSE_RATE, rateHz).apply()
    fun getPulseRate(default: Int = 200): Int = prefs.getInt(KEY_PULSE_RATE, default)

    fun savePulseWidth(widthUs: Int) = prefs.edit().putInt(KEY_PULSE_WIDTH, widthUs).apply()
    fun getPulseWidth(default: Int = 150): Int = prefs.getInt(KEY_PULSE_WIDTH, default)

    fun saveDelayTicks(ticks: Int) = prefs.edit().putInt(KEY_DELAY_TICKS, ticks).apply()
    fun getDelayTicks(default: Int = 10): Int = prefs.getInt(KEY_DELAY_TICKS, default)

    fun saveSensitivity(level: Int) = prefs.edit().putInt(KEY_SENSITIVITY, level).apply()
    fun getSensitivity(default: Int = 5): Int = prefs.getInt(KEY_SENSITIVITY, default)

    fun saveDeveloperMode(enabled: Boolean) = prefs.edit().putBoolean(KEY_DEV_MODE, enabled).apply()
    fun getDeveloperMode(default: Boolean = false): Boolean = prefs.getBoolean(KEY_DEV_MODE, default)

    fun saveHardwareProfileId(id: String) = prefs.edit().putString(KEY_HARDWARE_PROFILE_ID, id).apply()
    fun getHardwareProfileId(default: String = "leonardo_atmega32u4"): String =
        prefs.getString(KEY_HARDWARE_PROFILE_ID, default) ?: default

    fun saveOperationalPresetId(id: String) = prefs.edit().putString(KEY_OPERATIONAL_PRESET_ID, id).apply()
    fun getOperationalPresetId(default: String = "fast_scan"): String =
        prefs.getString(KEY_OPERATIONAL_PRESET_ID, default) ?: default

    fun saveDspProfileId(id: String) = prefs.edit().putString(KEY_DSP_PROFILE_ID, id).apply()
    fun getDspProfileId(default: String = "profile_stable"): String =
        prefs.getString(KEY_DSP_PROFILE_ID, default) ?: default

    fun saveDspThresholds(target: Double, confidence: Double, ironReject: Double) {
        prefs.edit()
            .putFloat(KEY_TARGET_THRESHOLD, target.toFloat())
            .putFloat(KEY_CONFIDENCE_THRESHOLD, confidence.toFloat())
            .putFloat(KEY_IRON_REJECT, ironReject.toFloat())
            .apply()
    }

    fun getTargetThreshold(default: Float = 35f): Float = prefs.getFloat(KEY_TARGET_THRESHOLD, default)
    fun getConfidenceThreshold(default: Float = 45f): Float = prefs.getFloat(KEY_CONFIDENCE_THRESHOLD, default)
    fun getIronRejectThreshold(default: Float = 60f): Float = prefs.getFloat(KEY_IRON_REJECT, default)

    fun saveAudioSettings(threshold: Double, mode: AudioMode, muted: Boolean, haptic: Boolean) {
        prefs.edit()
            .putFloat(KEY_AUDIO_THRESHOLD, threshold.toFloat())
            .putString(KEY_AUDIO_MODE, mode.name)
            .putBoolean(KEY_AUDIO_MUTED, muted)
            .putBoolean(KEY_HAPTIC_ENABLED, haptic)
            .apply()
    }

    fun getAudioThreshold(default: Float = 25f): Float = prefs.getFloat(KEY_AUDIO_THRESHOLD, default)
    fun getAudioMode(default: AudioMode = AudioMode.VCO): AudioMode {
        val name = prefs.getString(KEY_AUDIO_MODE, default.name) ?: default.name
        return try { AudioMode.valueOf(name) } catch (e: Exception) { default }
    }
    fun getAudioMuted(default: Boolean = false): Boolean = prefs.getBoolean(KEY_AUDIO_MUTED, default)
    fun getHapticEnabled(default: Boolean = true): Boolean = prefs.getBoolean(KEY_HAPTIC_ENABLED, default)

    fun savePolarityConfig(polarity: WaveformPolarity, mode: PolarityMode) {
        prefs.edit()
            .putString(KEY_POLARITY, polarity.name)
            .putString(KEY_POLARITY_MODE, mode.name)
            .apply()
    }

    fun getWaveformPolarity(default: WaveformPolarity = WaveformPolarity.POSITIVE): WaveformPolarity {
        val name = prefs.getString(KEY_POLARITY, default.name) ?: default.name
        return try { WaveformPolarity.valueOf(name) } catch (e: Exception) { default }
    }

    fun getPolarityMode(default: PolarityMode = PolarityMode.AUTO): PolarityMode {
        val name = prefs.getString(KEY_POLARITY_MODE, default.name) ?: default.name
        return try { PolarityMode.valueOf(name) } catch (e: Exception) { default }
    }

    // Custom Hardware Boards Persistence
    fun saveCustomHardwareProfiles(profiles: List<HardwareBoardProfile>) {
        val userCreated = profiles.filter { it.isUserEditable }
        val array = JSONArray()
        for (p in userCreated) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("mcu", p.mcu)
                put("clockFrequencyMhz", p.clockFrequencyMhz)
                put("minFrequencyHz", p.minFrequencyHz)
                put("maxFrequencyHz", p.maxFrequencyHz)
                put("minPulseUs", p.minPulseUs)
                put("maxPulseUs", p.maxPulseUs)
                put("minDelayTicks", p.minDelayTicks)
                put("maxDelayTicks", p.maxDelayTicks)
                put("delayUnitUs", p.delayUnitUs)
                put("sampleSpacingUs", p.sampleSpacingUs)
                put("nominalAdcResolutionBits", p.nominalAdcResolutionBits)
                put("effectiveAdcResolutionBits", p.effectiveAdcResolutionBits)
                put("adcInputVoltageRange", p.adcInputVoltageRange)
                put("digitalTxOutputLevel", p.digitalTxOutputLevel)
                put("sampleCount", p.sampleCount)
                put("notes", p.notes)
                put("isUserEditable", true)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_HARDWARE_JSON, array.toString()).apply()
    }

    fun loadCustomHardwareProfiles(): List<HardwareBoardProfile> {
        val jsonStr = prefs.getString(KEY_CUSTOM_HARDWARE_JSON, null) ?: return emptyList()
        val list = mutableListOf<HardwareBoardProfile>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    HardwareBoardProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        mcu = obj.getString("mcu"),
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
                        notes = obj.optString("notes", ""),
                        isUserEditable = true
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
