package com.example.storm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.media.ExifInterface
import android.media.Image
import android.media.ImageReader
import android.media.MediaActionSound
import android.net.Uri
import android.os.SystemClock
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.util.Size
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.storm.ui.theme.StormTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashLogger(applicationContext)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            StormTheme {
                CameraApp()
            }
        }
    }
}

@Composable
private fun CameraApp() {
    val context = LocalContext.current
    val cameraOptions = remember { findCameraOptions(context) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when {
            !hasCameraPermission -> {
                PermissionRequest(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }

            cameraOptions.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aucune caméra détectée")
                }
            }

            else -> {
                CameraScreen(
                    cameraOptions = cameraOptions,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun PermissionRequest(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Storm Camera a besoin de la caméra pour afficher l’aperçu.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Autoriser la caméra")
        }
    }
}

@Composable
private fun CameraScreen(
    cameraOptions: List<CameraOption>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val textureView = remember { TextureView(context) }
    val controller = remember { CameraController(context.applicationContext) }
    val groundLevelDegrees = rememberGroundLevelDegrees()
    val shutterSound = remember {
        MediaActionSound().also { sound ->
            sound.load(MediaActionSound.SHUTTER_CLICK)
        }
    }
    val initialCamera = remember(cameraOptions) { cameraOptions.preferredInitialCamera() }
    var selectedCameraKey by remember { mutableStateOf(initialCamera.key) }
    var iso by remember { mutableIntStateOf(initialCamera.defaultIso) }
    var exposureMs by remember { mutableFloatStateOf(initialCamera.defaultExposureMs) }
    var captureStatus by remember { mutableStateOf("Prêt") }
    var lastMedia by remember { mutableStateOf<SavedMedia?>(null) }
    var timerSeconds by remember { mutableIntStateOf(TIMER_OPTIONS.first()) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var captureFormat by remember { mutableStateOf(CaptureFormat.JPG) }
    var shootingMode by remember { mutableStateOf(ShootingMode.MANUAL) }
    var isLightningHuntRunning by remember { mutableStateOf(false) }
    var lightningHuntStartJob by remember { mutableStateOf<Job?>(null) }
    var whiteBalanceMode by remember { mutableStateOf(WhiteBalanceMode.AUTO) }
    var showNotice by remember { mutableStateOf(false) }
    val selectedCamera = cameraOptions.first { it.key == selectedCameraKey }
    val latestIso = rememberUpdatedState(iso)
    val latestExposureMs = rememberUpdatedState(exposureMs)
    val latestCaptureStatus = rememberUpdatedState<(String) -> Unit> { status ->
        captureStatus = status
    }
    val latestMediaSaved = rememberUpdatedState<(SavedMedia) -> Unit> { media ->
        lastMedia = media
    }
    val latestLightningDetected = rememberUpdatedState {
        if (shootingMode.isLightningHunt && isLightningHuntRunning && !isTimerRunning) {
            val started = controller.capturePhoto(
                jpegOrientation = selectedCamera.jpegOrientation(context.displayRotationDegrees()),
                captureFormat = captureFormat
            )
            if (started) {
                shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                captureStatus = "Éclair détecté - capture"
            }
        }
    }

    fun stopLightningHunt(status: String = "Chasse éclair arrêtée") {
        lightningHuntStartJob?.cancel()
        lightningHuntStartJob = null
        isLightningHuntRunning = false
        isTimerRunning = false
        captureStatus = status
    }

    LaunchedEffect(selectedCameraKey) {
        iso = iso.coerceIn(selectedCamera.uiIsoRange.lower, selectedCamera.uiIsoRange.upper)
        exposureMs = exposureMs.coerceIn(
            selectedCamera.exposureMsRange.start,
            selectedCamera.exposureMsRange.endInclusive
        )
    }

    DisposableEffect(lifecycleOwner, textureView, selectedCameraKey, captureFormat, shootingMode) {
        fun startCamera() {
            controller.start(
                textureView = textureView,
                cameraId = selectedCamera.cameraId,
                physicalCameraId = selectedCamera.physicalCameraId,
                previewSize = selectedCamera.previewSize,
                captureSize = selectedCamera.captureSize,
                rawSize = selectedCamera.rawSize,
                fallbackPreviewSize = selectedCamera.fallbackPreviewSize,
                fallbackCaptureSize = selectedCamera.fallbackCaptureSize,
                fallbackRawSize = selectedCamera.fallbackRawSize,
                fallbackZoomRatio = selectedCamera.fallbackZoomRatio,
                zoomRatio = selectedCamera.zoomRatio,
                supportsManualSensor = selectedCamera.supportsManualSensor,
                supportsRaw = selectedCamera.supportsRaw,
                includeRawSurface = captureFormat.includesRaw,
                includeAnalysisSurface = shootingMode.isLightningHunt,
                lightningLuminanceDeltaThreshold = shootingMode.luminanceDeltaThreshold,
                iso = latestIso.value,
                exposureNanos = latestExposureMs.value.msToNanos(),
                whiteBalanceMode = whiteBalanceMode,
                onCaptureStatus = { status -> latestCaptureStatus.value(status) },
                onMediaSaved = { media -> latestMediaSaved.value(media) },
                onLightningDetected = { latestLightningDetected.value() }
            )
        }

        startCamera()

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> startCamera()
                Lifecycle.Event.ON_PAUSE -> controller.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopLightningHunt()
            controller.stop()
        }
    }

    DisposableEffect(shutterSound) {
        onDispose {
            shutterSound.release()
        }
    }

    LaunchedEffect(iso, exposureMs, whiteBalanceMode) {
        controller.updateManualControls(
            iso = iso,
            exposureNanos = exposureMs.msToNanos(),
            whiteBalanceMode = whiteBalanceMode
        )
    }

    BoxWithConstraints(modifier = modifier.background(Color.Black)) {
        val photoAspectRatio = selectedCamera.captureSize.displayAspectRatio(
            context.displayRotationDegrees()
        )
        val containerAspectRatio = maxWidth.value / maxHeight.value
        val previewModifier = if (containerAspectRatio > photoAspectRatio) {
            Modifier
                .align(Alignment.TopCenter)
                .requiredHeight(maxHeight)
                .requiredWidth(maxHeight * photoAspectRatio)
        } else {
            Modifier
                .align(Alignment.TopCenter)
                .requiredWidth(maxWidth)
                .requiredHeight(maxWidth / photoAspectRatio)
        }

        AndroidView(
            factory = { textureView },
            modifier = previewModifier
        )

        CameraSelector(
            cameraOptions = cameraOptions,
            selectedCamera = selectedCamera,
            onCameraSelected = { selectedCameraKey = it },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        SettingsButton(
            selectedCamera = selectedCamera,
            captureFormat = captureFormat,
            shootingMode = shootingMode,
            whiteBalanceMode = whiteBalanceMode,
            onCaptureFormatChanged = { captureFormat = it },
            onShootingModeChanged = { nextMode ->
                if (nextMode == ShootingMode.MANUAL) {
                    stopLightningHunt()
                }
                shootingMode = nextMode
            },
            onWhiteBalanceModeChanged = { whiteBalanceMode = it },
            onOpenNotice = {
                showNotice = true
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        ManualCameraControls(
            selectedCamera = selectedCamera,
            iso = iso,
            exposureMs = exposureMs,
            captureStatus = captureStatus,
            groundLevelDegrees = groundLevelDegrees,
            timerSeconds = timerSeconds,
            lastMedia = lastMedia,
            isTimerRunning = isTimerRunning,
            shootingMode = shootingMode,
            isLightningHuntRunning = isLightningHuntRunning,
            onIsoChanged = { nextIso ->
                iso = nextIso.coerceIn(selectedCamera.uiIsoRange.lower, selectedCamera.uiIsoRange.upper)
            },
            onExposureChanged = { nextExposureMs ->
                exposureMs = nextExposureMs.coerceIn(
                    selectedCamera.exposureMsRange.start,
                    selectedCamera.exposureMsRange.endInclusive
                )
            },
            onTimerSelected = { timerSeconds = it },
            onCapture = {
                if (shootingMode.isLightningHunt) {
                    if (isLightningHuntRunning || lightningHuntStartJob != null) {
                        stopLightningHunt()
                        return@ManualCameraControls
                    }

                    lightningHuntStartJob = coroutineScope.launch {
                        try {
                            if (timerSeconds > 0) {
                                isTimerRunning = true
                                for (remaining in timerSeconds downTo 1) {
                                    if (!isActive) {
                                        return@launch
                                    }
                                    captureStatus = "Chasse dans ${remaining}s"
                                    delay(1_000L)
                                }
                                isTimerRunning = false
                            }

                            isLightningHuntRunning = true
                            captureStatus = "Chasse éclair armée"
                        } finally {
                            isTimerRunning = false
                            lightningHuntStartJob = null
                        }
                    }
                    return@ManualCameraControls
                }

                if (isTimerRunning) {
                    return@ManualCameraControls
                }
                coroutineScope.launch {
                    if (timerSeconds > 0) {
                        isTimerRunning = true
                        for (remaining in timerSeconds downTo 1) {
                            captureStatus = "Déclenchement dans ${remaining}s"
                            delay(1_000L)
                        }
                        isTimerRunning = false
                    }
                    val started = controller.capturePhoto(
                        jpegOrientation = selectedCamera.jpegOrientation(context.displayRotationDegrees()),
                        captureFormat = captureFormat
                    )
                    if (started) {
                        shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                    }
                }
            },
            onOpenLastImage = {
                lastMedia?.let { media ->
                    context.openMedia(media)
                } ?: context.openImageGallery()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }

    if (showNotice) {
        NoticeDialog(onDismiss = { showNotice = false })
    }
}

@Composable
private fun rememberGroundLevelDegrees(): Float {
    val context = LocalContext.current
    var levelDegrees by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values.getOrNull(0) ?: return
                val y = event.values.getOrNull(1) ?: return
                val nextLevel = computeGroundLevelDegrees(x, y).roundToTenth()
                if (abs(nextLevel - levelDegrees) >= 0.1f) {
                    levelDegrees = nextLevel
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (sensorManager != null && sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return levelDegrees
}

@Composable
private fun ManualCameraControls(
    selectedCamera: CameraOption,
    iso: Int,
    exposureMs: Float,
    captureStatus: String,
    groundLevelDegrees: Float,
    timerSeconds: Int,
    lastMedia: SavedMedia?,
    isTimerRunning: Boolean,
    shootingMode: ShootingMode,
    isLightningHuntRunning: Boolean,
    onIsoChanged: (Int) -> Unit,
    onExposureChanged: (Float) -> Unit,
    onTimerSelected: (Int) -> Unit,
    onCapture: () -> Unit,
    onOpenLastImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(88.dp)
    ) {
        MaterialSurface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.46f),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompactControlSlider(
                    title = "ISO",
                    valueText = iso.toString(),
                    value = iso.toFloat(),
                    valueRange = selectedCamera.uiIsoRange.lower.toFloat()..
                        selectedCamera.uiIsoRange.upper.toFloat(),
                    onValueChange = { onIsoChanged(it.roundToInt()) }
                )

                CompactControlSlider(
                    title = "Pose",
                    valueText = formatExposure(exposureMs),
                    value = exposureMs,
                    valueRange = selectedCamera.exposureMsRange,
                    steps = selectedCamera.exposureSteps,
                    onValueChange = onExposureChanged
                )
            }
        }

        MaterialSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.58f),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (selectedCamera.supportsManualSensor) {
                            "${shootingMode.label} - Focus infini - Mode M"
                        } else {
                            "${shootingMode.label} - Focus infini - Mode auto"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Objectif: ${selectedCamera.label}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = captureStatus,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = formatLevelDegrees(groundLevelDegrees),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                BottomActions(
                    timerSeconds = timerSeconds,
                    lastMedia = lastMedia,
                    isTimerRunning = isTimerRunning,
                    shootingMode = shootingMode,
                    isLightningHuntRunning = isLightningHuntRunning,
                    onTimerSelected = onTimerSelected,
                    onCapture = onCapture,
                    onOpenLastImage = onOpenLastImage
                )
            }
        }
    }
}

@Composable
private fun CameraSelector(
    cameraOptions: List<CameraOption>,
    selectedCamera: CameraOption,
    onCameraSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black.copy(alpha = 0.48f),
                contentColor = Color.White
            )
        ) {
            CameraGlyph(modifier = Modifier.size(24.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp)
        ) {
            cameraOptions.forEach { camera ->
                val isSelected = camera.key == selectedCamera.key
                DropdownMenuItem(
                    modifier = if (isSelected) {
                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    } else {
                        Modifier
                    },
                    leadingIcon = {
                        if (isSelected) {
                            CheckGlyph(modifier = Modifier.size(20.dp))
                        } else {
                            Spacer(modifier = Modifier.size(20.dp))
                        }
                    },
                    text = { Text(camera.label) },
                    onClick = {
                        expanded = false
                        onCameraSelected(camera.key)
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(
    selectedCamera: CameraOption,
    captureFormat: CaptureFormat,
    shootingMode: ShootingMode,
    whiteBalanceMode: WhiteBalanceMode,
    onCaptureFormatChanged: (CaptureFormat) -> Unit,
    onShootingModeChanged: (ShootingMode) -> Unit,
    onWhiteBalanceModeChanged: (WhiteBalanceMode) -> Unit,
    onOpenNotice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black.copy(alpha = 0.48f),
                contentColor = Color.White
            )
        ) {
            GearGlyph(modifier = Modifier.size(24.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp)
        ) {
            DropdownMenuItem(
                text = { Text("Mode: ${shootingMode.label}") },
                onClick = {
                    onShootingModeChanged(shootingMode.next())
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "Format: ${captureFormat.label}" +
                            if (selectedCamera.supportsRaw) "" else " (RAW indisponible)"
                    )
                },
                onClick = {
                    onCaptureFormatChanged(captureFormat.next())
                }
            )
            DropdownMenuItem(
                text = { Text("Balance: ${whiteBalanceMode.label}") },
                onClick = {
                    onWhiteBalanceModeChanged(whiteBalanceMode.next())
                }
            )
            DropdownMenuItem(
                text = { Text("Storm v${APP_VERSION_NAME}  ⓘ") },
                onClick = {
                    expanded = false
                    onOpenNotice()
                }
            )
        }
    }
}

@Composable
private fun NoticeDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        },
        title = {
            Text("Storm v${APP_VERSION_NAME}")
        },
        text = {
            Text(
                "Storm est conçu pour capturer les éclairs. Réglez ISO et Pose, " +
                    "choisissez Manuel ou Chasse éclair, puis utilisez le bouton central " +
                    "pour déclencher ou armer la chasse.\n\nKevin Dekev"
            )
        }
    )
}

@Composable
private fun BottomActions(
    timerSeconds: Int,
    lastMedia: SavedMedia?,
    isTimerRunning: Boolean,
    shootingMode: ShootingMode,
    isLightningHuntRunning: Boolean,
    onTimerSelected: (Int) -> Unit,
    onCapture: () -> Unit,
    onOpenLastImage: () -> Unit
) {
    var timerExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box {
            OutlinedButton(
                onClick = { timerExpanded = true },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(timerSeconds.toString())
            }
            DropdownMenu(
                expanded = timerExpanded,
                onDismissRequest = { timerExpanded = false },
                shape = RoundedCornerShape(18.dp)
            ) {
                TIMER_OPTIONS.forEach { seconds ->
                    DropdownMenuItem(
                        text = { Text(seconds.toString()) },
                        onClick = {
                            timerExpanded = false
                            onTimerSelected(seconds)
                        }
                    )
                }
            }
        }

        Button(
            onClick = onCapture,
            enabled = !isTimerRunning || shootingMode.isLightningHunt,
            modifier = Modifier.size(76.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLightningHuntRunning) {
                    Color(0xFFB3261E)
                } else {
                    Color(0xFF0B2D5C)
                },
                contentColor = Color.White
            )
        ) {
            if (isLightningHuntRunning) {
                StopGlyph(modifier = Modifier.size(30.dp))
            } else {
                LightningGlyph(
                    modifier = Modifier.size(34.dp),
                    color = Color(0xFFFFD54F)
                )
            }
        }

        OutlinedButton(
            onClick = onOpenLastImage,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            GalleryPreview(media = lastMedia, modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun CameraGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.08f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(size.width * 0.12f, size.height * 0.28f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.52f),
            cornerRadius = CornerRadius(size.minDimension * 0.12f),
            style = Stroke(width = strokeWidth)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(size.width * 0.28f, size.height * 0.16f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.24f, size.height * 0.16f),
            cornerRadius = CornerRadius(size.minDimension * 0.05f),
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.15f,
            center = Offset(size.width * 0.5f, size.height * 0.54f),
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
private fun GearGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val strokeWidth = size.minDimension * 0.08f
        repeat(8) { index ->
            val angle = Math.toRadians((index * 45).toDouble())
            val start = Offset(
                x = center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.31f,
                y = center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.31f
            )
            val end = Offset(
                x = center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.43f,
                y = center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.43f
            )
            drawLine(
                color = Color.White,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.28f,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.09f,
            center = center,
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
private fun LightningGlyph(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.58f, size.height * 0.06f)
            lineTo(size.width * 0.22f, size.height * 0.58f)
            lineTo(size.width * 0.48f, size.height * 0.58f)
            lineTo(size.width * 0.36f, size.height * 0.94f)
            lineTo(size.width * 0.78f, size.height * 0.42f)
            lineTo(size.width * 0.52f, size.height * 0.42f)
            close()
        }
        drawPath(color = color, path = path)
    }
}

@Composable
private fun CheckGlyph(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height * 0.52f),
            end = Offset(size.width * 0.42f, size.height * 0.74f),
            strokeWidth = size.minDimension * 0.12f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.42f, size.height * 0.74f),
            end = Offset(size.width * 0.84f, size.height * 0.24f),
            strokeWidth = size.minDimension * 0.12f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun StopGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(size.width * 0.24f, size.height * 0.24f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.52f, size.height * 0.52f),
            cornerRadius = CornerRadius(size.minDimension * 0.06f)
        )
    }
}

