package com.example.felezjoo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import com.example.felezjoo.audio.AudioMode
import com.example.felezjoo.storage.ProfileJsonSerializer
import com.example.felezjoo.models.HardwareBoardProfile
import com.example.felezjoo.models.OperationalPreset
import com.example.felezjoo.models.PolarityDetectionQuality
import com.example.felezjoo.models.PolarityMode
import com.example.felezjoo.models.WaveformPolarity
import com.example.felezjoo.ui.components.TactileButton
import com.example.felezjoo.ui.components.TactileChip
import com.example.felezjoo.ui.components.TactileOutlinedButton
import com.example.felezjoo.viewmodel.FelezJooViewModel
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabError
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabPrimaryPressed
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSecondaryPressed
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceHighlight
import com.example.ui.theme.LabSurfaceVariant
import com.example.ui.theme.LabTertiary
import com.example.ui.theme.LabTextMuted
import com.example.ui.theme.LabTextSecondary

@Composable
fun DetectorControlsScreen(viewModel: FelezJooViewModel) {
    val currentBlock by viewModel.currentBlock.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val samplingConfig by viewModel.samplingConfig.collectAsState()
    val polarityDetectionResult by viewModel.polarityDetectionResult.collectAsState()
    val dspResult by viewModel.dspResult.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    val isSimulation by viewModel.isSimulationMode.collectAsState()
    val activeHardwareProfile by viewModel.activeHardwareProfile.collectAsState()
    val availableHardwareProfiles by viewModel.availableHardwareProfiles.collectAsState()
    val availablePresets by viewModel.availablePresets.collectAsState()
    val activePreset by viewModel.activePreset.collectAsState()
    val simplifiedSensitivity by viewModel.simplifiedSensitivity.collectAsState()
    val guidedCalibrationStep by viewModel.guidedCalibrationStep.collectAsState()
    val guidedCalibrationMessage by viewModel.guidedCalibrationMessage.collectAsState()
    val groundCaptureProgress by viewModel.groundCaptureProgress.collectAsState()
    val audioManager = viewModel.audioManager

    var pulseRateHz by remember { mutableIntStateOf(currentBlock.pulseRate) }
    var pulseWidthUs by remember { mutableIntStateOf(currentBlock.pulseWidthUs) }
    var delayTicks by remember { mutableIntStateOf(currentBlock.delayTicks) }

    var targetThreshold by remember { mutableFloatStateOf(activeProfile.targetThreshold.toFloat()) }
    var confidenceThreshold by remember { mutableFloatStateOf(activeProfile.confidenceThreshold.toFloat()) }
    var ironRejectThreshold by remember { mutableFloatStateOf(activeProfile.ironRejectThreshold.toFloat()) }
    var audioThreshold by remember { mutableFloatStateOf(audioManager.audioThreshold.toFloat()) }
    var hapticState by remember { mutableStateOf(audioManager.hapticEnabled) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Dialog States for Profile & Hardware import/export
    var showExportProfileDialog by remember { mutableStateOf(false) }
    var showImportProfileDialog by remember { mutableStateOf(false) }
    var showAddHardwareDialog by remember { mutableStateOf(false) }

    var exportProfileName by remember { mutableStateOf("Setup ${System.currentTimeMillis() % 1000}") }
    var exportProfileDescription by remember { mutableStateOf("Custom detector configuration") }
    var importJsonText by remember { mutableStateOf("") }

    // New Hardware Dialog fields
    var newBoardName by remember { mutableStateOf("") }
    var newBoardMcu by remember { mutableStateOf("ESP32-S3 @ 240MHz") }
    var newBoardFreqMhz by remember { mutableStateOf("240") }
    var newBoardMinFreq by remember { mutableStateOf("30") }
    var newBoardMaxFreq by remember { mutableStateOf("800") }
    var newBoardMinPulse by remember { mutableStateOf("20") }
    var newBoardMaxPulse by remember { mutableStateOf("350") }
    var newBoardMinDelay by remember { mutableStateOf("2") }
    var newBoardMaxDelay by remember { mutableStateOf("100") }
    var newBoardNotes by remember { mutableStateOf("Custom user hardware board") }

    LaunchedEffect(currentBlock) {
        pulseRateHz = currentBlock.pulseRate
        pulseWidthUs = currentBlock.pulseWidthUs
        delayTicks = currentBlock.delayTicks
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // App Mode Header (User vs Developer)
        Surface(
            color = if (isDeveloperMode) LabSurfaceHighlight else LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (isDeveloperMode) LabTertiary else LabSecondary),
            modifier = Modifier.fillMaxWidth().testTag("app_mode_toggle_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isDeveloperMode) Icons.Default.DeveloperMode else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (isDeveloperMode) LabTertiary else LabSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            if (isDeveloperMode) "DEVELOPER MODE (UNLOCKED)" else "USER MODE (FIELD SAFE)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (isDeveloperMode) "Full low-level register control and simulation unlocked" else "Curated presets, 1..10 sensitivity, 2-step calibration",
                            fontSize = 10.sp,
                            color = LabTextSecondary
                        )
                    }
                }
                Switch(
                    checked = isDeveloperMode,
                    onCheckedChange = { viewModel.setDeveloperMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LabTertiary,
                        checkedTrackColor = LabTertiary.copy(alpha = 0.4f),
                        uncheckedThumbColor = LabSecondary
                    ),
                    modifier = Modifier.testTag("developer_mode_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Profile Management, Save, Export, Import & Share System
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, LabSecondary),
            modifier = Modifier.fillMaxWidth().testTag("profile_export_import_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = LabSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PROFILE & HARDWARE PRESETS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
                    }
                    Text("Auto-Saved", fontSize = 10.sp, color = LabTextSecondary, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Save current timing, pulse widths & sensitivity, export as JSON, or share with colleagues via Bluetooth/Chat.",
                    fontSize = 11.sp,
                    color = LabTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Export & Share Button
                    TactileButton(
                        onClick = { showExportProfileDialog = true },
                        containerColor = LabSecondary,
                        modifier = Modifier.weight(1f).height(38.dp).testTag("export_profile_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("EXPORT / SHARE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Import Button
                    TactileOutlinedButton(
                        onClick = {
                            importJsonText = ""
                            showImportProfileDialog = true
                        },
                        accentColor = LabPrimary,
                        modifier = Modifier.weight(1f).height(38.dp).testTag("import_profile_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("IMPORT PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hardware Board Profile Selector (Section 4)
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth().testTag("hardware_profile_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = LabPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TARGET HARDWARE PROFILE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                    }
                    Text(
                        "${activeHardwareProfile.clockFrequencyMhz} MHz • ${activeHardwareProfile.digitalTxOutputLevel}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = LabTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Select the physical micro-controller running the detector firmware to enforce physical timing limits:",
                    fontSize = 11.sp,
                    color = LabTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableHardwareProfiles.forEach { profile ->
                        TactileChip(
                            selected = activeHardwareProfile.id == profile.id,
                            onClick = { viewModel.setHardwareBoardProfile(profile) },
                            label = profile.name.substringBefore(" ("),
                            activeColor = LabPrimary,
                            modifier = Modifier.weight(1f).testTag("profile_${profile.id}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = LabSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, LabBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "${activeHardwareProfile.name}: ${activeHardwareProfile.notes}",
                            fontSize = 10.sp,
                            color = Color.White
                        )
                        Text(
                            "Bounds: Freq ${activeHardwareProfile.minFrequencyHz}..${activeHardwareProfile.maxFrequencyHz} Hz | Pulse ${activeHardwareProfile.minPulseUs}..${activeHardwareProfile.maxPulseUs} µs | Delay ${activeHardwareProfile.minDelayTicks}..${activeHardwareProfile.maxDelayTicks} ticks",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = LabTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Hardware Add / Export Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TactileButton(
                        onClick = {
                            newBoardName = "Custom Board ${availableHardwareProfiles.size + 1}"
                            showAddHardwareDialog = true
                        },
                        containerColor = LabTertiary,
                        modifier = Modifier.weight(1f).height(32.dp).testTag("add_custom_hardware_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ ADD NEW BOARD", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    if (activeHardwareProfile.isUserEditable) {
                        TactileOutlinedButton(
                            onClick = {
                                viewModel.deleteCustomHardwareProfile(activeHardwareProfile.id)
                                Toast.makeText(context, "Deleted board profile", Toast.LENGTH_SHORT).show()
                            },
                            accentColor = LabError,
                            borderColor = LabError,
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = LabError)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DELETE BOARD", fontSize = 10.sp, color = LabError)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ==========================================
        // USER MODE (FIELD-SAFE) UI COMPONENTS
        // ==========================================
        if (!isDeveloperMode) {
            // Operational Presets
            Surface(
                color = LabSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LabBorder),
                modifier = Modifier.fillMaxWidth().testTag("operational_presets_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = LabSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OPERATIONAL PRESETS (برامج التشغيل الميداني)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Curated task configurations optimized for search conditions:",
                        fontSize = 11.sp,
                        color = LabTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availablePresets.forEach { preset ->
                            val isSelected = activePreset.id == preset.id
                            Surface(
                                color = if (isSelected) LabSecondary.copy(alpha = 0.15f) else LabSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) LabSecondary else LabBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${preset.nameEn} (${preset.nameAr})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isSelected) LabSecondary else Color.White
                                        )
                                        Text(
                                            preset.descriptionAr,
                                            fontSize = 10.sp,
                                            color = LabTextSecondary
                                        )
                                    }
                                    TactileButton(
                                        onClick = { viewModel.applyOperationalPreset(preset) },
                                        containerColor = if (isSelected) LabSecondary else LabBorder,
                                        pressedColor = LabSecondaryPressed,
                                        contentColor = if (isSelected) Color.Black else Color.White,
                                        modifier = Modifier.testTag("preset_${preset.id}")
                                    ) {
                                        Text(if (isSelected) "ACTIVE" else "SELECT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Simplified Sensitivity 1..10
            Surface(
                color = LabSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LabBorder),
                modifier = Modifier.fillMaxWidth().testTag("simplified_sensitivity_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SIMPLIFIED SENSITIVITY (الحساسية العامة)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                        Text(
                            "LEVEL $simplifiedSensitivity / 10",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = LabPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Scale 1..10 automatically configures internal target and confidence thresholds:",
                        fontSize = 11.sp,
                        color = LabTextSecondary
                    )

                    Slider(
                        value = simplifiedSensitivity.toFloat(),
                        onValueChange = { viewModel.setSimplifiedSensitivity(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(thumbColor = LabPrimary, activeTrackColor = LabPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("sensitivity_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1: High Stability (Mineralized Soil)", fontSize = 9.sp, color = LabTextMuted)
                        Text("10: Maximum Depth", fontSize = 9.sp, color = LabTextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Guided 2-Step Calibration (Air -> Ground)
            Surface(
                color = LabSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LabBorder),
                modifier = Modifier.fillMaxWidth().testTag("guided_calibration_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LabSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GUIDED CALIBRATION (معايرة موجهة بخطوتين)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        guidedCalibrationMessage,
                        fontSize = 11.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TactileButton(
                            onClick = { viewModel.captureGuidedAir() },
                            containerColor = if (guidedCalibrationStep == 1) LabPrimary else LabSurfaceVariant,
                            pressedColor = LabPrimaryPressed,
                            contentColor = if (guidedCalibrationStep == 1) Color.Black else Color.White,
                            modifier = Modifier.weight(1f).testTag("guided_air_btn")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Air, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("1. التقاط الهواء", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        TactileButton(
                            onClick = { viewModel.captureGuidedGround() },
                            containerColor = if (guidedCalibrationStep == 2) LabSecondary else LabSurfaceVariant,
                            pressedColor = LabSecondaryPressed,
                            contentColor = if (guidedCalibrationStep == 2) Color.Black else Color.White,
                            modifier = Modifier.weight(1f).testTag("guided_ground_btn")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (groundCaptureProgress > 0) {
                                    CircularProgressIndicator(
                                        progress = { groundCaptureProgress / 100f },
                                        modifier = Modifier.size(14.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Landscape, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("2. التقاط الأرض", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        TactileOutlinedButton(
                            onClick = { viewModel.resetGuidedCalibration() },
                            borderColor = LabBorder,
                            pressedBorderColor = LabError,
                            accentColor = LabError,
                            modifier = Modifier.testTag("reset_guided_cal_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // ==========================================
        // DEVELOPER MODE (UNLOCKED) UI COMPONENTS
        // ==========================================
        if (isDeveloperMode) {
            // Simulation Lock and Engine Toggle
            Surface(
                color = LabSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LabTertiary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("dev_simulation_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SIGNAL SIMULATION GENERATOR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabTertiary)
                        Text(
                            "Synthesizes raw ETS packets for laboratory testing without physical hardware",
                            fontSize = 10.sp,
                            color = LabTextSecondary
                        )
                    }
                    Switch(
                        checked = isSimulation,
                        onCheckedChange = { viewModel.setSimulationMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LabTertiary,
                            checkedTrackColor = LabTertiary.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("simulation_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hardware Pulse Generation & Timing
            Surface(
                color = LabSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LabBorder),
                modifier = Modifier.fillMaxWidth().testTag("raw_timing_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("COIL PULSE TIMING & POWER (REGISTERS)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Pulse Frequency
                    ControlSlider(
                        label = "Pulse Rate",
                        value = pulseRateHz.toFloat(),
                        range = activeHardwareProfile.minFrequencyHz.toFloat()..activeHardwareProfile.maxFrequencyHz.toFloat(),
                        unit = "Hz",
                        onValueChange = { pulseRateHz = it.toInt() }
                    )

                    // Pulse Width
                    ControlSlider(
                        label = "Pulse Width",
                        value = pulseWidthUs.toFloat(),
                        range = activeHardwareProfile.minPulseUs.toFloat()..activeHardwareProfile.maxPulseUs.toFloat(),
                        unit = "µs",
                        onValueChange = { pulseWidthUs = it.toInt() }
                    )

                    // Delay Ticks
                    ControlSlider(
                        label = "Delay Ticks",
                        value = delayTicks.toFloat(),
                        range = activeHardwareProfile.minDelayTicks.toFloat()..activeHardwareProfile.maxDelayTicks.toFloat(),
                        unit = "ticks (%.1f µs)".format(delayTicks * 1.6),
                        onValueChange = { delayTicks = it.toInt() }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    TactileButton(
                        onClick = {
                            viewModel.setPulseRateHz(pulseRateHz)
                            viewModel.setPulseWidthUs(pulseWidthUs)
                            viewModel.setDelayTicks(delayTicks)
                        },
                        containerColor = LabPrimary,
                        pressedColor = LabPrimaryPressed,
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("apply_detector_hardware_btn")
                    ) {
                        Text("TRANSMIT HARDWARE CONFIGURATION", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Waveform Polarity (AFE Inverting/Non-inverting and Auto Detection)
            Surface(
                color = LabSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LabBorder),
                modifier = Modifier.fillMaxWidth().testTag("waveform_polarity_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WAVEFORM POLARITY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                        val activeSign = dspResult?.effectivePolarity?.sign ?: samplingConfig.polarity.sign
                        Text(
                            text = if (activeSign > 0) "ACTIVE: POSITIVE (+)" else "ACTIVE: NEGATIVE (-)",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (activeSign > 0) LabSecondary else LabTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Controls AFE decay normalization so eddy current decays are positive for regression.",
                        fontSize = 11.sp,
                        color = LabTextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Polarity Mode Selection:", fontSize = 11.sp, color = LabTextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PolarityMode.entries.forEach { mode ->
                            TactileChip(
                                selected = samplingConfig.polarityMode == mode,
                                onClick = { viewModel.setPolarityMode(mode) },
                                label = mode.displayName,
                                activeColor = LabPrimary,
                                modifier = Modifier.weight(1f).testTag("polarity_mode_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Auto-detection status display
                    Surface(
                        color = LabSurfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, LabBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val detectedPol = polarityDetectionResult.detectedPolarity
                                val detectedStr = when (detectedPol) {
                                    WaveformPolarity.POSITIVE -> "Positive"
                                    WaveformPolarity.NEGATIVE -> "Negative"
                                    null -> "Unknown"
                                }
                                Text(
                                    text = "Detected: $detectedStr",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (detectedPol) {
                                        WaveformPolarity.POSITIVE -> LabSecondary
                                        WaveformPolarity.NEGATIVE -> LabTertiary
                                        null -> LabTextSecondary
                                    }
                                )

                                val qualityColor = when (polarityDetectionResult.quality) {
                                    PolarityDetectionQuality.HIGH -> LabSecondary
                                    PolarityDetectionQuality.MEDIUM -> LabPrimary
                                    PolarityDetectionQuality.LOW -> LabTertiary
                                    PolarityDetectionQuality.UNKNOWN, PolarityDetectionQuality.NONE -> LabTextSecondary
                                }
                                Text(
                                    text = "Quality: ${polarityDetectionResult.quality.displayName}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = qualityColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = polarityDetectionResult.reason,
                                fontSize = 10.sp,
                                color = LabTextSecondary
                            )

                            if (polarityDetectionResult.detectedPolarity != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "+Fit: R²=%.2f (pts=%d) | -Fit: R²=%.2f (pts=%d)".format(
                                        polarityDetectionResult.positiveR2,
                                        polarityDetectionResult.positiveValidSamples,
                                        polarityDetectionResult.negativeR2,
                                        polarityDetectionResult.negativeValidSamples
                                    ),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = LabTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TactileOutlinedButton(
                            onClick = { viewModel.runPolarityDetection() },
                            borderColor = LabBorder,
                            pressedBorderColor = LabPrimary,
                            accentColor = LabPrimary,
                            modifier = Modifier.weight(1f).testTag("detect_polarity_btn")
                        ) {
                            Text("DETECT POLARITY", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        TactileButton(
                            onClick = { viewModel.applyDetectedPolarity() },
                            enabled = polarityDetectionResult.detectedPolarity != null,
                            containerColor = LabSecondary,
                            pressedColor = LabSecondaryPressed,
                            contentColor = Color.Black,
                            modifier = Modifier.weight(1f).testTag("apply_detected_polarity_btn")
                        ) {
                            Text("APPLY DETECTED", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Detection & Discrimination Thresholds
            Surface(
                color = LabSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LabBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("DETECTION & DISCRIMINATION THRESHOLDS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabTertiary)
                    Spacer(modifier = Modifier.height(8.dp))

                    ControlSlider(
                        label = "Target Threshold",
                        value = targetThreshold,
                        range = 10f..80f,
                        unit = "/ 100",
                        onValueChange = {
                            targetThreshold = it
                            viewModel.updateProfile(activeProfile.copy(targetThreshold = it.toDouble()))
                        }
                    )

                    ControlSlider(
                        label = "Confidence Min",
                        value = confidenceThreshold,
                        range = 10f..80f,
                        unit = "%",
                        onValueChange = {
                            confidenceThreshold = it
                            viewModel.updateProfile(activeProfile.copy(confidenceThreshold = it.toDouble()))
                        }
                    )

                    ControlSlider(
                        label = "Iron Reject",
                        value = ironRejectThreshold,
                        range = 20f..90f,
                        unit = "/ 100",
                        onValueChange = {
                            ironRejectThreshold = it
                            viewModel.updateProfile(activeProfile.copy(ironRejectThreshold = it.toDouble()))
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Audio Synthesizer Mode & Haptics (available in both modes)
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("ACOUSTIC FEEDBACK & HAPTICS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                ControlSlider(
                    label = "Audio Threshold",
                    value = audioThreshold,
                    range = 5f..80f,
                    unit = "/ 100",
                    onValueChange = {
                        audioThreshold = it
                        audioManager.audioThreshold = it.toDouble()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Sound Mode:", fontSize = 11.sp, color = LabTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AudioMode.entries.forEach { m ->
                        FilterChip(
                            selected = audioManager.mode == m,
                            onClick = { audioManager.mode = m },
                            label = { Text(m.displayName, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic Vibration Click:", fontSize = 12.sp, color = Color.White)
                    Switch(
                        checked = hapticState,
                        onCheckedChange = {
                            hapticState = it
                            audioManager.hapticEnabled = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = LabSecondary)
                    )
                }
            }
        }

        // ==========================================
        // DIALOG: EXPORT & SHARE PROFILE
        // ==========================================
        if (showExportProfileDialog) {
            val jsonPayload = viewModel.exportCurrentProfileJson(exportProfileName, exportProfileDescription)

            AlertDialog(
                onDismissRequest = { showExportProfileDialog = false },
                containerColor = LabSurface,
                title = {
                    Text("Export & Share Profile", color = LabSecondary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    Column {
                        Text("Give your profile setup a recognizable name:", fontSize = 12.sp, color = LabTextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = exportProfileName,
                            onValueChange = { exportProfileName = it },
                            label = { Text("Profile Name") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = exportProfileDescription,
                            onValueChange = { exportProfileDescription = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Current parameters: ${currentBlock.pulseRate} Hz, ${currentBlock.pulseWidthUs} µs, ${samplingConfig.delayTicks} ticks delay", fontSize = 10.sp, color = LabPrimary)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            ProfileJsonSerializer.shareText(
                                context = context,
                                title = "FelezJoo Profile: $exportProfileName",
                                textContent = jsonPayload
                            )
                            showExportProfileDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LabSecondary, contentColor = Color.Black)
                    ) {
                        Text("SHARE / SEND")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(jsonPayload))
                            Toast.makeText(context, "Profile JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                            showExportProfileDialog = false
                        }
                    ) {
                        Text("COPY JSON", color = LabPrimary)
                    }
                }
            )
        }

        // ==========================================
        // DIALOG: IMPORT PROFILE
        // ==========================================
        if (showImportProfileDialog) {
            AlertDialog(
                onDismissRequest = { showImportProfileDialog = false },
                containerColor = LabSurface,
                title = {
                    Text("Import Profile JSON", color = LabPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    Column {
                        Text("Paste the shared profile JSON code below to restore settings:", fontSize = 12.sp, color = LabTextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = importJsonText,
                            onValueChange = { importJsonText = it },
                            placeholder = { Text("{\"profileName\": ... }", color = LabTextMuted) },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            maxLines = 8
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (importJsonText.isNotBlank()) {
                                val success = viewModel.importProfileJson(importJsonText)
                                if (success) {
                                    Toast.makeText(context, "Profile imported & applied!", Toast.LENGTH_LONG).show()
                                    showImportProfileDialog = false
                                } else {
                                    Toast.makeText(context, "Invalid profile format!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LabPrimary, contentColor = Color.Black)
                    ) {
                        Text("APPLY PROFILE")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showImportProfileDialog = false }) {
                        Text("CANCEL", color = LabTextSecondary)
                    }
                }
            )
        }

        // ==========================================
        // DIALOG: ADD NEW HARDWARE BOARD
        // ==========================================
        if (showAddHardwareDialog) {
            AlertDialog(
                onDismissRequest = { showAddHardwareDialog = false },
                containerColor = LabSurface,
                title = {
                    Text("Add Custom Board Profile", color = LabTertiary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Define safety parameters for custom MCU/DSP hardware:", fontSize = 11.sp, color = LabTextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = newBoardName,
                            onValueChange = { newBoardName = it },
                            label = { Text("Board Name") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = newBoardMcu,
                            onValueChange = { newBoardMcu = it },
                            label = { Text("MCU Architecture") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = newBoardMinFreq,
                                onValueChange = { newBoardMinFreq = it },
                                label = { Text("Min Hz") },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(color = Color.White, fontSize = 11.sp)
                            )
                            OutlinedTextField(
                                value = newBoardMaxFreq,
                                onValueChange = { newBoardMaxFreq = it },
                                label = { Text("Max Hz") },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(color = Color.White, fontSize = 11.sp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = newBoardMinPulse,
                                onValueChange = { newBoardMinPulse = it },
                                label = { Text("Min Pulse (µs)") },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(color = Color.White, fontSize = 11.sp)
                            )
                            OutlinedTextField(
                                value = newBoardMaxPulse,
                                onValueChange = { newBoardMaxPulse = it },
                                label = { Text("Max Pulse (µs)") },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(color = Color.White, fontSize = 11.sp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = newBoardMinDelay,
                                onValueChange = { newBoardMinDelay = it },
                                label = { Text("Min Delay") },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(color = Color.White, fontSize = 11.sp)
                            )
                            OutlinedTextField(
                                value = newBoardMaxDelay,
                                onValueChange = { newBoardMaxDelay = it },
                                label = { Text("Max Delay") },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(color = Color.White, fontSize = 11.sp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newBoardName.isNotBlank()) {
                                val boardId = "board_${System.currentTimeMillis() % 100000}"
                                val newBoard = HardwareBoardProfile(
                                    id = boardId,
                                    name = newBoardName,
                                    mcu = newBoardMcu,
                                    clockFrequencyMhz = newBoardFreqMhz.toIntOrNull() ?: 16,
                                    minFrequencyHz = newBoardMinFreq.toIntOrNull() ?: 30,
                                    maxFrequencyHz = newBoardMaxFreq.toIntOrNull() ?: 800,
                                    minPulseUs = newBoardMinPulse.toIntOrNull() ?: 20,
                                    maxPulseUs = newBoardMaxPulse.toIntOrNull() ?: 350,
                                    minDelayTicks = newBoardMinDelay.toIntOrNull() ?: 2,
                                    maxDelayTicks = newBoardMaxDelay.toIntOrNull() ?: 100,
                                    delayUnitUs = 1.0,
                                    sampleSpacingUs = 1.0,
                                    nominalAdcResolutionBits = 12,
                                    effectiveAdcResolutionBits = 10.5,
                                    adcInputVoltageRange = "0V - 3.3V",
                                    digitalTxOutputLevel = "3.3V / 5V",
                                    sampleCount = 70,
                                    notes = newBoardNotes,
                                    isUserEditable = true
                                )
                                viewModel.addNewHardwareProfile(newBoard)
                                Toast.makeText(context, "Added hardware profile ${newBoard.name}", Toast.LENGTH_SHORT).show()
                                showAddHardwareDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LabTertiary, contentColor = Color.Black)
                    ) {
                        Text("SAVE BOARD")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showAddHardwareDialog = false }) {
                        Text("CANCEL", color = LabTextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun ControlSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = LabTextSecondary, modifier = Modifier.width(120.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = LabPrimary, activeTrackColor = LabPrimary),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "%.0f %s".format(value, unit),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            modifier = Modifier.width(65.dp)
        )
    }
}
