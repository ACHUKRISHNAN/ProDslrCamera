package com.pro.dslrcamera.ui

import android.content.Intent
import android.opengl.GLSurfaceView
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pro.dslrcamera.audio.ShutterSoundPlayer
import com.pro.dslrcamera.camera.Camera2Controller
import com.pro.dslrcamera.gl.DslrGlRenderer
import com.pro.dslrcamera.model.*
import com.pro.dslrcamera.ui.components.*
import kotlinx.coroutines.delay

@Composable
fun DslrCameraScreen(
    cameraController: Camera2Controller,
    shutterSoundPlayer: ShutterSoundPlayer
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(CameraUiState()) }

    val glRenderer = remember {
        DslrGlRenderer { surfaceTexture ->
            cameraController.openCamera(surfaceTexture, state.isFrontCamera)
        }
    }

    LaunchedEffect(state) {
        glRenderer.currentPreset = state.activeFilmPreset
        cameraController.applyState(state)
    }

    LaunchedEffect(state.isBulbActive) {
        if (state.isBulbActive) {
            val start = System.currentTimeMillis()
            while (state.isBulbActive) {
                state = state.copy(bulbElapsedMs = System.currentTimeMillis() - start)
                delay(50L)
            }
        }
    }

    fun adjustActiveParameter(delta: Int) {
        state = when (state.activeParameter) {
            ControlParameter.ISO -> state.copy(
                isoIndex = (state.isoIndex + delta).coerceIn(0, CameraStepTables.isoValues.lastIndex)
            )
            ControlParameter.APERTURE -> state.copy(
                apertureIndex = (state.apertureIndex + delta).coerceIn(0, CameraStepTables.apertureValues.lastIndex)
            )
            ControlParameter.SHUTTER -> {
                val nextIdx = (state.shutterIndex + delta).coerceIn(0, CameraStepTables.shutterValues.lastIndex)
                val isBulb = CameraStepTables.shutterValues[nextIdx].isBulb
                state.copy(
                    shutterIndex = nextIdx,
                    exposureMode = if (isBulb) ExposureMode.BULB else state.exposureMode
                )
            }
            ControlParameter.WB -> state.copy(
                wbIndex = (state.wbIndex + delta).coerceIn(0, CameraStepTables.wbKelvinValues.lastIndex)
            )
            ControlParameter.FOCUS -> state.copy(
                focusIndex = (state.focusIndex + delta).coerceIn(0, CameraStepTables.focusLabels.lastIndex)
            )
        }
    }

    fun triggerCapture() {
        val isBulbMode = state.exposureMode == ExposureMode.BULB ||
            CameraStepTables.shutterValues[state.shutterIndex].isBulb

        if (isBulbMode) {
            if (!state.isBulbActive) {
                shutterSoundPlayer.playShutterClick()
                state = state.copy(isBulbActive = true, bulbElapsedMs = 0L)
            } else {
                shutterSoundPlayer.playShutterClick()
                state = state.copy(isBulbActive = false)
                cameraController.captureStillPhoto()
            }
        } else {
            shutterSoundPlayer.playShutterClick()
            cameraController.captureStillPhoto()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(126.dp)
                    .fillMaxHeight()
                    .padding(vertical = 14.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    IconButton(
                        onClick = { state = state.copy(showQuickMenu = !state.showQuickMenu) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF242424))
                        .border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                        .clickable {
                            state.lastCapturedUri?.let { uri ->
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val thumb = state.lastCapturedThumb
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = "Last Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Photo, contentDescription = null, tint = Color.Gray)
                    }
                }

                DslrModeDial(
                    currentMode = state.exposureMode,
                    onModeSelected = { mode ->
                        state = state.copy(
                            exposureMode = mode,
                            shutterIndex = if (mode == ExposureMode.BULB) CameraStepTables.shutterValues.lastIndex else state.shutterIndex
                        )
                    }
                )

                Spacer(modifier = Modifier.height(52.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(bottom = 78.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            GLSurfaceView(ctx).apply {
                                setEGLContextClientVersion(2)
                                setRenderer(glRenderer)
                                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    ViewfinderInteractiveOverlay(
                        gridMode = state.gridMode,
                        mirrorSpiral = state.mirrorSpiral,
                        focusNorm = state.focusReticleNorm,
                        isFocusLocked = state.isFocusLocked,
                        onTapToFocus = { norm ->
                            state = state.copy(focusReticleNorm = norm, isFocusLocked = true)
                            cameraController.triggerTapToFocus(norm)
                        },
                        onDragFocus = { norm ->
                            state = state.copy(focusReticleNorm = norm, isFocusLocked = true)
                            cameraController.triggerTapToFocus(norm)
                        },
                        onPinchZoom = { factor ->
                            state = state.copy(zoomRatio = (state.zoomRatio * factor).coerceIn(0.5f, 8.0f))
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x99181818))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.clickable { state = state.copy(isFlashOn = !state.isFlashOn) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (state.isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Flash",
                                    tint = if (state.isFlashOn) AmberAccent else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = if (state.isFlashOn) "ON" else "OFF",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(
                                modifier = Modifier.clickable { state = state.copy(isRawEnabled = !state.isRawEnabled) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, if (state.isRawEnabled) AmberAccent else Color.White, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (state.isRawEnabled) "RAW" else "JPG",
                                        color = if (state.isRawEnabled) AmberAccent else Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (state.isRawEnabled) "RAW+JPEG" else "FINE JPEG",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 8.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SdStorage, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(state.freeStorageGb, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x99181818))
                                .clickable { state = state.copy(showQuickMenu = !state.showQuickMenu) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pro/", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("DSLR", color = AmberAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (state.isBulbActive) {
                        val secs = state.bulbElapsedMs / 1000.0
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 46.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xCC8B0000))
                                .border(1.dp, AmberAccent, RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = String.format("BULB  %05.1fs", secs),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }



                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 10.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0x991A1A1A))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                            .padding(vertical = 6.dp, horizontal = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        listOf(0.5f to "0.5x", 1.0f to "1x", 3.0f to "3x").forEach { (zoomVal, label) ->
                            val isSelected = kotlin.math.abs(state.zoomRatio - zoomVal) < 0.25f
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                                    .clickable { state = state.copy(zoomRatio = zoomVal) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) AmberAccent else Color(0xFFD0D0D0),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "< TAP OR SPIN\n   DIAL TO ADJUST",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val carouselItems = when (state.activeParameter) {
                            ControlParameter.ISO -> CameraStepTables.isoValues.map { it.toString() }
                            ControlParameter.APERTURE -> CameraStepTables.apertureValues
                            ControlParameter.SHUTTER -> CameraStepTables.shutterValues.map { it.label }
                            ControlParameter.WB -> CameraStepTables.wbKelvinValues.map { "${it}K" }
                            ControlParameter.FOCUS -> CameraStepTables.focusLabels
                        }
                        val carouselIdx = when (state.activeParameter) {
                            ControlParameter.ISO -> state.isoIndex
                            ControlParameter.APERTURE -> state.apertureIndex
                            ControlParameter.SHUTTER -> state.shutterIndex
                            ControlParameter.WB -> state.wbIndex
                            ControlParameter.FOCUS -> state.focusIndex
                        }

                        RotaryValueCarousel(
                            items = carouselItems,
                            selectedIndex = carouselIdx,
                            onIndexChanged = { newIdx ->
                                adjustActiveParameter(newIdx - carouselIdx)
                            }
                        )

                        Text(
                            text = "TAP OR SPIN >\nDIAL TO ADJUST",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .width(128.dp)
                    .fillMaxHeight()
                    .padding(top = 12.dp, bottom = 92.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { state = state.copy(showQuickMenu = !state.showQuickMenu) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Quick Menu",
                        tint = if (state.showQuickMenu) AmberAccent else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Quick Menu",
                        color = if (state.showQuickMenu) AmberAccent else Color.White,
                        fontSize = 9.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222222))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                        .clickable { state = state.copy(isVideoMode = !state.isVideoMode) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isVideoMode) Icons.Default.PhotoCamera else Icons.Default.Videocam,
                        contentDescription = "Switch Photo/Video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E2E2E))
                        .border(2.5.dp, Color(0xFFD0D0D0), CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isBulbActive) Color(0xFFFF3B30) else Color(0xFFE53935)
                        )
                        .pointerInput(state.exposureMode, state.shutterIndex, state.isBulbActive) {
                            detectTapGestures(
                                onPress = {
                                    val isBulb = state.exposureMode == ExposureMode.BULB ||
                                        CameraStepTables.shutterValues[state.shutterIndex].isBulb
                                    if (isBulb && !state.isBulbActive) {
                                        shutterSoundPlayer.playShutterClick()
                                        state = state.copy(isBulbActive = true, bulbElapsedMs = 0L)
                                        val released = tryAwaitRelease()
                                        if (released && state.bulbElapsedMs > 350L) {
                                            shutterSoundPlayer.playShutterClick()
                                            state = state.copy(isBulbActive = false)
                                            cameraController.captureStillPhoto()
                                        }
                                    } else {
                                        tryAwaitRelease()
                                        triggerCapture()
                                    }
                                }
                            )
                        }
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222222))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                        .clickable {
                            state = state.copy(isFrontCamera = !state.isFrontCamera)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Flip Camera",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(78.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xE6141414), Color(0xFF0E0E0E))
                    )
                )
                .padding(start = 112.dp, end = 148.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1D1D1D))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .clickable { state = state.copy(showQuickMenu = !state.showQuickMenu) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Presets", tint = Color.LightGray, modifier = Modifier.size(18.dp))
            }

            val blocks = listOf(
                Triple(ControlParameter.ISO, "ISO:", CameraStepTables.isoValues[state.isoIndex].toString()),
                Triple(ControlParameter.APERTURE, "Aperture:", CameraStepTables.apertureValues[state.apertureIndex]),
                Triple(ControlParameter.SHUTTER, "Shutter Speed:", CameraStepTables.shutterValues[state.shutterIndex].let {
                    if (it.isBulb) "BULB" else "${it.label}s"
                }),
                Triple(ControlParameter.WB, "WB:", "${CameraStepTables.wbKelvinValues[state.wbIndex]}K"),
                Triple(ControlParameter.FOCUS, "Focus:", CameraStepTables.focusLabels[state.focusIndex])
            )

            blocks.forEach { (param, label, valueText) ->
                val isSelected = state.activeParameter == param

                if (isSelected) {
                    MicroStepButton(isLeft = true, onClick = { adjustActiveParameter(-1) })
                }

                Column(
                    modifier = Modifier
                        .weight(if (isSelected) 1.35f else 1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF1E1710) else Color(0xFF181818))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) AmberAccent else Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { state = state.copy(activeParameter = param) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) AmberAccent else Color(0xFF9E9E9E),
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                    Text(
                        text = valueText,
                        color = if (isSelected) AmberAccent else Color.White,
                        fontSize = if (isSelected) 17.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                if (isSelected) {
                    MicroStepButton(isLeft = false, onClick = { adjustActiveParameter(1) })
                }
            }
        }

        TactileJogWheel(
            onStep = { delta -> adjustActiveParameter(delta) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 8.dp, y = 12.dp)
        )

        if (state.showQuickMenu) {
            QuickMenuOverlay(
                state = state,
                onUpdateState = { state = it },
                onDismiss = { state = state.copy(showQuickMenu = false) }
            )
        }
    }
}

@Composable
private fun QuickMenuOverlay(
    state: CameraUiState,
    onUpdateState: (CameraUiState) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .background(Color(0xF5161616))
                .border(1.dp, Color.White.copy(alpha = 0.15f))
                .clickable(enabled = false) {}
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DSLR CREATIVE & OPTICS", color = AmberAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Text("FUJIFILM SIMULATION", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            FilmPreset.entries.chunked(2).forEach { rowPresets ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowPresets.forEach { preset ->
                        val active = state.activeFilmPreset == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AmberAccent.copy(alpha = 0.2f) else Color(0xFF222222))
                                .border(1.dp, if (active) AmberAccent else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable { onUpdateState(state.copy(activeFilmPreset = preset)) }
                                .padding(8.dp)
                        ) {
                            Text(preset.displayName, color = if (active) AmberAccent else Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            Text("COMPOSITION OVERLAY", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GridOverlayMode.entries.forEach { mode ->
                    val active = state.gridMode == mode
                    FilterChip(
                        selected = active,
                        onClick = { onUpdateState(state.copy(gridMode = mode)) },
                        label = { Text(mode.label, fontSize = 11.sp) }
                    )
                }
            }
            if (state.gridMode == GridOverlayMode.GOLDEN_SPIRAL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.mirrorSpiral,
                        onCheckedChange = { onUpdateState(state.copy(mirrorSpiral = it)) }
                    )
                    Text("Mirror Golden Spiral Orientation", color = Color.White, fontSize = 12.sp)
                }
            }

        }
    }
}