@Composable
private fun GalleryPreview(
    media: SavedMedia?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        if (media?.isJpeg == true) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    imageView.setImageURI(null)
                    imageView.setImageURI(media.uri)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (media != null) {
            Text(
                text = media.shortLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            GalleryGlyph(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun GalleryGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.08f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(size.width * 0.12f, size.height * 0.18f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.64f),
            cornerRadius = CornerRadius(size.minDimension * 0.1f),
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.08f,
            center = Offset(size.width * 0.68f, size.height * 0.34f)
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.18f, size.height * 0.74f),
            end = Offset(size.width * 0.42f, size.height * 0.52f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.42f, size.height * 0.52f),
            end = Offset(size.width * 0.62f, size.height * 0.7f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.56f, size.height * 0.62f),
            end = Offset(size.width * 0.82f, size.height * 0.38f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ControlSlider(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(16.dp))
            Text(valueText, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@Composable
private fun CompactControlSlider(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: List<Float>? = null,
    onValueChange: (Float) -> Unit
) {
    val sliderValueRange = if (steps == null) {
        valueRange
    } else {
        0f..steps.lastIndex.toFloat()
    }
    val sliderValue = if (steps == null) {
        value.coerceIn(valueRange.start, valueRange.endInclusive)
    } else {
        steps.nearestIndex(value).toFloat()
    }
    val sliderSteps = if (steps == null) {
        0
    } else {
        (steps.size - 2).coerceAtLeast(0)
    }
    val hapticView = LocalView.current
    var lastHapticStep by remember(steps) {
        mutableIntStateOf(if (steps == null) -1 else steps.nearestIndex(value))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.width(38.dp),
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = sliderValue,
            onValueChange = { nextValue ->
                if (steps == null) {
                    onValueChange(nextValue)
                } else {
                    val index = nextValue.roundToInt().coerceIn(0, steps.lastIndex)
                    if (index != lastHapticStep) {
                        hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        lastHapticStep = index
                    }
                    onValueChange(steps[index])
                }
            },
            modifier = Modifier.weight(1f),
            valueRange = sliderValueRange,
            steps = sliderSteps
        )
        Text(
            text = valueText,
            modifier = Modifier.width(66.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private data class CameraOption(
    val key: String,
    val cameraId: String,
    val physicalCameraId: String?,
    val label: String,
    val previewSize: Size,
    val captureSize: Size,
    val rawSize: Size,
    val fallbackPreviewSize: Size,
    val fallbackCaptureSize: Size,
    val fallbackRawSize: Size,
    val fallbackZoomRatio: Float?,
    val focalLengthMm: Float?,
    val fieldOfView: FieldOfView?,
    val isAutoLens: Boolean,
    val zoomRatio: Float?,
    val supportsManualSensor: Boolean,
    val supportsRaw: Boolean,
    val sensorOrientation: Int,
    val lensFacing: Int?,
    val isoRange: Range<Int>,
    val exposureNanosRange: Range<Long>
) {
    val uiIsoRange: Range<Int>
        get() = Range(isoRange.lower, minOf(isoRange.upper, MAX_UI_ISO).coerceAtLeast(isoRange.lower))

    val exposureMsRange: ClosedFloatingPointRange<Float>
        get() = exposureNanosRange.lower.nanosToMs()..exposureNanosRange.upper.nanosToMs()

    val exposureSteps: List<Float>
        get() = buildExposureSteps(exposureMsRange)

    val defaultIso: Int
        get() = 100.coerceIn(uiIsoRange.lower, uiIsoRange.upper)

    val defaultExposureMs: Float
        get() = (1_000f / 60f).coerceIn(exposureMsRange.start, exposureMsRange.endInclusive)
}

private data class FieldOfView(
    val horizontalDegrees: Float,
    val verticalDegrees: Float
) {
    val areaScore: Float
        get() = horizontalDegrees * verticalDegrees
}

private data class SavedMedia(
    val uri: Uri,
    val mimeType: String,
    val shortLabel: String
) {
    val isJpeg: Boolean
        get() = mimeType == MIME_TYPE_JPEG
}

private enum class CaptureFormat(
    val label: String
) {
    JPG("JPG"),
    RAW("RAW"),
    BOTH("JPG + RAW");

    fun next(): CaptureFormat = when (this) {
        JPG -> RAW
        RAW -> BOTH
        BOTH -> JPG
    }

    val includesRaw: Boolean
        get() = this == RAW || this == BOTH
}

private enum class ShootingMode(
    val label: String,
    val luminanceDeltaThreshold: Double
) {
    MANUAL("Manuel", LIGHTNING_NORMAL_LUMINANCE_DELTA_THRESHOLD),
    LIGHTNING_HUNT_NORMAL("Chasse éclair normale", LIGHTNING_NORMAL_LUMINANCE_DELTA_THRESHOLD),
    LIGHTNING_HUNT_HIGH("Chasse éclair élevée", 22.0);

    fun next(): ShootingMode = when (this) {
        MANUAL -> LIGHTNING_HUNT_NORMAL
        LIGHTNING_HUNT_NORMAL -> LIGHTNING_HUNT_HIGH
        LIGHTNING_HUNT_HIGH -> MANUAL
    }

    val isLightningHunt: Boolean
        get() = this != MANUAL
}

private enum class WhiteBalanceMode(
    val label: String
) {
    AUTO("Auto"),
    STORM_4300K("4300K ciel bleu");

    fun next(): WhiteBalanceMode = when (this) {
        AUTO -> STORM_4300K
        STORM_4300K -> AUTO
    }
}

private fun CaptureFormat.surfaces(
    jpegSurface: Surface?,
    rawSurface: Surface?,
    supportsRaw: Boolean
): List<Surface> {
    return when (this) {
        CaptureFormat.JPG -> listOfNotNull(jpegSurface)
        CaptureFormat.RAW -> if (supportsRaw) listOfNotNull(rawSurface) else emptyList()
        CaptureFormat.BOTH -> listOfNotNull(jpegSurface) + if (supportsRaw) {
            listOfNotNull(rawSurface)
        } else {
            emptyList()
        }
    }
}

private fun findCameraOptions(context: Context): List<CameraOption> {
    val cameraManager = context.getSystemService(CameraManager::class.java)
    val options = mutableListOf<CameraOption>()

    cameraManager.cameraIdList.forEach { cameraId ->
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        if (facing != CameraCharacteristics.LENS_FACING_BACK) {
            return@forEach
        }

        val isLogicalMultiCamera = characteristics.isLogicalMultiCamera()
        val supportsManualSensor = characteristics.supportsManualSensor()
        val supportsRaw = characteristics.supportsRaw()
        val isoRange = characteristics.get(
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        ) ?: DEFAULT_ISO_RANGE
        val exposureRange = characteristics.get(
            CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
        ) ?: DEFAULT_EXPOSURE_RANGE
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val focalLengths = characteristics.get(
            CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
        ) ?: floatArrayOf()
        val sensorPhysicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val streamConfigurationMap = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )
        val captureSize = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )?.getOutputSizes(ImageFormat.JPEG)
            .chooseCaptureSize()
        val previewSize = streamConfigurationMap
            ?.getOutputSizes(SurfaceTexture::class.java)
            .choosePreviewSize(captureSize)
        val rawSize = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )?.getOutputSizes(ImageFormat.RAW_SENSOR)
            .chooseRawSize()
        val physicalCameraIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isLogicalMultiCamera) {
            characteristics.physicalCameraIds
        } else {
            emptySet()
        }
        val isDirectUltraWideCamera = !isLogicalMultiCamera && focalLengths.isUltraWideFocalLength()

        options += CameraOption(
            key = cameraId,
            cameraId = cameraId,
            physicalCameraId = null,
            label = if (isDirectUltraWideCamera) "Ultra physique" else "Objectif auto",
            previewSize = previewSize,
            captureSize = captureSize,
            rawSize = rawSize,
            fallbackPreviewSize = previewSize,
            fallbackCaptureSize = captureSize,
            fallbackRawSize = rawSize,
            fallbackZoomRatio = null,
            focalLengthMm = focalLengths.minOrNull(),
            fieldOfView = calculateFieldOfView(sensorPhysicalSize, focalLengths),
            isAutoLens = !isDirectUltraWideCamera,
            zoomRatio = null,
            supportsManualSensor = supportsManualSensor,
            supportsRaw = supportsRaw,
            sensorOrientation = sensorOrientation,
            lensFacing = facing,
            isoRange = isoRange,
            exposureNanosRange = exposureRange
        )

        val ultraWideZoomRatio = characteristics.ultraWideZoomRatio()
        if (!isDirectUltraWideCamera && ultraWideZoomRatio != null) {
            options += CameraOption(
                key = "$cameraId:zoom:${ultraWideZoomRatio.zoomKey()}",
                cameraId = cameraId,
                physicalCameraId = null,
                label = "${ultraWideZoomRatio.zoomLabel()} Ultra natif secours",
                previewSize = previewSize,
                captureSize = captureSize,
                rawSize = rawSize,
                fallbackPreviewSize = previewSize,
                fallbackCaptureSize = captureSize,
                fallbackRawSize = rawSize,
                fallbackZoomRatio = ultraWideZoomRatio,
                focalLengthMm = focalLengths.minOrNull()?.times(ultraWideZoomRatio),
                fieldOfView = calculateFieldOfView(sensorPhysicalSize, focalLengths, ultraWideZoomRatio),
                isAutoLens = false,
                zoomRatio = ultraWideZoomRatio,
                supportsManualSensor = supportsManualSensor,
                supportsRaw = supportsRaw,
                sensorOrientation = sensorOrientation,
                lensFacing = facing,
                isoRange = isoRange,
                exposureNanosRange = exposureRange
            )
        }

        if (physicalCameraIds.isEmpty()) {
            return@forEach
        }

        physicalCameraIds.forEach { physicalCameraId ->
            val physicalCharacteristics = runCatching {
                cameraManager.getCameraCharacteristics(physicalCameraId)
            }.getOrNull() ?: return@forEach
            val physicalFocalLengths = physicalCharacteristics.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            ) ?: floatArrayOf()
            val physicalSensorPhysicalSize = physicalCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
            ) ?: sensorPhysicalSize
            val physicalSensorOrientation = physicalCharacteristics.get(
                CameraCharacteristics.SENSOR_ORIENTATION
            ) ?: sensorOrientation
            val physicalSupportsManualSensor = physicalCharacteristics.supportsManualSensor() ||
                supportsManualSensor
            val physicalSupportsRaw = physicalCharacteristics.supportsRaw() || supportsRaw
            val physicalIsoRange = physicalCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
            ) ?: isoRange
            val physicalExposureRange = physicalCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
            ) ?: exposureRange
            val physicalStreamConfigurationMap = physicalCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )
            val physicalCaptureSize = physicalStreamConfigurationMap
                ?.getOutputSizes(ImageFormat.JPEG)
                .chooseCaptureSize()
            val physicalPreviewSize = physicalStreamConfigurationMap
                ?.getOutputSizes(SurfaceTexture::class.java)
                .choosePreviewSize(physicalCaptureSize)
            val physicalRawSize = physicalStreamConfigurationMap
                ?.getOutputSizes(ImageFormat.RAW_SENSOR)
                .chooseRawSize()
            val physicalFallbackZoomRatio = if (physicalFocalLengths.isUltraWideFocalLength()) {
                ultraWideZoomRatio
            } else {
                null
            }

            options += CameraOption(
                key = "$cameraId:$physicalCameraId",
                cameraId = cameraId,
                physicalCameraId = physicalCameraId,
                label = physicalCameraId,
                previewSize = physicalPreviewSize,
                captureSize = physicalCaptureSize,
                rawSize = physicalRawSize,
                fallbackPreviewSize = previewSize,
                fallbackCaptureSize = captureSize,
                fallbackRawSize = rawSize,
                fallbackZoomRatio = physicalFallbackZoomRatio,
                focalLengthMm = physicalFocalLengths.minOrNull(),
                fieldOfView = calculateFieldOfView(physicalSensorPhysicalSize, physicalFocalLengths),
                isAutoLens = false,
                zoomRatio = null,
                supportsManualSensor = physicalSupportsManualSensor,
                supportsRaw = physicalSupportsRaw,
                sensorOrientation = physicalSensorOrientation,
                lensFacing = facing,
                isoRange = physicalIsoRange,
                exposureNanosRange = physicalExposureRange
            )
        }
    }

    val distinctOptions = options.distinctBy { option ->
        if (option.isAutoLens) "auto:${option.cameraId}" else option.physicalCameraId ?: option.key
    }
    val autoLensOptions = distinctOptions
        .filter { it.isAutoLens }
        .sortedBy { it.cameraId }
    val physicalLensOptions = distinctOptions
        .filterNot { it.isAutoLens }
        .sortedWith(
            compareByDescending<CameraOption> { it.fieldOfView?.areaScore ?: 0f }
                .thenBy { it.focalLengthMm ?: Float.MAX_VALUE }
                .thenBy { it.cameraId }
                .thenBy { it.physicalCameraId.orEmpty() }
        )
    val referenceFocalLength = physicalLensOptions.standardFocalLength()
    val labeledPhysicalLensOptions = physicalLensOptions
        .mapIndexed { index, option ->
            option.copy(label = option.shortLensLabel(index, referenceFocalLength))
        }
    val trueUltraWideOptions = labeledPhysicalLensOptions.filter { option ->
        option.isTrueUltraWideLens()
    }
    val otherPhysicalLensOptions = labeledPhysicalLensOptions.filterNot { option ->
        option.isTrueUltraWideLens()
    }

    return trueUltraWideOptions + autoLensOptions + otherPhysicalLensOptions
}

