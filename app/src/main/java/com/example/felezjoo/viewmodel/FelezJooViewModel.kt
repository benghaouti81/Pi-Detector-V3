package com.example.felezjoo.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.felezjoo.audio.AudioMode
import com.example.felezjoo.audio.DetectorAudioManager
import com.example.felezjoo.diagnostics.RawPacketMonitorManager
import com.example.felezjoo.diagnostics.SystemDiagnostics
import com.example.felezjoo.dsp.DspCalculationResult
import com.example.felezjoo.dsp.DspPipeline
import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.models.DetectorCapabilities
import com.example.felezjoo.models.DeviceInfo
import com.example.felezjoo.models.DspProfile
import com.example.felezjoo.models.PolarityDetectionQuality
import com.example.felezjoo.models.PolarityDetectionResult
import com.example.felezjoo.models.PolarityMode
import com.example.felezjoo.models.SamplingConfiguration
import com.example.felezjoo.models.TargetClassification
import com.example.felezjoo.models.TargetEvent
import com.example.felezjoo.models.WaveformPolarity
import com.example.felezjoo.protocol.ProtocolParser
import com.example.felezjoo.serial.CommandConsoleManager
import com.example.felezjoo.serial.SerialDirection
import com.example.felezjoo.serial.SerialMonitorManager
import com.example.felezjoo.simulation.SimulationEngine
import com.example.felezjoo.simulation.SimulationTargetType
import com.example.felezjoo.storage.AppDatabase
import com.example.felezjoo.storage.DataExportHelper
import com.example.felezjoo.storage.DecayBlockEntity
import com.example.felezjoo.storage.ReplayEngine
import com.example.felezjoo.storage.SessionEntity
import com.example.felezjoo.storage.TargetEventEntity
import com.example.felezjoo.ui.Screen
import com.example.felezjoo.usb.UsbConnectionState
import com.example.felezjoo.usb.UsbDriverDetector
import com.example.felezjoo.usb.UsbStatistics
import com.example.felezjoo.usb.UsbTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.felezjoo.models.HardwareBoardProfile
import com.example.felezjoo.models.OperationalPreset
import com.example.felezjoo.storage.ProfileExportPackage
import com.example.felezjoo.storage.ProfileJsonSerializer
import com.example.felezjoo.storage.SettingsPreferencesManager

class FelezJooViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val database = AppDatabase.getInstance(context)
    val preferencesManager = SettingsPreferencesManager(context)

    // Current Navigation Screen
    private val _currentScreen = MutableStateFlow(Screen.DASHBOARD)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // Role Mode: Developer vs User
    private val _isDeveloperMode = MutableStateFlow(preferencesManager.getDeveloperMode(false))
    val isDeveloperMode: StateFlow<Boolean> = _isDeveloperMode.asStateFlow()

    fun setDeveloperMode(enabled: Boolean) {
        _isDeveloperMode.value = enabled
        preferencesManager.saveDeveloperMode(enabled)
        if (!enabled && _isSimulationMode.value) {
            setSimulationMode(false)
        }
    }

    // Hardware Board Profiles
    private val _availableHardwareProfiles = MutableStateFlow<List<HardwareBoardProfile>>(emptyList())
    val availableHardwareProfiles: StateFlow<List<HardwareBoardProfile>> = _availableHardwareProfiles.asStateFlow()

    private val _activeHardwareProfile = MutableStateFlow(HardwareBoardProfile.LEONARDO_DEFAULT)
    val activeHardwareProfile: StateFlow<HardwareBoardProfile> = _activeHardwareProfile.asStateFlow()

    fun selectHardwareProfile(profile: HardwareBoardProfile) {
        _activeHardwareProfile.value = profile
        preferencesManager.saveHardwareProfileId(profile.id)
        val currentBlockVal = _currentBlock.value
        setPulseRateHz(currentBlockVal.pulseRate)
        setPulseWidthUs(currentBlockVal.pulseWidthUs)
        setDelayTicks(_samplingConfig.value.delayTicks)
        SystemDiagnostics.info("Hardware", "Selected hardware profile: ${profile.name}")
    }

    fun setHardwareBoardProfile(profile: HardwareBoardProfile) = selectHardwareProfile(profile)

    fun addNewHardwareProfile(profile: HardwareBoardProfile) {
        val current = _availableHardwareProfiles.value.toMutableList()
        current.removeAll { it.id == profile.id }
        current.add(profile)
        _availableHardwareProfiles.value = current
        preferencesManager.saveCustomHardwareProfiles(current)
        selectHardwareProfile(profile)
    }

    fun deleteCustomHardwareProfile(profileId: String) {
        val current = _availableHardwareProfiles.value.toMutableList()
        current.removeAll { it.id == profileId && it.isUserEditable }
        _availableHardwareProfiles.value = current
        preferencesManager.saveCustomHardwareProfiles(current)
        if (_activeHardwareProfile.value.id == profileId) {
            selectHardwareProfile(HardwareBoardProfile.LEONARDO_DEFAULT)
        }
    }

    // Operational Presets
    private val _availablePresets = MutableStateFlow(OperationalPreset.PRESETS)
    val availablePresets: StateFlow<List<OperationalPreset>> = _availablePresets.asStateFlow()

    private val _activePreset = MutableStateFlow(OperationalPreset.FAST_SCAN)
    val activePreset: StateFlow<OperationalPreset> = _activePreset.asStateFlow()

    fun applyOperationalPreset(preset: OperationalPreset) {
        _activePreset.value = preset
        preferencesManager.saveOperationalPresetId(preset.id)
        val profile = _activeHardwareProfile.value
        val safeFreq = profile.clampFrequency(preset.frequencyHz)
        val safePulse = profile.clampPulse(preset.pulseWidthUs)
        val safeDelay = profile.clampDelay(preset.delayTicks)

        setPulseRateHz(safeFreq)
        setPulseWidthUs(safePulse)
        setDelayTicks(safeDelay)
        SystemDiagnostics.info("Preset", "Applied preset '${preset.nameEn}' (Freq=$safeFreq, Pulse=$safePulse, Delay=$safeDelay)")
    }

    // User Mode Simplified Sensitivity (1..10) mapping to DSP thresholds
    private val _simplifiedSensitivity = MutableStateFlow(preferencesManager.getSensitivity(5))
    val simplifiedSensitivity: StateFlow<Int> = _simplifiedSensitivity.asStateFlow()

    fun setSimplifiedSensitivity(level: Int) {
        val clamped = level.coerceIn(1, 10)
        _simplifiedSensitivity.value = clamped
        preferencesManager.saveSensitivity(clamped)
        val targetThresh = (63.5 - clamped * 3.5).coerceIn(25.0, 60.0)
        val confThresh = (69.5 - clamped * 4.5).coerceIn(20.0, 65.0)
        updateProfile(_activeProfile.value.copy(
            targetThreshold = targetThresh,
            confidenceThreshold = confThresh
        ))
    }

    // Guided 2-Step Air/Ground Calibration for User Mode
    private val _guidedCalibrationStep = MutableStateFlow(1) // 1: Air prompt, 2: Ground prompt, 3: Completed
    val guidedCalibrationStep: StateFlow<Int> = _guidedCalibrationStep.asStateFlow()

    private val _guidedCalibrationMessage = MutableStateFlow("الخطوة 1: ارفع ملف الكاشف في الهواء بعيدًا عن أي معدن، ثم اضغط 'التقاط الهواء'")
    val guidedCalibrationMessage: StateFlow<String> = _guidedCalibrationMessage.asStateFlow()

    fun startGuidedCalibration() {
        _guidedCalibrationStep.value = 1
        _guidedCalibrationMessage.value = "الخطوة 1: ارفع ملف الكاشف في الهواء بعيدًا عن أي معدن، ثم اضغط 'التقاط الهواء'"
    }

    fun executeGuidedStep1Air() {
        captureAirBaseline()
        _guidedCalibrationStep.value = 2
        _guidedCalibrationMessage.value = "الخطوة 2: ضع ملف الكاشف قرب سطح التربة المراد البحث فيها، ثم اضغط 'التقاط الأرض'"
    }

    fun executeGuidedStep2Ground() {
        captureGround()
        _guidedCalibrationStep.value = 3
        _guidedCalibrationMessage.value = "تمت المعايرة بنجاح! تم حفظ خط الأساس الهوائي ونموذج التربة."
    }

    fun captureGuidedAir() = executeGuidedStep1Air()
    fun captureGuidedGround() = executeGuidedStep2Ground()

    fun resetGuidedCalibration() {
        _guidedCalibrationStep.value = 1
        _guidedCalibrationMessage.value = "الخطوة 1: ارفع ملف الكاشف في الهواء بعيدًا عن أي معدن، ثم اضغط 'التقاط الهواء'"
    }

    // Hardware & Device State
    private val _samplingConfig = MutableStateFlow(SamplingConfiguration())
    val samplingConfig: StateFlow<SamplingConfiguration> = _samplingConfig.asStateFlow()

    private val _activeProfile = MutableStateFlow(DspProfile.STABLE)
    val activeProfile: StateFlow<DspProfile> = _activeProfile.asStateFlow()

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    private val _capabilities = MutableStateFlow(DetectorCapabilities())
    val capabilities: StateFlow<DetectorCapabilities> = _capabilities.asStateFlow()

    // Real USB Subsystem
    val serialMonitor = SerialMonitorManager()
    val packetMonitor = RawPacketMonitorManager()
    lateinit var commandConsole: CommandConsoleManager
    val dspPipeline = DspPipeline()
    val audioManager = DetectorAudioManager(context, viewModelScope)
    val replayEngine = ReplayEngine(viewModelScope) { onBlockReceived(it) }

    // Single-consumer sequential frame channel to prevent parallel race conditions
    private val incomingBlockChannel = Channel<DecayBlock>(Channel.BUFFERED)

    private val _usbState = MutableStateFlow(UsbConnectionState.DISCONNECTED)
    val usbState: StateFlow<UsbConnectionState> = _usbState.asStateFlow()

    private val _usbStatusMessage = MutableStateFlow("USB Disconnected")
    val usbStatusMessage: StateFlow<String> = _usbStatusMessage.asStateFlow()

    private val _availableUsbDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val availableUsbDevices: StateFlow<List<UsbDevice>> = _availableUsbDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<UsbDevice?>(null)
    val selectedDevice: StateFlow<UsbDevice?> = _selectedDevice.asStateFlow()

    val usbStats = UsbStatistics()

    // Simulation Subsystem (Locked to developer mode, defaults to false)
    lateinit var simulationEngine: SimulationEngine
    private val _isSimulationMode = MutableStateFlow(false) // Default to false
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    fun setSimulationMode(enabled: Boolean) {
        if (enabled && !_isDeveloperMode.value) {
            SystemDiagnostics.warn("Simulation", "Simulation mode cannot be activated outside Developer Mode")
            return
        }
        _isSimulationMode.value = enabled
        if (enabled) {
            simulationEngine.start()
            SystemDiagnostics.info("Simulation", "Simulation engine started")
        } else {
            simulationEngine.stop()
            SystemDiagnostics.info("Simulation", "Simulation engine stopped")
        }
    }

    fun toggleSimulationMode() {
        setSimulationMode(!_isSimulationMode.value)
    }

    // Streaming & Recording
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _activeSession = MutableStateFlow<SessionEntity?>(null)
    val activeSession: StateFlow<SessionEntity?> = _activeSession.asStateFlow()

    // Live DSP Results
    private val _currentBlock = MutableStateFlow(DecayBlock())
    val currentBlock: StateFlow<DecayBlock> = _currentBlock.asStateFlow()

    private val _dspResult = MutableStateFlow<DspCalculationResult?>(null)
    val dspResult: StateFlow<DspCalculationResult?> = _dspResult.asStateFlow()

    private val _historyScores = MutableStateFlow<List<Double>>(emptyList())
    val historyScores: StateFlow<List<Double>> = _historyScores.asStateFlow()

    private val _historySnr = MutableStateFlow<List<Double>>(emptyList())
    val historySnr: StateFlow<List<Double>> = _historySnr.asStateFlow()

    private val _historyNoise = MutableStateFlow<List<Double>>(emptyList())
    val historyNoise: StateFlow<List<Double>> = _historyNoise.asStateFlow()

    private val _targetEvents = MutableStateFlow<List<TargetEvent>>(emptyList())
    val targetEvents: StateFlow<List<TargetEvent>> = _targetEvents.asStateFlow()

    private val _autoDelayResult = MutableStateFlow<Triple<Int, Double, Double>?>(null)
    val autoDelayResult: StateFlow<Triple<Int, Double, Double>?> = _autoDelayResult.asStateFlow()

    // Waveform Polarity Detection and Configuration State
    private val _polarityDetectionResult = MutableStateFlow<PolarityDetectionResult>(PolarityDetectionResult.UNKNOWN)
    val polarityDetectionResult: StateFlow<PolarityDetectionResult> = _polarityDetectionResult.asStateFlow()

    private val _groundCaptureProgress = MutableStateFlow(0)
    val groundCaptureProgress: StateFlow<Int> = _groundCaptureProgress.asStateFlow()

    private val protocolParser = ProtocolParser(
        onDecayBlockParsed = { block -> onBlockReceived(block) },
        onRawPacketRecord = { record ->
            packetMonitor.addRecord(record)
            if (record.isValid) {
                usbStats.validPackets++
            } else {
                usbStats.invalidPackets++
            }
        },
        onAsciiLineParsed = { line ->
            serialMonitor.addEntry(SerialDirection.RX, line)
            commandConsole.onResponseReceived(line)
        },
        onSequenceGapDetected = { exp, act ->
            usbStats.sequenceGaps++
            SystemDiagnostics.warn("Protocol", "Sequence gap detected: expected $exp, actual $act")
        },
        onCrcErrorDetected = { exp, act ->
            usbStats.crcErrors++
            SystemDiagnostics.warn("Protocol", "CRC mismatch: expected 0x%04X, calculated 0x%04X".format(act, exp))
        }
    )

    private val usbTransport = UsbTransport(
        context = context,
        coroutineScope = viewModelScope,
        onStateChanged = { state, msg ->
            _usbState.value = state
            _usbStatusMessage.value = msg
            SystemDiagnostics.info("USB", "$state: $msg")
            if (state == UsbConnectionState.DISCONNECTED) {
                audioManager.triggerDisconnectHaptic()
            }
        },
        onBytesReceived = { bytes, len ->
            serialMonitor.addBytes(SerialDirection.RX, bytes, len)
            protocolParser.processIncomingBytes(bytes, len)
        },
        onBytesSent = { bytes, len ->
            serialMonitor.addBytes(SerialDirection.TX, bytes, len)
        },
        onError = { err ->
            SystemDiagnostics.error("USB", err)
        }
    )

    private val ACTION_USB_PERMISSION = "com.example.felezjoo.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == ACTION_USB_PERMISSION) {
                synchronized(this) {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null) {
                        if (granted) {
                            _usbState.value = UsbConnectionState.PERMISSION_GRANTED
                            _usbStatusMessage.value = "Permission granted for ${device.deviceName}"
                            connectUsb(device)
                        } else {
                            _usbState.value = UsbConnectionState.ERROR
                            _usbStatusMessage.value = "USB permission denied by user"
                        }
                    }
                }
            } else if (action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (device != null && device == _selectedDevice.value) {
                    disconnectUsb()
                    _usbState.value = UsbConnectionState.DEVICE_REMOVED
                    _usbStatusMessage.value = "USB device disconnected"
                }
                refreshUsbDevices()
            } else if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                refreshUsbDevices()
            }
        }
    }

    init {
        // Single-consumer sequential processor for incoming decay blocks to eliminate race conditions
        viewModelScope.launch(Dispatchers.Default) {
            for (block in incomingBlockChannel) {
                processBlockSequentially(block)
            }
        }

        commandConsole = CommandConsoleManager(viewModelScope) { cmd ->
            if (_isSimulationMode.value) {
                handleSimulationCommand(cmd)
                true
            } else {
                usbTransport.sendAscii(cmd)
            }
        }

        simulationEngine = SimulationEngine(viewModelScope) { block, packetBytes ->
            if (_isSimulationMode.value && _isStreaming.value) {
                // Pipe simulated block into the same architecture
                serialMonitor.addBytes(SerialDirection.RX, packetBytes, packetBytes.size)
                protocolParser.processIncomingBytes(packetBytes, packetBytes.size)
            }
        }

        // Register USB broadcast receiver safely
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }

        refreshUsbDevices()

        // Load and initialize persisted settings and custom hardware profiles
        loadInitialPersistedSettings()

        // Generate initial demo block so Signal Lab immediately shows realistic waveform
        generateDemoDecayBlock()
    }

    private fun loadInitialPersistedSettings() {
        try {
            // Load custom hardware boards
            val customHardware = preferencesManager.loadCustomHardwareProfiles()
            val allBoards = HardwareBoardProfile.BUILT_IN_PROFILES + customHardware
            _availableHardwareProfiles.value = allBoards

            val savedBoardId = preferencesManager.getHardwareProfileId("leonardo_atmega32u4")
            val matchingBoard = allBoards.find { it.id == savedBoardId } ?: HardwareBoardProfile.LEONARDO_DEFAULT
            _activeHardwareProfile.value = matchingBoard

            // Load saved pulse & delay timings
            val savedRate = matchingBoard.clampFrequency(preferencesManager.getPulseRate(200))
            val savedPulse = matchingBoard.clampPulse(preferencesManager.getPulseWidth(150))
            val savedDelay = matchingBoard.clampDelay(preferencesManager.getDelayTicks(10))

            _currentBlock.value = _currentBlock.value.copy(
                pulseRate = savedRate,
                pulseWidthUs = savedPulse,
                delayTicks = savedDelay
            )
            _samplingConfig.value = _samplingConfig.value.copy(
                delayTicks = savedDelay,
                polarity = preferencesManager.getWaveformPolarity(),
                polarityMode = preferencesManager.getPolarityMode()
            )

            // Load DSP profile & thresholds
            val savedDspId = preferencesManager.getDspProfileId("profile_stable")
            val baseDsp = DspProfile.BUILT_IN_PROFILES.find { it.id == savedDspId } ?: DspProfile.STABLE
            val restoredDsp = baseDsp.copy(
                targetThreshold = preferencesManager.getTargetThreshold(baseDsp.targetThreshold.toFloat()).toDouble(),
                confidenceThreshold = preferencesManager.getConfidenceThreshold(baseDsp.confidenceThreshold.toFloat()).toDouble(),
                ironRejectThreshold = preferencesManager.getIronRejectThreshold(baseDsp.ironRejectThreshold.toFloat()).toDouble(),
                audioThreshold = preferencesManager.getAudioThreshold(baseDsp.audioThreshold.toFloat()).toDouble()
            )
            _activeProfile.value = restoredDsp

            // Load Audio settings
            audioManager.audioThreshold = restoredDsp.audioThreshold
            audioManager.mode = preferencesManager.getAudioMode()
            audioManager.isMuted = preferencesManager.getAudioMuted()
            audioManager.hapticEnabled = preferencesManager.getHapticEnabled()

            // Load operational preset
            val savedPresetId = preferencesManager.getOperationalPresetId("fast_scan")
            val matchingPreset = OperationalPreset.PRESETS.find { it.id == savedPresetId } ?: OperationalPreset.FAST_SCAN
            _activePreset.value = matchingPreset
        } catch (e: Exception) {
            SystemDiagnostics.error("Settings", "Failed restoring saved settings: ${e.message}")
        }
    }

    fun refreshUsbDevices() {
        try {
            val list = usbManager.deviceList.values.toList()
            _availableUsbDevices.value = list
            if (_selectedDevice.value == null && list.isNotEmpty()) {
                _selectedDevice.value = list.first()
            }
        } catch (e: Exception) {
            SystemDiagnostics.error("USB", "Device enumeration error: ${e.message}")
        }
    }

    fun selectUsbDevice(device: UsbDevice?) {
        _selectedDevice.value = device
    }

    fun requestUsbPermission(device: UsbDevice) {
        _selectedDevice.value = device
        if (usbManager.hasPermission(device)) {
            connectUsb(device)
        } else {
            _usbState.value = UsbConnectionState.PERMISSION_REQUESTED
            _usbStatusMessage.value = "Requesting USB permission..."
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    fun connectUsb(device: UsbDevice, baudRate: Int = 115200) {
        viewModelScope.launch {
            _isSimulationMode.value = false
            simulationEngine.stop()

            val success = usbTransport.connect(device, baudRate)
            if (success) {
                // Handshake sequence
                performHandshake()
            }
        }
    }

    fun disconnectUsb() {
        usbTransport.disconnect()
        _isStreaming.value = false
    }

    private suspend fun performHandshake() {
        _usbState.value = UsbConnectionState.HANDSHAKE
        _usbStatusMessage.value = "Negotiating handshake with MCU..."

        // 1. PING
        commandConsole.send("PING")
        delay(150L)

        // 2. CONFIG?
        commandConsole.send("CONFIG?")
        delay(150L)

        // 3. STATUS
        commandConsole.send("STATUS")
        delay(100L)

        _usbState.value = UsbConnectionState.READY
        _usbStatusMessage.value = "Detector Ready (Click START to stream)"
    }

    fun startStreaming() {
        _isStreaming.value = true
        if (_isSimulationMode.value) {
            simulationEngine.start()
        } else {
            viewModelScope.launch {
                commandConsole.send("START")
                _usbState.value = UsbConnectionState.STREAMING
            }
        }
    }

    fun stopStreaming() {
        _isStreaming.value = false
        if (_isSimulationMode.value) {
            simulationEngine.stop()
        } else {
            viewModelScope.launch {
                commandConsole.send("STOP")
                _usbState.value = UsbConnectionState.READY
            }
        }
    }

    fun toggleSimulation(enabled: Boolean) {
        _isSimulationMode.value = enabled
        if (enabled) {
            disconnectUsb()
            _usbState.value = UsbConnectionState.DISCONNECTED
            _usbStatusMessage.value = "Simulation Mode Active"
        }
    }

    private fun handleSimulationCommand(cmd: String) {
        val upper = cmd.trim().uppercase()
        when {
            upper == "PING" -> serialMonitor.addEntry(SerialDirection.RX, "PONG (Simulation)")
            upper == "CONFIG?" -> {
                val cfg = "#CONFIG:70,1600,10,ETS,14,5"
                protocolParser.parseConfigLine(cfg)
                serialMonitor.addEntry(SerialDirection.RX, cfg)
            }
            upper == "STATUS" -> serialMonitor.addEntry(SerialDirection.RX, "#STATUS:MODE=SIM,TX=ON,ETS=ACTIVE,FPS=14")
            upper == "START" -> startStreaming()
            upper == "STOP" -> stopStreaming()
            upper.startsWith("SET:DELAY=") -> {
                val ticks = upper.substringAfter("=").toIntOrNull() ?: 10
                setDelayTicks(ticks)
                serialMonitor.addEntry(SerialDirection.RX, "OK: DELAY=$ticks")
            }
            upper.startsWith("SET:PULSE=") -> {
                val us = upper.substringAfter("=").toIntOrNull() ?: 150
                serialMonitor.addEntry(SerialDirection.RX, "OK: PULSE=$us")
            }
            upper.startsWith("SET:FREQ=") -> {
                val hz = upper.substringAfter("=").toIntOrNull() ?: 200
                serialMonitor.addEntry(SerialDirection.RX, "OK: FREQ=$hz")
            }
            else -> serialMonitor.addEntry(SerialDirection.RX, "OK (Simulation)")
        }
    }

    private fun onBlockReceived(block: DecayBlock) {
        incomingBlockChannel.trySend(block)
    }

    private suspend fun processBlockSequentially(block: DecayBlock) {
        val startMs = System.currentTimeMillis()
        // Ensure block inherits user's active sampling configuration (including polarity & polarityMode)
        val activeCfg = _samplingConfig.value
        val effectiveBlock = if (block.samplingConfiguration.polarity != activeCfg.polarity ||
            block.samplingConfiguration.polarityMode != activeCfg.polarityMode) {
            block.copy(
                samplingConfiguration = block.samplingConfiguration.copy(
                    polarity = activeCfg.polarity,
                    polarityMode = activeCfg.polarityMode
                ),
                polarity = activeCfg.polarity,
                polarityMode = activeCfg.polarityMode
            )
        } else {
            block
        }
        _currentBlock.value = effectiveBlock

        // Run DSP Pipeline
        val result = dspPipeline.processBlock(effectiveBlock, _activeProfile.value)
        val dspDuration = System.currentTimeMillis() - startMs
        SystemDiagnostics.lastDspDurationMs = dspDuration

        _dspResult.value = result
        if (result.polarityDetectionResult.quality != PolarityDetectionQuality.NONE) {
            _polarityDetectionResult.value = result.polarityDetectionResult
        }

        // Update rolling history
        val currentScores = _historyScores.value.toMutableList()
        val currentSnr = _historySnr.value.toMutableList()
        val currentNoise = _historyNoise.value.toMutableList()

        currentScores.add(result.featureVector.targetScore)
        currentSnr.add(result.featureVector.snr)
        currentNoise.add(result.featureVector.noise)

        if (currentScores.size > 64) currentScores.removeAt(0)
        if (currentSnr.size > 64) currentSnr.removeAt(0)
        if (currentNoise.size > 64) currentNoise.removeAt(0)

        _historyScores.value = currentScores
        _historySnr.value = currentSnr
        _historyNoise.value = currentNoise

        // Update Non-blocking Audio
        audioManager.updateTargetState(
            targetScore = result.featureVector.targetScore,
            confidence = result.featureVector.targetConfidence,
            ironScore = result.featureVector.ironScore,
            targetId = result.featureVector.targetId,
            classification = result.targetClassification
        )

        // Track target events if valid target detected (aligned with actual classifications)
        if (result.targetClassification == TargetClassification.FERROUS_LIKELY ||
            result.targetClassification == TargetClassification.NON_FERROUS_LIKELY ||
            result.targetClassification == TargetClassification.STABLE_TARGET ||
            result.targetClassification == TargetClassification.NON_FERROUS ||
            result.targetClassification == TargetClassification.IRON
        ) {
            if (result.featureVector.targetScore > 50.0 && result.featureVector.targetConfidence > 50.0) {
                recordTargetEvent(result)
            }
        }

        // Save to database if recording
        if (_isRecording.value) {
            val session = _activeSession.value
            if (session != null) {
                val entity = DecayBlockEntity(
                    sessionId = session.id,
                    sequenceNumber = block.sequenceNumber,
                    timestamp = block.timestamp,
                    delayTicks = block.delayTicks,
                    delayUs = block.delayUs,
                    pulseRate = block.pulseRate,
                    pulseWidthUs = block.pulseWidthUs,
                    rawSamplesCsv = block.rawSamples.joinToString(","),
                    targetScore = result.featureVector.targetScore,
                    confidence = result.featureVector.targetConfidence,
                    ironScore = result.featureVector.ironScore,
                    targetId = result.featureVector.targetId,
                    classification = result.targetClassification.name
                )
                database.decayBlockDao().insertBlock(entity)
            }
        }
    }

    private fun recordTargetEvent(result: DspCalculationResult) {
        val events = _targetEvents.value.toMutableList()
        val now = System.currentTimeMillis()
        val recent = events.lastOrNull()

        if (recent != null && (now - recent.endTimeMs) < 1500) {
            // Continuation of same physical sweep event
            recent.endTimeMs = now
        } else {
            // New event
            val ev = TargetEvent(
                startTimeMs = now,
                endTimeMs = now,
                peakScore = result.featureVector.targetScore,
                peakConfidence = result.featureVector.targetConfidence,
                ironScore = result.featureVector.ironScore,
                targetId = result.featureVector.targetId,
                classification = result.targetClassification,
                bestFeatureVector = result.featureVector
            )
            events.add(ev)
            if (events.size > 50) events.removeAt(0)
            _targetEvents.value = events

            // Persist event to DB
            viewModelScope.launch(Dispatchers.IO) {
                val sessId = _activeSession.value?.id ?: "live"
                database.targetEventDao().insertEvent(
                    TargetEventEntity(
                        sessionId = sessId,
                        startTimeMs = ev.startTimeMs,
                        endTimeMs = ev.endTimeMs,
                        peakScore = ev.peakScore,
                        peakConfidence = ev.peakConfidence,
                        ironScore = ev.ironScore,
                        targetId = ev.targetId,
                        classification = ev.classification.name
                    )
                )
            }
        }
    }

    // Profile & DSP parameter changes
    fun updateProfile(newProfile: DspProfile) {
        _activeProfile.value = newProfile
        audioManager.audioThreshold = newProfile.audioThreshold
        preferencesManager.saveDspProfileId(newProfile.id)
        preferencesManager.saveDspThresholds(
            newProfile.targetThreshold,
            newProfile.confidenceThreshold,
            newProfile.ironRejectThreshold
        )
        _dspResult.value?.let { currentRes ->
            // Re-evaluate immediately with new profile
            val updated = dspPipeline.processBlock(currentRes.block, newProfile)
            _dspResult.value = updated
        }
    }

    fun setDelayTicks(ticks: Int) {
        val safeTicks = _activeHardwareProfile.value.clampDelay(ticks)
        preferencesManager.saveDelayTicks(safeTicks)
        _samplingConfig.value = _samplingConfig.value.copy(
            delayTicks = safeTicks
        )
        if (!_isSimulationMode.value) {
            commandConsole.send("SET:DELAY=$safeTicks")
        }
    }

    fun setPulseWidthUs(widthUs: Int) {
        val safeWidth = _activeHardwareProfile.value.clampPulse(widthUs)
        preferencesManager.savePulseWidth(safeWidth)
        _currentBlock.value = _currentBlock.value.copy(pulseWidthUs = safeWidth)
        if (!_isSimulationMode.value) {
            commandConsole.send("SET:PULSE=$safeWidth")
        }
    }

    fun setPulseRateHz(freqHz: Int) {
        val safeFreq = _activeHardwareProfile.value.clampFrequency(freqHz)
        preferencesManager.savePulseRate(safeFreq)
        _currentBlock.value = _currentBlock.value.copy(pulseRate = safeFreq)
        if (!_isSimulationMode.value) {
            commandConsole.send("SET:FREQ=$safeFreq")
        }
    }

    fun captureAirBaseline() {
        val res = _dspResult.value ?: return
        val samples = if (res.rawChronologicalCurve.isNotEmpty()) res.rawChronologicalCurve else res.filteredCurve
        dspPipeline.captureAirBaseline(samples)
        SystemDiagnostics.info("DSP", "Captured air baseline from raw chronological curve")
    }

    fun captureGround() {
        viewModelScope.launch {
            val res = _dspResult.value ?: return@launch
            for (p in 0..100 step 20) {
                _groundCaptureProgress.value = p
                delay(80L)
            }
            val groundSamples = if (res.airCompensatedCurve.isNotEmpty()) res.airCompensatedCurve else res.filteredCurve
            dspPipeline.setGroundDirect(groundSamples)
            _groundCaptureProgress.value = 100
            delay(200L)
            _groundCaptureProgress.value = 0
            SystemDiagnostics.info("DSP", "Ground captured successfully from raw air-compensated residual")
        }
    }

    fun resetGround() {
        dspPipeline.resetGround()
        SystemDiagnostics.info("DSP", "Ground reset")
    }

    fun runAutoDelayFinder() {
        val block = _currentBlock.value
        val res = dspPipeline.findAutoDelay(block.rawSamples, block.samplingConfiguration)
        _autoDelayResult.value = res
    }

    fun applyAutoDelay() {
        val candidate = _autoDelayResult.value ?: return
        val currentDelay = _samplingConfig.value.delayTicks
        val recommendedDelay = currentDelay + candidate.first
        val clampedDelay = _activeHardwareProfile.value.clampDelay(recommendedDelay)
        setDelayTicks(clampedDelay)
        SystemDiagnostics.info("DSP", "Applied auto delay: current=$currentDelay + offset=${candidate.first} -> $clampedDelay ticks")
    }

    // Polarity Mode & Detection Controls
    fun setPolarityMode(mode: PolarityMode) {
        val updatedConfig = _samplingConfig.value.copy(
            polarityMode = mode,
            polarity = when (mode) {
                PolarityMode.POSITIVE -> WaveformPolarity.POSITIVE
                PolarityMode.NEGATIVE -> WaveformPolarity.NEGATIVE
                PolarityMode.AUTO -> _samplingConfig.value.polarity
            }
        )
        _samplingConfig.value = updatedConfig
        protocolParser.updateSamplingConfig(updatedConfig)
        preferencesManager.savePolarityConfig(updatedConfig.polarity, mode)
        SystemDiagnostics.info("DSP", "Polarity mode set to ${mode.displayName}")

        // Immediately re-process current block with updated config
        val current = _currentBlock.value
        if (current.rawSamples.isNotEmpty()) {
            val updatedBlock = current.copy(
                samplingConfiguration = updatedConfig,
                polarity = updatedConfig.polarity,
                polarityMode = updatedConfig.polarityMode
            )
            _currentBlock.value = updatedBlock
            val result = dspPipeline.processBlock(updatedBlock, _activeProfile.value)
            _dspResult.value = result
            if (result.polarityDetectionResult.quality != PolarityDetectionQuality.NONE) {
                _polarityDetectionResult.value = result.polarityDetectionResult
            }
        }
    }

    fun setWaveformPolarity(polarity: WaveformPolarity) {
        val mode = when (polarity) {
            WaveformPolarity.POSITIVE -> PolarityMode.POSITIVE
            WaveformPolarity.NEGATIVE -> PolarityMode.NEGATIVE
        }
        val updatedConfig = _samplingConfig.value.copy(
            polarity = polarity,
            polarityMode = mode
        )
        _samplingConfig.value = updatedConfig
        protocolParser.updateSamplingConfig(updatedConfig)
        SystemDiagnostics.info("DSP", "Waveform polarity manually set to ${polarity.displayName}")

        val current = _currentBlock.value
        if (current.rawSamples.isNotEmpty()) {
            val updatedBlock = current.copy(
                samplingConfiguration = updatedConfig,
                polarity = updatedConfig.polarity,
                polarityMode = updatedConfig.polarityMode
            )
            _currentBlock.value = updatedBlock
            val result = dspPipeline.processBlock(updatedBlock, _activeProfile.value)
            _dspResult.value = result
        }
    }

    fun runPolarityDetection() {
        val res = _dspResult.value
        val config = _samplingConfig.value
        val profile = _activeProfile.value
        val residual = res?.residualCurve ?: return
        val dt = if (config.sampleSpacingUs > 0.0) config.sampleSpacingUs else 1.6
        val (decayStart, decayEnd) = profile.getIntegrationIndices(config)
        val tailFrac = profile.calibration.tailNoiseFraction
        val tStart = ((residual.size * tailFrac).toInt()).coerceIn(0, (residual.size - 2).coerceAtLeast(0))
        val rawNoise = com.example.felezjoo.dsp.NoiseEstimator.estimateNoise(residual, tStart, residual.size)

        val detectionResult = com.example.felezjoo.dsp.PolarityDetector.detectPolarity(
            rawResidual = residual,
            dt = dt,
            startIndex = decayStart,
            endIndex = decayEnd,
            noiseFloor = rawNoise.noiseFloor,
            calibration = profile.calibration
        )
        _polarityDetectionResult.value = detectionResult
        SystemDiagnostics.info(
            "DSP",
            "Polarity detection evaluated: ${detectionResult.detectedPolarity?.displayName ?: "UNKNOWN"} (${detectionResult.quality.displayName})"
        )
    }

    fun applyDetectedPolarity() {
        val detected = _polarityDetectionResult.value.detectedPolarity ?: return
        val updatedConfig = _samplingConfig.value.copy(
            polarity = detected,
            polarityMode = when (detected) {
                WaveformPolarity.POSITIVE -> PolarityMode.POSITIVE
                WaveformPolarity.NEGATIVE -> PolarityMode.NEGATIVE
            }
        )
        _samplingConfig.value = updatedConfig
        protocolParser.updateSamplingConfig(updatedConfig)
        SystemDiagnostics.info("DSP", "Adopted detected polarity: ${detected.displayName}")

        val current = _currentBlock.value
        if (current.rawSamples.isNotEmpty()) {
            val updatedBlock = current.copy(
                samplingConfiguration = updatedConfig,
                polarity = updatedConfig.polarity,
                polarityMode = updatedConfig.polarityMode
            )
            _currentBlock.value = updatedBlock
            val result = dspPipeline.processBlock(updatedBlock, _activeProfile.value)
            _dspResult.value = result
        }
    }

    // Recording & Sessions
    fun startRecording(sessionName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = SessionEntity(name = sessionName, deviceName = _deviceInfo.value.deviceName)
            database.sessionDao().insertSession(session)
            _activeSession.value = session
            _isRecording.value = true
        }
    }

    fun stopRecording() {
        _isRecording.value = false
    }

    fun loadSessionForReplay(session: SessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val blocks = database.decayBlockDao().getBlocksForSession(session.id)
            withContext(Dispatchers.Main) {
                replayEngine.loadBlocks(blocks)
                navigateTo(Screen.RECORDER_REPLAY)
            }
        }
    }

    private fun generateDemoDecayBlock() {
        val count = 70
        val samples = IntArray(count)
        val config = SamplingConfiguration()
        // Synthesize nice clean copper coin decay
        for (i in 0 until count) {
            val tUs = i * config.sampleSpacingUs
            val flyback = 600.0 * kotlin.math.exp(-tUs / 2.0)
            val ground = 30.0 * kotlin.math.exp(-tUs / 15.0)
            val target = 110.0 * kotlin.math.exp(-tUs / 18.0)
            val noise = (i % 3) * 1.5
            samples[i] = (120.0 + flyback + ground + target + noise).toInt().coerceIn(0, 1023)
        }
        val block = DecayBlock(
            sequenceNumber = 1,
            sampleCount = count,
            rawSamples = samples,
            samplingConfiguration = config
        )
        onBlockReceived(block)
    }

    // Export, Import & Share System for Configurations and Profiles
    fun exportCurrentProfileJson(name: String = "My Detector Setup", description: String = ""): String {
        val blk = _currentBlock.value
        val dsp = _activeProfile.value
        val cfg = _samplingConfig.value
        val pkg = ProfileExportPackage(
            profileName = name,
            description = description,
            exportTimestamp = System.currentTimeMillis(),
            pulseRateHz = blk.pulseRate,
            pulseWidthUs = blk.pulseWidthUs,
            delayTicks = cfg.delayTicks,
            sensitivity = _simplifiedSensitivity.value,
            targetThreshold = dsp.targetThreshold,
            confidenceThreshold = dsp.confidenceThreshold,
            ironRejectThreshold = dsp.ironRejectThreshold,
            audioThreshold = audioManager.audioThreshold,
            polarity = cfg.polarity.name,
            polarityMode = cfg.polarityMode.name,
            hardwareProfileId = _activeHardwareProfile.value.id,
            operationalPresetId = _activePreset.value.id
        )
        return ProfileJsonSerializer.serialize(pkg)
    }

    fun importProfileJson(jsonContent: String): Boolean {
        return try {
            val pkg = ProfileJsonSerializer.deserialize(jsonContent)
            // Restore hardware board if available
            val matchingBoard = _availableHardwareProfiles.value.find { it.id == pkg.hardwareProfileId }
            if (matchingBoard != null) {
                selectHardwareProfile(matchingBoard)
            }

            // Restore timing
            setPulseRateHz(pkg.pulseRateHz)
            setPulseWidthUs(pkg.pulseWidthUs)
            setDelayTicks(pkg.delayTicks)
            setSimplifiedSensitivity(pkg.sensitivity)

            // Restore polarity
            val pol = try { WaveformPolarity.valueOf(pkg.polarity) } catch (e: Exception) { WaveformPolarity.POSITIVE }
            val mode = try { PolarityMode.valueOf(pkg.polarityMode) } catch (e: Exception) { PolarityMode.AUTO }
            setPolarityMode(mode)
            if (mode != PolarityMode.AUTO) {
                setWaveformPolarity(pol)
            }

            // Restore DSP thresholds
            val currentDsp = _activeProfile.value
            val updatedDsp = currentDsp.copy(
                targetThreshold = pkg.targetThreshold,
                confidenceThreshold = pkg.confidenceThreshold,
                ironRejectThreshold = pkg.ironRejectThreshold,
                audioThreshold = pkg.audioThreshold
            )
            updateProfile(updatedDsp)
            audioManager.audioThreshold = pkg.audioThreshold

            SystemDiagnostics.info("Profile", "Successfully imported profile: ${pkg.profileName}")
            true
        } catch (e: Exception) {
            SystemDiagnostics.error("Profile", "Failed importing profile: ${e.message}")
            false
        }
    }

    fun exportHardwareProfileJson(profile: HardwareBoardProfile): String {
        return ProfileJsonSerializer.serializeHardwareProfile(profile)
    }

    fun importHardwareProfileJson(jsonContent: String): Boolean {
        return try {
            val profile = ProfileJsonSerializer.deserializeHardwareProfile(jsonContent)
            addNewHardwareProfile(profile)
            SystemDiagnostics.info("Hardware", "Imported custom hardware profile: ${profile.name}")
            true
        } catch (e: Exception) {
            SystemDiagnostics.error("Hardware", "Failed importing hardware profile: ${e.message}")
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        usbTransport.disconnect()
        simulationEngine.stop()
        audioManager.release()
    }
}