private fun CameraCharacteristics.supportsManualSensor(): Boolean {
    val capabilities = get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
    return capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
}

private fun CameraCharacteristics.isLogicalMultiCamera(): Boolean {
    val capabilities = get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
    return capabilities.contains(
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
    )
}

private fun CameraCharacteristics.supportsRaw(): Boolean {
    val capabilities = get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
    return capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
}

private fun CameraCharacteristics.ultraWideZoomRatio(): Float? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return null
    }

    val zoomRange = get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) ?: return null
    return zoomRange.lower.takeIf { ratio -> ratio < 0.95f }
}

private fun FloatArray.isUltraWideFocalLength(): Boolean {
    return any { focalLength ->
        focalLength > 0f && focalLength < ULTRA_WIDE_MAX_FOCAL_LENGTH_MM
    }
}

private fun CameraOption.isTrueUltraWideLens(): Boolean {
    return !isAutoLens && zoomRatio == null &&
        (focalLengthMm?.let { focalLength -> focalLength < ULTRA_WIDE_MAX_FOCAL_LENGTH_MM } == true ||
            lensTypeLabel() == "fisheye" ||
            lensTypeLabel() == "ultra-wide")
}

private fun CameraOption.shortLensLabel(
    index: Int,
    referenceFocalLengthMm: Float?
): String {
    if (zoomRatio != null && zoomRatio < 1f) {
        return listOfNotNull(
            zoomRatio.zoomLabel(),
            lensTypeLabel() ?: "fisheye",
            fieldOfView?.horizontalDegrees?.roundToInt()?.let { degrees -> "${degrees} deg" },
            captureSize.megapixelLabel()
        ).joinToString(" ")
    }

    if (isAutoLens) {
        return "$label ${captureSize.megapixelLabel()}"
    }
    val fieldLabel = fieldOfView?.let { fieldOfView ->
        "${fieldOfView.horizontalDegrees.roundToInt()} x " +
            "${fieldOfView.verticalDegrees.roundToInt()} deg"
    }
    val focalLabel = focalLengthMm?.let { focalLength ->
        String.format(Locale.US, "%.1fmm", focalLength)
    }
    return listOfNotNull(
        zoomRatioLabel(referenceFocalLengthMm) ?: "Objectif $index",
        lensTypeLabel(),
        fieldLabel ?: focalLabel,
        captureSize.megapixelLabel()
    ).joinToString(" ")
}

private fun List<CameraOption>.standardFocalLength(): Float? {
    return firstOrNull { option ->
        option.lensTypeLabel() == "wide"
    }?.focalLengthMm ?: minByOrNull { option ->
        kotlin.math.abs((option.fieldOfView?.horizontalDegrees ?: 70f) - 70f)
    }?.focalLengthMm
}

private fun List<CameraOption>.preferredInitialCamera(): CameraOption {
    return firstOrNull { option ->
        option.isTrueUltraWideLens()
    } ?: firstOrNull { option ->
        option.zoomRatio != null && option.zoomRatio < 1f
    } ?: firstOrNull { option ->
        option.lensTypeLabel() == "fisheye"
    } ?: first()
}

private fun CameraOption.zoomRatioLabel(referenceFocalLengthMm: Float?): String? {
    val focalLength = focalLengthMm ?: return null
    val reference = referenceFocalLengthMm ?: return null
    if (focalLength <= 0f || reference <= 0f) {
        return null
    }

    val ratio = focalLength / reference
    return String.format(Locale.US, "%.1fx", ratio)
}

private fun Float.zoomKey(): String {
    return String.format(Locale.US, "%.2f", this)
}

private fun Float.zoomLabel(): String {
    return String.format(Locale.US, "%.1fx", this)
}

private fun CameraOption.resolutionLabel(): String {
    val rawLabel = if (supportsRaw) {
        " - RAW ${rawSize.formatResolution()}"
    } else {
        ""
    }
    return "JPG ${captureSize.formatResolution()}$rawLabel"
}

private fun CameraOption.lensTypeLabel(): String? {
    val horizontalDegrees = fieldOfView?.horizontalDegrees ?: return null
    return when {
        horizontalDegrees >= 115f -> "fisheye"
        horizontalDegrees >= 100f -> "ultra-wide"
        horizontalDegrees >= 55f -> "wide"
        horizontalDegrees >= 25f -> "tele"
        else -> "super-tele"
    }
}

private fun calculateFieldOfView(
    sensorPhysicalSize: SizeF?,
    focalLengths: FloatArray,
    zoomRatio: Float = 1f
): FieldOfView? {
    val focalLength = focalLengths.minOrNull() ?: return null
    val effectiveFocalLength = focalLength * zoomRatio
    if (sensorPhysicalSize == null || effectiveFocalLength <= 0f) {
        return null
    }
    val horizontal = 2.0 * atan(sensorPhysicalSize.width / (2.0 * effectiveFocalLength))
    val vertical = 2.0 * atan(sensorPhysicalSize.height / (2.0 * effectiveFocalLength))
    return FieldOfView(
        horizontalDegrees = Math.toDegrees(horizontal).toFloat(),
        verticalDegrees = Math.toDegrees(vertical).toFloat()
    )
}

private fun CameraOption.jpegOrientation(deviceRotationDegrees: Int): Int {
    val frontCameraSign = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) 1 else -1
    return (sensorOrientation - deviceRotationDegrees * frontCameraSign + 360) % 360
}

private fun Throwable.isClosedCameraError(): Boolean {
    return this is IllegalStateException && message.orEmpty().contains("closed", ignoreCase = true)
}

private fun TextureView.configurePreviewTransform(previewSize: Size) {
    val viewWidth = width.toFloat()
    val viewHeight = height.toFloat()
    if (viewWidth <= 0f || viewHeight <= 0f) {
        return
    }

    val matrix = Matrix()
    val viewRect = RectF(0f, 0f, viewWidth, viewHeight)
    val centerX = viewRect.centerX()
    val centerY = viewRect.centerY()
    val rotationDegrees = context.displayRotationDegrees()

    if (rotationDegrees == 90 || rotationDegrees == 270) {
        val bufferRect = RectF(
            0f,
            0f,
            previewSize.height.toFloat(),
            previewSize.width.toFloat()
        )
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
        val scale = max(
            viewHeight / previewSize.height.toFloat(),
            viewWidth / previewSize.width.toFloat()
        )
        matrix.postScale(scale, scale, centerX, centerY)
        matrix.postRotate(if (rotationDegrees == 90) -90f else 90f, centerX, centerY)
    } else {
        val scale = max(
            viewWidth / previewSize.width.toFloat(),
            viewHeight / previewSize.height.toFloat()
        )
        matrix.postScale(scale, scale, centerX, centerY)
        if (rotationDegrees == 180) {
            matrix.postRotate(180f, centerX, centerY)
        }
    }

    setTransform(matrix)
}

@Suppress("DEPRECATION")
private fun Context.displayRotationDegrees(): Int {
    val rotation = findActivity()?.windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    return when (rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun Context.openMedia(media: SavedMedia) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(media.uri, media.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (findActivity() == null) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    runCatching {
        startActivity(intent)
    }.onFailure {
        Toast.makeText(this, "Aucune app pour ouvrir ${media.shortLabel}", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.openImageGallery() {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        if (findActivity() == null) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    runCatching {
        startActivity(intent)
    }
}

private class CameraController(
    context: Context
) {
    private val appContext = context.applicationContext
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewSurface: Surface? = null
    private var imageReader: ImageReader? = null
    private var rawImageReader: ImageReader? = null
    private var analysisImageReader: ImageReader? = null
    private var textureView: TextureView? = null
    private var cameraId: String? = null
    private var physicalCameraId: String? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var rawCameraCharacteristics: CameraCharacteristics? = null
    private var usePhysicalOutputRouting: Boolean = false
    private var supportsManualSensor: Boolean = false
    private var supportsRaw: Boolean = false
    private var includeRawSurface: Boolean = false
    private var includeAnalysisSurface: Boolean = false
    private var zoomRatio: Float? = null
    private var fallbackZoomRatio: Float? = null
    private var iso: Int = 100
    private var exposureNanos: Long = 1_000_000_000L / 60L
    private var whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO
    private var isCapturing = false
    private var lastCaptureResultStatus = ""
    private var lastAnalysisLuminance: Double = 0.0
    private var lightningLuminanceDeltaThreshold: Double = LIGHTNING_NORMAL_LUMINANCE_DELTA_THRESHOLD
    private var lastLightningTriggerMillis: Long = 0L
    private var latestCaptureResult: TotalCaptureResult? = null
    private var latestDngOrientation: Int = ExifInterface.ORIENTATION_NORMAL
    private var pendingRawImage: Image? = null
    private var countdownRunnable: Runnable? = null
    @Volatile
    private var sessionGeneration: Long = 0L
    private var onCaptureStatus: (String) -> Unit = {}
    private var onMediaSaved: (SavedMedia) -> Unit = {}
    private var onLightningDetected: () -> Unit = {}
    private val mainHandler = Handler(appContext.mainLooper)

    @SuppressLint("MissingPermission")
    fun start(
        textureView: TextureView,
        cameraId: String,
        physicalCameraId: String?,
        previewSize: Size,
        captureSize: Size,
        rawSize: Size,
        fallbackPreviewSize: Size,
        fallbackCaptureSize: Size,
        fallbackRawSize: Size,
        fallbackZoomRatio: Float?,
        zoomRatio: Float?,
        supportsManualSensor: Boolean,
        supportsRaw: Boolean,
        includeRawSurface: Boolean,
        includeAnalysisSurface: Boolean,
        lightningLuminanceDeltaThreshold: Double,
        iso: Int,
        exposureNanos: Long,
        whiteBalanceMode: WhiteBalanceMode,
        onCaptureStatus: (String) -> Unit,
        onMediaSaved: (SavedMedia) -> Unit,
        onLightningDetected: () -> Unit
    ) {
        stop()
        val generation = nextSessionGeneration()

        this.textureView = textureView
        this.cameraId = cameraId
        this.physicalCameraId = physicalCameraId
        this.usePhysicalOutputRouting = physicalCameraId != null
        val logicalCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        this.cameraCharacteristics = logicalCharacteristics
        this.rawCameraCharacteristics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && physicalCameraId != null) {
            runCatching {
                cameraManager.getCameraCharacteristics(physicalCameraId)
            }.getOrElse {
                logicalCharacteristics
            }
        } else {
            logicalCharacteristics
        }
        this.previewSize = previewSize
        this.captureSize = captureSize
        this.rawSize = rawSize
        this.fallbackPreviewSize = fallbackPreviewSize
        this.fallbackCaptureSize = fallbackCaptureSize
        this.fallbackRawSize = fallbackRawSize
        this.fallbackZoomRatio = fallbackZoomRatio
        this.zoomRatio = zoomRatio
        this.supportsManualSensor = supportsManualSensor
        this.supportsRaw = supportsRaw
        this.includeRawSurface = includeRawSurface
        this.includeAnalysisSurface = includeAnalysisSurface
        this.lightningLuminanceDeltaThreshold = lightningLuminanceDeltaThreshold
        this.iso = iso
        this.exposureNanos = exposureNanos
        this.whiteBalanceMode = whiteBalanceMode
        this.onCaptureStatus = onCaptureStatus
        this.onMediaSaved = onMediaSaved
        this.onLightningDetected = onLightningDetected
        startBackgroundThread()

        if (textureView.isAvailable) {
            openCamera(generation)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    fun updateManualControls(
        iso: Int,
        exposureNanos: Long,
        whiteBalanceMode: WhiteBalanceMode
    ) {
        this.iso = iso
        this.exposureNanos = exposureNanos
        this.whiteBalanceMode = whiteBalanceMode
        applyRepeatingRequest()
    }

    fun capturePhoto(
        jpegOrientation: Int,
        captureFormat: CaptureFormat
    ): Boolean {
        if (isCapturing) {
            notifyCaptureStatus("Capture déjà en cours")
            return false
        }
        val device = cameraDevice ?: run {
            notifyCaptureStatus("Caméra indisponible")
            return false
        }
        val session = captureSession ?: run {
            notifyCaptureStatus("Session indisponible")
            return false
        }
        val handler = backgroundHandler ?: run {
            notifyCaptureStatus("Thread indisponible")
            return false
        }
        val captureSurfaces = captureFormat.surfaces(
            jpegSurface = imageReader?.surface,
            rawSurface = rawImageReader?.surface,
            supportsRaw = supportsRaw
        )
        if (captureSurfaces.isEmpty()) {
            notifyCaptureStatus("RAW indisponible sur cet objectif")
            return false
        }
        val requestedIso = iso
        val requestedExposureNanos = exposureNanos
        latestDngOrientation = jpegOrientation.toExifOrientation()

        try {
            isCapturing = true
            latestCaptureResult = null
            startCaptureCountdown(requestedExposureNanos)
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                captureSurfaces.forEach(::addTarget)
                set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE)
                applyManualSettings(
                    iso = requestedIso,
                    exposureNanos = requestedExposureNanos,
                    controlMode = CameraMetadata.CONTROL_MODE_OFF,
                    physicalCameraId = physicalCameraId,
                    zoomRatio = zoomRatio,
                    manualSensor = supportsManualSensor,
                    whiteBalanceMode = whiteBalanceMode
                )
                set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
            }.build()

            session.stopRepeating()
            session.abortCaptures()
            session.capture(
                request,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        isCapturing = false
                        cancelCaptureCountdown()
                        latestCaptureResult = result
                        pendingRawImage?.let { rawImage ->
                            pendingRawImage = null
                            saveRawImage(rawImage)
                            rawImage.close()
                        }
                        val appliedExposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                        val appliedIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                        val exposureText = appliedExposure?.nanosToMs()?.let(::formatExposure)
                            ?: formatExposure(requestedExposureNanos.nanosToMs())
                        lastCaptureResultStatus = "Appliqué : $exposureText ISO ${appliedIso ?: requestedIso}"
                        notifyCaptureStatus(lastCaptureResultStatus)
                        applyRepeatingRequest()
                    }

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        isCapturing = false
                        cancelCaptureCountdown()
                        pendingRawImage?.close()
                        pendingRawImage = null
                        Log.e(LOG_TAG, "Capture failed: reason=${failure.reason}")
                        notifyCaptureStatus("Échec capture")
                        applyRepeatingRequest()
                    }
                },
                handler
            )
            return true
        } catch (error: Exception) {
            isCapturing = false
            cancelCaptureCountdown()
            pendingRawImage?.close()
            pendingRawImage = null
            applyRepeatingRequest()
            notifyCaptureStatus("Échec capture : ${error.message.orEmpty()}")
            return false
        }
    }

    fun stop() {
        nextSessionGeneration()
        textureView?.surfaceTextureListener = null
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        rawImageReader?.close()
        rawImageReader = null
        analysisImageReader?.close()
        analysisImageReader = null
        previewSurface?.release()
        previewSurface = null
        previewRequestBuilder = null
        textureView = null
        cameraId = null
        physicalCameraId = null
        cameraCharacteristics = null
        rawCameraCharacteristics = null
        latestDngOrientation = ExifInterface.ORIENTATION_NORMAL
        usePhysicalOutputRouting = false
        fallbackPreviewSize = Size(1280, 720)
        fallbackCaptureSize = Size(1920, 1080)
        fallbackRawSize = Size(1920, 1080)
        fallbackZoomRatio = null
        zoomRatio = null
        supportsManualSensor = false
        supportsRaw = false
        includeRawSurface = false
        includeAnalysisSurface = false
        whiteBalanceMode = WhiteBalanceMode.AUTO
        isCapturing = false
        cancelCaptureCountdown()
        lastCaptureResultStatus = ""
        lastAnalysisLuminance = 0.0
        lightningLuminanceDeltaThreshold = LIGHTNING_NORMAL_LUMINANCE_DELTA_THRESHOLD
        lastLightningTriggerMillis = 0L
        latestCaptureResult = null
        pendingRawImage?.close()
        pendingRawImage = null
        onCaptureStatus = {}
        onMediaSaved = {}
        onLightningDetected = {}
        stopBackgroundThread()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(generation: Long = sessionGeneration) {
        val id = cameraId ?: return
        val handler = backgroundHandler ?: return
        runCatching {
            cameraManager.openCamera(id, createCameraStateCallback(generation), handler)
        }.onFailure { error ->
            Log.e(LOG_TAG, "Open camera failed", error)
            notifyCaptureStatus("Échec ouverture caméra")
        }
    }

    @Suppress("DEPRECATION")
    private fun createPreviewSession(generation: Long = sessionGeneration) {
        if (!isCurrentGeneration(generation)) {
            return
        }
        val device = cameraDevice ?: return
        val texture = textureView?.surfaceTexture ?: return
        val handler = backgroundHandler ?: return

        resetSessionOutputs()

        val previewSize = previewSize
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        textureView?.configurePreviewTransform(previewSize)
        val surface = Surface(texture)
        val captureSize = captureSize
        imageReader = ImageReader.newInstance(
            captureSize.width,
            captureSize.height,
            ImageFormat.JPEG,
            MAX_PENDING_IMAGES
        ).apply {
            setOnImageAvailableListener(
                { reader ->
                    saveNextImage(reader)
                },
                handler
            )
        }
        if (supportsRaw && includeRawSurface) {
            val rawSize = rawSize
            rawImageReader = ImageReader.newInstance(
                rawSize.width,
                rawSize.height,
                ImageFormat.RAW_SENSOR,
                MAX_PENDING_IMAGES
            ).apply {
                setOnImageAvailableListener(
                    { reader ->
                        saveNextRawImage(reader)
                    },
                    handler
                )
            }
        }
        if (includeAnalysisSurface) {
            val analysisSize = cameraCharacteristics
                ?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.YUV_420_888)
                .chooseAnalysisSize(captureSize)
            analysisImageReader = ImageReader.newInstance(
                analysisSize.width,
                analysisSize.height,
                ImageFormat.YUV_420_888,
                MAX_ANALYSIS_IMAGES
            ).apply {
                setOnImageAvailableListener(
                    { reader ->
                        analyzeNextPreviewImage(reader)
                    },
                    handler
                )
            }
        }
        previewSurface = surface
        if (!isCurrentGeneration(generation) || cameraDevice !== device) {
            resetSessionOutputs()
            return
        }
        previewRequestBuilder = runCatching {
            device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                analysisImageReader?.surface?.let(::addTarget)
            }
        }.getOrElse { error ->
            handleCreateSessionFailure(error, generation)
            return
        }

        val imageReaderSurface = imageReader?.surface ?: return
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (!isCurrentGeneration(generation) || cameraDevice == null) {
                    session.close()
                    return
                }
                captureSession = session
                if (!applyRepeatingRequest()) {
                    session.close()
                    captureSession = null
                    if (usePhysicalOutputRouting) {
                        Log.e(LOG_TAG, "Physical output routing rejected; retrying logical session")
                        notifyCaptureStatus("Objectif physique : secours logique")
                        switchToLogicalOutputRouting()
                        createPreviewSession(generation)
                    } else {
                        notifyCaptureStatus("Échec aperçu caméra")
                    }
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(LOG_TAG, "Camera session configure failed")
                session.close()
                if (isCurrentGeneration(generation) && usePhysicalOutputRouting) {
                    notifyCaptureStatus("Objectif physique : secours logique")
                    switchToLogicalOutputRouting()
                    createPreviewSession(generation)
                } else {
                    notifyCaptureStatus("Échec session caméra")
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            fun OutputConfiguration.routeToPhysicalLens(): OutputConfiguration = apply {
                if (usePhysicalOutputRouting) {
                    physicalCameraId?.let(::setPhysicalCameraId)
                }
            }
            val outputConfigurations = listOfNotNull(
                OutputConfiguration(surface).routeToPhysicalLens(),
                OutputConfiguration(imageReaderSurface).routeToPhysicalLens(),
                rawImageReader?.surface?.let { rawSurface ->
                    OutputConfiguration(rawSurface).routeToPhysicalLens()
                },
                analysisImageReader?.surface?.let { analysisSurface ->
                    OutputConfiguration(analysisSurface).routeToPhysicalLens()
                }
            )
            runCatching {
                device.createCaptureSessionByOutputConfigurations(
                    outputConfigurations,
                    callback,
                    handler
                )
            }.onFailure { error ->
                handleCreateSessionFailure(error, generation)
            }
        } else {
            runCatching {
                device.createCaptureSession(
                    listOfNotNull(
                        surface,
                        imageReaderSurface,
                        rawImageReader?.surface,
                        analysisImageReader?.surface
                    ),
                    callback,
                    handler
                )
            }.onFailure { error ->
                handleCreateSessionFailure(error, generation)
            }
        }
    }

    private fun resetSessionOutputs() {
        captureSession?.close()
        captureSession = null
        imageReader?.close()
        imageReader = null
        rawImageReader?.close()
        rawImageReader = null
        analysisImageReader?.close()
        analysisImageReader = null
        previewSurface?.release()
        previewSurface = null
        previewRequestBuilder = null
    }

    private fun handleCreateSessionFailure(
        error: Throwable,
        generation: Long
    ) {
        Log.e(LOG_TAG, "Create camera session failed", error)
        resetSessionOutputs()
        if (!isCurrentGeneration(generation)) {
            return
        }

        if (error.isClosedCameraError()) {
            cameraDevice = null
            notifyCaptureStatus("Caméra fermée, réouverture")
            openCamera(generation)
            return
        }

        if (usePhysicalOutputRouting) {
            notifyCaptureStatus("Objectif physique : secours logique")
            switchToLogicalOutputRouting()
            createPreviewSession(generation)
        } else {
            notifyCaptureStatus("Échec création session")
        }
    }

    private fun switchToLogicalOutputRouting() {
        usePhysicalOutputRouting = false
        previewSize = fallbackPreviewSize
        captureSize = fallbackCaptureSize
        rawSize = fallbackRawSize
        zoomRatio = fallbackZoomRatio
    }

    private fun nextSessionGeneration(): Long {
        sessionGeneration += 1L
        return sessionGeneration
    }

    private fun isCurrentGeneration(generation: Long): Boolean {
        return generation == sessionGeneration && textureView != null && cameraId != null
    }

    private fun applyRepeatingRequest(): Boolean {
        val session = captureSession ?: return false
        val builder = previewRequestBuilder ?: return false
        val handler = backgroundHandler ?: return false

        builder.applyManualSettings(
            iso = iso,
            exposureNanos = exposureNanos.coerceAtMost(MAX_PREVIEW_EXPOSURE_NANOS),
            controlMode = CameraMetadata.CONTROL_MODE_AUTO,
            physicalCameraId = physicalCameraId,
            zoomRatio = zoomRatio,
            manualSensor = supportsManualSensor,
            whiteBalanceMode = whiteBalanceMode
        )

        return runCatching {
            session.setRepeatingRequest(builder.build(), null, handler)
            true
        }.getOrElse { error ->
            Log.e(LOG_TAG, "Repeating request failed", error)
            false
        }
    }

    private fun CaptureRequest.Builder.applyManualSettings(
        iso: Int,
        exposureNanos: Long,
        controlMode: Int,
        physicalCameraId: String?,
        zoomRatio: Float?,
        manualSensor: Boolean,
        whiteBalanceMode: WhiteBalanceMode
    ) {
        if (!manualSensor) {
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
            applyZoomRatio(zoomRatio)
            applyWhiteBalance(whiteBalanceMode)
            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            return
        }

        set(CaptureRequest.CONTROL_MODE, controlMode)
        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        set(CaptureRequest.SENSOR_SENSITIVITY, iso)
        set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNanos)
        set(CaptureRequest.SENSOR_FRAME_DURATION, exposureNanos.coerceAtLeast(MIN_FRAME_DURATION_NANOS))
        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
        applyZoomRatio(zoomRatio)
        applyWhiteBalance(whiteBalanceMode)
        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && physicalCameraId != null) {
            runCatching {
                setPhysicalCameraKey(CaptureRequest.SENSOR_SENSITIVITY, iso, physicalCameraId)
                setPhysicalCameraKey(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNanos, physicalCameraId)
                setPhysicalCameraKey(
                    CaptureRequest.SENSOR_FRAME_DURATION,
                    exposureNanos.coerceAtLeast(MIN_FRAME_DURATION_NANOS),
                    physicalCameraId
                )
                setPhysicalCameraKey(CaptureRequest.LENS_FOCUS_DISTANCE, 0f, physicalCameraId)
            }
        }
    }

    private fun CaptureRequest.Builder.applyZoomRatio(zoomRatio: Float?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && zoomRatio != null) {
            set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
        }
    }

    private fun CaptureRequest.Builder.applyWhiteBalance(whiteBalanceMode: WhiteBalanceMode) {
        when (whiteBalanceMode) {
            WhiteBalanceMode.AUTO -> {
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_FAST)
            }

            WhiteBalanceMode.STORM_4300K -> {
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)
                set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_FAST)
            }
        }
    }

    private fun saveNextImage(reader: ImageReader) {
        val image = reader.acquireNextImage() ?: return
        try {
            val bytes = image.toByteArray()
            val uri = saveJpeg(bytes)
            if (uri != null) {
                notifyMediaSaved(SavedMedia(uri, MIME_TYPE_JPEG, "JPG"))
            }
            notifyCaptureStatus(
                if (uri != null) {
                    listOf(lastCaptureResultStatus, "Photo enregistrée")
                        .filter { it.isNotBlank() }
                        .joinToString(" - ")
                } else {
                    "Échec enregistrement"
                }
            )
        } catch (error: IOException) {
            notifyCaptureStatus("Échec fichier : ${error.message.orEmpty()}")
        } finally {
            image.close()
        }
    }

    private fun saveNextRawImage(reader: ImageReader) {
        val image = reader.acquireNextImage() ?: return
        if (latestCaptureResult == null) {
            pendingRawImage?.close()
            pendingRawImage = image
            return
        }

        try {
            saveRawImage(image)
        } finally {
            image.close()
        }
    }

    private fun analyzeNextPreviewImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            val luminance = image.averageLuminance()
            val previousLuminance = lastAnalysisLuminance
            lastAnalysisLuminance = luminance
            if (previousLuminance <= 0.0) {
                return
            }

            val now = SystemClock.elapsedRealtime()
            val delta = luminance - previousLuminance
            if (
                delta >= lightningLuminanceDeltaThreshold &&
                now - lastLightningTriggerMillis >= LIGHTNING_TRIGGER_COOLDOWN_MILLIS
            ) {
                lastLightningTriggerMillis = now
                notifyCaptureStatus("Hausse lumière détectée")
                notifyLightningDetected()
            }
        } finally {
            image.close()
        }
    }

    private fun notifyLightningDetected() {
        mainHandler.post {
            onLightningDetected()
        }
    }

    private fun saveRawImage(image: Image) {
        try {
            val characteristics = rawCameraCharacteristics ?: cameraCharacteristics
            val result = latestRawCaptureResult()
            if (characteristics == null || result == null) {
                notifyCaptureStatus("RAW reçu sans métadonnées")
                return
            }

            val uri = saveDng(image, characteristics, result)
            if (uri != null) {
                notifyMediaSaved(SavedMedia(uri, MIME_TYPE_DNG, "RAW"))
            }
            notifyCaptureStatus(
                if (uri != null) {
                    listOf(lastCaptureResultStatus, "RAW enregistré")
                        .filter { it.isNotBlank() }
                        .joinToString(" - ")
                } else {
                    "Échec RAW"
                }
            )
        } catch (error: Throwable) {
            Log.e(LOG_TAG, "DNG save failed", error)
            notifyCaptureStatus("Échec RAW : ${error.message.orEmpty()}")
        }
    }

    @Suppress("DEPRECATION")
    private fun latestRawCaptureResult(): CaptureResult? {
        val result = latestCaptureResult ?: return null
        val physicalId = physicalCameraId
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && physicalId != null) {
            result.physicalCameraResults[physicalId] ?: result
        } else {
            result
        }
    }

    private fun saveJpeg(bytes: ByteArray): Uri? {
        val fileName = "Storm_${System.currentTimeMillis()}.jpg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE_JPEG)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Storm")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null

            try {
                val didWrite = resolver.openOutputStream(uri)?.use { output ->
                    output.write(bytes)
                    true
                } ?: false
                if (!didWrite) {
                    resolver.delete(uri, null, null)
                    return null
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "Storm"
            )
            if (!directory.exists() && !directory.mkdirs()) {
                return null
            }
            val file = File(directory, fileName)
            FileOutputStream(file).use { output ->
                output.write(bytes)
            }
            Uri.fromFile(file)
        }
    }

    private fun saveDng(
        image: Image,
        characteristics: CameraCharacteristics,
        result: CaptureResult
    ): Uri? {
        val fileName = "Storm_${System.currentTimeMillis()}.dng"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE_DNG)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Storm")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null

            try {
                val didWrite = resolver.openOutputStream(uri)?.use { output ->
                    DngCreator(characteristics, result).use { dngCreator ->
                        dngCreator.setOrientation(latestDngOrientation)
                        dngCreator.writeImage(output, image)
                    }
                    true
                } ?: false
                if (!didWrite) {
                    resolver.delete(uri, null, null)
                    return null
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (error: IOException) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "Storm"
            )
            if (!directory.exists() && !directory.mkdirs()) {
                return null
            }
            val file = File(directory, fileName)
            try {
                FileOutputStream(file).use { output ->
                    DngCreator(characteristics, result).use { dngCreator ->
                        dngCreator.setOrientation(latestDngOrientation)
                        dngCreator.writeImage(output, image)
                    }
                }
            } catch (error: Throwable) {
                file.delete()
                throw error
            }
            Uri.fromFile(file)
        }
    }

    private fun notifyCaptureStatus(status: String) {
        mainHandler.post {
            onCaptureStatus(status)
        }
    }

    private fun notifyMediaSaved(media: SavedMedia) {
        mainHandler.post {
            onMediaSaved(media)
        }
    }

    private fun startCaptureCountdown(exposureNanos: Long) {
        cancelCaptureCountdown()

        val durationMillis = (exposureNanos / 1_000_000L).coerceAtLeast(1L)
        val finishedAt = SystemClock.elapsedRealtime() + durationMillis
        countdownRunnable = object : Runnable {
            override fun run() {
                if (!isCapturing) {
                    return
                }

                val remainingMillis = (finishedAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                notifyCaptureStatus("Capture: ${formatRemainingTime(remainingMillis)} restantes")
                if (remainingMillis > 0L) {
                    mainHandler.postDelayed(this, COUNTDOWN_UPDATE_MILLIS)
                }
            }
        }.also { runnable ->
            runnable.run()
        }
    }

    private fun cancelCaptureCountdown() {
        countdownRunnable?.let(mainHandler::removeCallbacks)
        countdownRunnable = null
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("Camera2Preview").also { thread ->
            thread.start()
            backgroundHandler = Handler(thread.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            backgroundThread = null
            backgroundHandler = null
        }
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            textureView?.configurePreviewTransform(previewSize)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            stop()
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    private fun createCameraStateCallback(generation: Long) = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            if (!isCurrentGeneration(generation) || camera.id != cameraId || textureView == null) {
                camera.close()
                return
            }
            cameraDevice = camera
            createPreviewSession(generation)
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.e(LOG_TAG, "Camera disconnected: ${camera.id}")
            camera.close()
            if (cameraDevice == camera) {
                cameraDevice = null
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(LOG_TAG, "Camera error ${camera.id}: $error")
            camera.close()
            if (cameraDevice == camera) {
                cameraDevice = null
            }
        }
    }

    private var previewSize = Size(1280, 720)
    private var captureSize = Size(1920, 1080)
    private var rawSize = Size(1920, 1080)
    private var fallbackPreviewSize = Size(1280, 720)
    private var fallbackCaptureSize = Size(1920, 1080)
    private var fallbackRawSize = Size(1920, 1080)

    private companion object {
        const val MAX_PENDING_IMAGES = 2
        const val MAX_ANALYSIS_IMAGES = 2
        const val MAX_PREVIEW_EXPOSURE_NANOS = 33_333_333L
        const val MIN_FRAME_DURATION_NANOS = 33_333_333L
        const val COUNTDOWN_UPDATE_MILLIS = 250L
    }
}

private fun Image.toByteArray(): ByteArray {
    val buffer = planes.first().buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}

private fun Image.averageLuminance(): Double {
    val buffer = planes.first().buffer
    val remaining = buffer.remaining()
    if (remaining <= 0) {
        return 0.0
    }

    val step = max(1, remaining / LIGHTNING_ANALYSIS_SAMPLE_COUNT)
    var index = buffer.position()
    val end = buffer.limit()
    var sum = 0L
    var count = 0
    while (index < end) {
        sum += buffer.get(index).toInt() and 0xFF
        count += 1
        index += step
    }

    return if (count > 0) sum.toDouble() / count.toDouble() else 0.0
}

private fun Long.nanosToMs(): Float = this / 1_000_000f

private fun Float.msToNanos(): Long = (this * 1_000_000f).roundToLong()

private fun Int.toExifOrientation(): Int = when (((this % 360) + 360) % 360) {
    90 -> ExifInterface.ORIENTATION_ROTATE_90
    180 -> ExifInterface.ORIENTATION_ROTATE_180
    270 -> ExifInterface.ORIENTATION_ROTATE_270
    else -> ExifInterface.ORIENTATION_NORMAL
}

private fun computeGroundLevelDegrees(x: Float, y: Float): Float {
    val rawDegrees = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
    return when {
        rawDegrees > 90f -> rawDegrees - 180f
        rawDegrees < -90f -> rawDegrees + 180f
        else -> rawDegrees
    }
}

private fun Float.roundToTenth(): Float = (this * 10f).roundToInt() / 10f

private fun formatLevelDegrees(degrees: Float): String {
    val rounded = degrees.roundToTenth()
    if (abs(rounded) < 0.05f) {
        return "0°"
    }

    val roundedInt = rounded.roundToInt()
    return if (abs(rounded - roundedInt) < 0.05f) {
        "$roundedInt°"
    } else {
        String.format(Locale.FRANCE, "%.1f°", rounded)
    }
}

private fun formatExposure(exposureMs: Float): String {
    val seconds = exposureMs / 1_000f
    return if (seconds > 0f) {
        val denominator = (1f / seconds).roundToInt()
        when {
            denominator >= 2 -> "1/$denominator s"
            else -> String.format(Locale.US, "%.2f s", seconds)
        }
    } else {
        String.format(Locale.US, "%.2f ms", exposureMs)
    }
}

private fun buildExposureSteps(
    range: ClosedFloatingPointRange<Float>
): List<Float> {
    val candidates = mutableListOf(
        1f,
        2f,
        4f,
        8f,
        1_000f / 60f,
        1_000f / 30f,
        1_000f / 15f,
        125f,
        250f,
        500f
    )
    var seconds = 1
    while (seconds * 1_000f <= range.endInclusive) {
        candidates += seconds * 1_000f
        seconds += 1
    }

    val steps = candidates
        .filter { it in range }
        .distinctBy { it.roundToInt() }
        .sorted()

    return if (steps.isNotEmpty()) {
        steps
    } else {
        listOf(range.start, range.endInclusive).distinct()
    }
}

private fun List<Float>.nearestIndex(target: Float): Int {
    if (isEmpty()) return 0

    var nearestIndex = 0
    var nearestDistance = Float.MAX_VALUE
    forEachIndexed { index, candidate ->
        val distance = if (candidate > target) {
            candidate - target
        } else {
            target - candidate
        }
        if (distance < nearestDistance) {
            nearestDistance = distance
            nearestIndex = index
        }
    }
    return nearestIndex
}

private fun formatRemainingTime(remainingMillis: Long): String {
    return if (remainingMillis >= 1_000L) {
        String.format(Locale.US, "%.1f s", remainingMillis / 1_000f)
    } else {
        "${remainingMillis} ms"
    }
}

private fun Size.formatResolution(): String {
    return "${width}x${height} (${megapixelLabel()})"
}

private fun Size.aspectRatio(): Float {
    return width.toFloat() / height.toFloat()
}

private fun Size.displayAspectRatio(displayRotationDegrees: Int): Float {
    val ratio = aspectRatio()
    return if (displayRotationDegrees == 90 || displayRotationDegrees == 270) {
        ratio
    } else {
        1f / ratio
    }
}

private fun Size.megapixelLabel(): String {
    val megapixels = width.toDouble() * height.toDouble() / 1_000_000.0
    return String.format(Locale.US, "%.1f MP", megapixels)
}

private val DEFAULT_ISO_RANGE = Range(50, 3200)
private val DEFAULT_EXPOSURE_RANGE = Range(1_000_000L, 10_000_000_000L)
private val TIMER_OPTIONS = listOf(0, 1, 2, 3, 4, 5, 10)
private const val MAX_UI_ISO = 200
private const val APP_VERSION_NAME = "1.0"
private const val APP_YEAR = 2026
private const val CRASH_LOG_FILE = "storm-crash.log"
private const val LOG_TAG = "StormCamera"
private const val MIME_TYPE_JPEG = "image/jpeg"
private const val MIME_TYPE_DNG = "image/x-adobe-dng"
private const val ANALYSIS_MAX_WIDTH = 640
private const val ANALYSIS_MAX_HEIGHT = 480
private const val LIGHTNING_ANALYSIS_SAMPLE_COUNT = 2_000
private const val LIGHTNING_NORMAL_LUMINANCE_DELTA_THRESHOLD = 35.0
private const val LIGHTNING_TRIGGER_COOLDOWN_MILLIS = 2_500L
private const val NATIVE_CAPTURE_WIDTH = 4032
private const val NATIVE_CAPTURE_HEIGHT = 3024
private const val NATIVE_CAPTURE_MAX_PIXELS = 13_000_000L
private const val ASPECT_RATIO_TOLERANCE = 0.02f
private const val ULTRA_WIDE_MAX_FOCAL_LENGTH_MM = 2.5f

private fun Array<Size>?.choosePreviewSize(referenceSize: Size): Size {
    val sizes = this?.toList().orEmpty()
    val referenceRatio = referenceSize.width.toFloat() / referenceSize.height.toFloat()
    val boundedSizes = sizes.filter { size ->
        size.width <= 1920 && size.height <= 1440
    }
    val ratioMatchedSizes = boundedSizes.filter { size ->
        val ratio = size.width.toFloat() / size.height.toFloat()
        kotlin.math.abs(ratio - referenceRatio) < 0.02f
    }
    return (ratioMatchedSizes.ifEmpty { boundedSizes.ifEmpty { sizes } })
        .maxByOrNull { size -> size.width.toLong() * size.height.toLong() }
        ?: Size(1280, 720)
}

private fun Array<Size>?.chooseAnalysisSize(referenceSize: Size): Size {
    val sizes = this?.toList().orEmpty()
    val referenceRatio = referenceSize.aspectRatio()
    val ratioMatchedSizes = sizes.filter { size ->
        kotlin.math.abs(size.aspectRatio() - referenceRatio) < ASPECT_RATIO_TOLERANCE
    }
    return ratioMatchedSizes
        .filter { size -> size.width <= ANALYSIS_MAX_WIDTH && size.height <= ANALYSIS_MAX_HEIGHT }
        .maxByOrNull { size -> size.pixelCount() }
        ?: ratioMatchedSizes.minByOrNull { size -> size.pixelCount() }
        ?: sizes.minByOrNull { size -> size.pixelCount() }
        ?: Size(640, 480)
}

private fun Array<Size>?.chooseCaptureSize(): Size {
    val sizes = this?.toList().orEmpty()
    val nativeRatio = NATIVE_CAPTURE_WIDTH.toFloat() / NATIVE_CAPTURE_HEIGHT.toFloat()
    val nativeSized = sizes.firstOrNull { size ->
        size.matchesDimensions(NATIVE_CAPTURE_WIDTH, NATIVE_CAPTURE_HEIGHT)
    }
    if (nativeSized != null) {
        return nativeSized
    }

    val nativeRatioSizes = sizes.filter { size ->
        kotlin.math.abs(size.aspectRatio() - nativeRatio) < ASPECT_RATIO_TOLERANCE
    }
    return nativeRatioSizes
        .filter { size -> size.pixelCount() <= NATIVE_CAPTURE_MAX_PIXELS }
        .maxByOrNull { size -> size.pixelCount() }
        ?: nativeRatioSizes.maxByOrNull { size -> size.pixelCount() }
        ?: sizes.maxByOrNull { size -> size.pixelCount() }
        ?: Size(1920, 1080)
}

private fun Size.matchesDimensions(
    expectedWidth: Int,
    expectedHeight: Int
): Boolean {
    return (width == expectedWidth && height == expectedHeight) ||
        (width == expectedHeight && height == expectedWidth)
}

private fun Size.pixelCount(): Long {
    return width.toLong() * height.toLong()
}

private fun Array<Size>?.chooseRawSize(): Size {
    val sizes = this?.toList().orEmpty()
    return sizes
        .maxByOrNull { size -> size.width.toLong() * size.height.toLong() }
        ?: Size(1920, 1080)
}

private fun installCrashLogger(context: Context) {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val stackTrace = StringWriter().also { writer ->
                throwable.printStackTrace(PrintWriter(writer))
            }.toString()
            val crashText = buildString {
                appendLine("[$timestamp] ${thread.name}")
                appendLine("${throwable::class.java.name}: ${throwable.message.orEmpty()}")
                appendLine(stackTrace)
                appendLine()
            }

            File(context.filesDir, CRASH_LOG_FILE).appendText(crashText)
            Log.e(LOG_TAG, crashText)
        }
        previousHandler?.uncaughtException(thread, throwable)
    }
}
