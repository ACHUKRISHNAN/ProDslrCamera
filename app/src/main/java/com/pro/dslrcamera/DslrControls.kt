package com.pro.dslrcamera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

val HudDark = Color(0xCC111114)
val AmberAccent = Color(0xFFF59E0B)

@Composable
fun RotatableModeDial(
    currentMode: ExposureMode,
    onModeSelected: (ExposureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val modes = ExposureMode.entries
    val modeCount = modes.size.coerceAtLeast(1)
    val currentIndex = modes.indexOf(currentMode).coerceAtLeast(0)
    val stepAngle = 360f / modeCount

    var activeIdx by remember(currentMode) { mutableIntStateOf(currentIndex) }
    var lastTouchAngle by remember { mutableFloatStateOf(0f) }
    var angleAccum by remember { mutableFloatStateOf(0f) }

    val latestOnModeSelected by rememberUpdatedState(onModeSelected)
    val outerRingRadiusPx = with(density) { 35.dp.toPx() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(98.dp)
            .clip(CircleShape)
            .background(HudDark)
            .border(1.5.dp, Color(0xFF52525B), CircleShape)
            .pointerInput(modeCount) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        lastTouchAngle = (atan2(offset.y - cy, offset.x - cx) * (180.0 / PI)).toFloat()
                        angleAccum = 0f
                    },
                    onDrag = { change, _ ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val currentTouchAngle =
                            (atan2(change.position.y - cy, change.position.x - cx) * (180.0 / PI)).toFloat()
                        var delta = currentTouchAngle - lastTouchAngle
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        lastTouchAngle = currentTouchAngle
                        angleAccum += delta.coerceIn(-60f, 60f)

                        if (angleAccum >= stepAngle) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeIdx = (activeIdx + 1) % modeCount
                            latestOnModeSelected(modes[activeIdx])
                            angleAccum = 0f
                        } else if (angleAccum <= -stepAngle) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeIdx = (activeIdx - 1 + modeCount) % modeCount
                            latestOnModeSelected(modes[activeIdx])
                            angleAccum = 0f
                        }
                    }
                )
            }
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val next = (activeIdx + 1) % modeCount
                activeIdx = next
                latestOnModeSelected(modes[next])
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            val tickCount = 24
            for (i in 0 until tickCount) {
                val angleRad = (i * (360f / tickCount) - 90f) * (PI / 180.0)
                val innerR = radius * 0.92f
                val outerR = radius * 0.99f
                drawLine(
                    color = Color(0x44A1A1AA),
                    start = Offset(
                        center.x + (cos(angleRad) * innerR).toFloat(),
                        center.y + (sin(angleRad) * innerR).toFloat()
                    ),
                    end = Offset(
                        center.x + (cos(angleRad) * outerR).toFloat(),
                        center.y + (sin(angleRad) * outerR).toFloat()
                    ),
                    strokeWidth = 1.dp.toPx()
                )
            }

            drawCircle(
                color = Color(0xFF3F3F46),
                radius = radius * 0.42f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF18181B),
                radius = radius * 0.40f,
                center = center
            )
            drawCircle(
                color = AmberAccent,
                radius = 3.5.dp.toPx(),
                center = center
            )
        }

        modes.forEachIndexed { idx, mode ->
            val degFromTop = idx * stepAngle
            val rad = ((degFromTop - 90f) * (PI / 180.0)).toFloat()
            val isAuto = mode.name.equals("AUTO", ignoreCase = true)
            val rPx = if (isAuto) outerRingRadiusPx - 1f else outerRingRadiusPx
            val dx = cos(rad) * rPx
            val dy = sin(rad) * rPx
            val isSelected = idx == activeIdx

            Text(
                text = mode.name,
                color = if (isSelected) AmberAccent else Color(0xFFA1A1AA),
                fontSize = if (isAuto) 6.sp else 7.5.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    translationX = dx
                    translationY = dy
                    val scale = if (isSelected) 1.15f else 1.0f
                    scaleX = scale
                    scaleY = scale
                }
            )
        }
    }
}

@Composable
fun ThumbJogWheel(
    enabled: Boolean,
    isSelectingParam: Boolean,
    onDialClick: () -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var wheelRotation by remember { mutableFloatStateOf(0f) }
    var lastTouchAngle by remember { mutableFloatStateOf(0f) }
    var angleAccum by remember { mutableFloatStateOf(0f) }

    val detentDegrees = 45f
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnStep by rememberUpdatedState(onStep)
    val latestOnDialClick by rememberUpdatedState(onDialClick)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(HudDark)
            .border(
                width = 1.5.dp,
                color = if (isSelectingParam) AmberAccent else Color(0xFF52525B),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        lastTouchAngle = (atan2(offset.y - cy, offset.x - cx) * (180.0 / PI)).toFloat()
                        angleAccum = 0f
                    },
                    onDrag = { change, _ ->
                        if (!latestEnabled) return@detectDragGestures
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val currentTouchAngle =
                            (atan2(change.position.y - cy, change.position.x - cx) * (180.0 / PI)).toFloat()
                        var rawDelta = currentTouchAngle - lastTouchAngle
                        if (rawDelta > 180f) rawDelta -= 360f
                        if (rawDelta < -180f) rawDelta += 360f
                        lastTouchAngle = currentTouchAngle

                        val clampedDelta = rawDelta.coerceIn(-45f, 45f)
                        wheelRotation += clampedDelta
                        angleAccum += clampedDelta

                        if (angleAccum >= detentDegrees) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnStep(1)
                            angleAccum -= detentDegrees
                        } else if (angleAccum <= -detentDegrees) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnStep(-1)
                            angleAccum += detentDegrees
                        }
                    }
                )
            }
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                latestOnDialClick()
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .graphicsLayer {
                    rotationZ = wheelRotation
                }
        ) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val ticks = 24
            for (i in 0 until ticks) {
                val angleRad = (i * (360f / ticks) - 90f) * (PI / 180.0)
                val innerR = radius * 0.72f
                val outerR = radius * 0.96f
                drawLine(
                    color = if (i == 0) AmberAccent else Color(0xFF71717A),
                    start = Offset(
                        center.x + (cos(angleRad) * innerR).toFloat(),
                        center.y + (sin(angleRad) * innerR).toFloat()
                    ),
                    end = Offset(
                        center.x + (cos(angleRad) * outerR).toFloat(),
                        center.y + (sin(angleRad) * outerR).toFloat()
                    ),
                    strokeWidth = if (i == 0) 3.dp.toPx() else 1.5.dp.toPx()
                )
            }
            drawCircle(
                color = Color(0xFF27272A),
                radius = radius * 0.56f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

@Composable
fun RotaryValueCarousel(
    paramLabel: String,
    values: List<String>,
    selectedIndex: Int,
    isAutoLocked: Boolean,
    isSelectingParam: Boolean,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val stepPx = 36f
    val safeIdx = selectedIndex.coerceIn(0, (values.size - 1).coerceAtLeast(0))
    val currentVal = values.getOrElse(safeIdx) { "--" }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(HudDark)
            .border(
                width = 1.dp,
                color = if (isSelectingParam) AmberAccent else Color(0xFF3F3F46),
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(values.size, safeIdx, isAutoLocked) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        if (isAutoLocked && !isSelectingParam) return@detectHorizontalDragGestures
                        dragAccum += dragAmount
                        if (dragAccum >= stepPx) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectIndex((safeIdx + 1).coerceAtMost((values.size - 1).coerceAtLeast(0)))
                            dragAccum -= stepPx
                        } else if (dragAccum <= -stepPx) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectIndex((safeIdx - 1).coerceAtLeast(0))
                            dragAccum += stepPx
                        }
                    }
                )
            }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = if (isSelectingParam) "SELECT PARAM" else paramLabel,
            color = if (isSelectingParam) AmberAccent else Color(0xFFA1A1AA),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = values.getOrElse(safeIdx - 1) { "" },
                color = Color(0xFF71717A),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (isAutoLocked && !isSelectingParam) "AUTO" else currentVal,
                color = if (isAutoLocked && !isSelectingParam) Color(0xFF71717A) else AmberAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = values.getOrElse(safeIdx + 1) { "" },
                color = Color(0xFF71717A),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun LensBarrelZoomDial(
    currentZoomRatio: Float,
    isZoomDialActive: Boolean,
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val quickStops = listOf(0.5f, 1.0f, 2.0f, 3.0f, 5.0f, 8.0f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(HudDark, RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = if (isZoomDialActive) AmberAccent else Color(0xFF3F3F46),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = "LENS",
            color = Color(0xFFA1A1AA),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        quickStops.forEach { stop ->
            val selected = kotlin.math.abs(currentZoomRatio - stop) < 0.15f
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) AmberAccent else Color(0xFF27272A))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onZoomSelected(stop)
                    }
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.1fx", stop),
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun RotaryFilmLookWheel(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    presets: List<FilmPreset>,
    selectedPreset: FilmPreset,
    isBypassed: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPresetSelected: (FilmPreset) -> Unit,
    onResetNormal: () -> Unit,
    onHoldBypassChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val presetListState = rememberLazyListState()

    val activePresetIndex = remember(presets, selectedPreset.code) {
        presets.indexOfFirst { it.code == selectedPreset.code }
    }

    LaunchedEffect(activePresetIndex, isExpanded, presets.size) {
        if (activePresetIndex >= 0 && presets.isNotEmpty()) {
            val targetIndex = (activePresetIndex - 1).coerceAtLeast(0)
            if (isExpanded) {
                presetListState.animateScrollToItem(targetIndex)
            } else {
                presetListState.scrollToItem(targetIndex)
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(HudDark)
                    .border(1.dp, AmberAccent.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleExpanded()
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isBypassed) "BYPASS: NORMAL" else "${selectedPreset.code} · ${selectedPreset.name}",
                    color = if (isBypassed) Color(0xFF34D399) else AmberAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBypassed) AmberAccent else HudDark)
                    .border(1.dp, Color(0xFF52525B), RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onHoldBypassChanged(!isBypassed)
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isBypassed) "RAW ON" else "RAW",
                    color = if (isBypassed) Color.Black else Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (selectedPreset.code != "NORMAL") {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HudDark)
                        .border(1.dp, Color(0xFF52525B), RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onResetNormal()
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "RESET",
                        color = Color(0xFFE4E4E7),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (isExpanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HudDark, RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    categories.forEach { cat ->
                        val active = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AmberAccent else Color(0xFF27272A))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onCategorySelected(cat)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = cat,
                                color = if (active) Color.Black else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                LazyRow(
                    state = presetListState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(presets, key = { _, item -> item.code }) { _, preset ->
                        val active = preset.code == selectedPreset.code && !isBypassed
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) AmberAccent.copy(alpha = 0.25f) else Color(0xFF18181B))
                                .border(
                                    width = 1.dp,
                                    color = if (active) AmberAccent else Color(0xFF3F3F46),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onPresetSelected(preset)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset.code,
                                color = if (active) AmberAccent else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = preset.name,
                                color = Color(0xFFD4D4D8),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompositionGridOverlay(
    gridType: CompositionGridType,
    modifier: Modifier = Modifier
) {
    val gridName = gridType.name.uppercase(Locale.US)
    if (gridName == "OFF" || gridName == "NONE") return

    val lineColor = Color.White.copy(alpha = 0.35f)
    val accentColor = AmberAccent.copy(alpha = 0.55f)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .padding(0.dp)
    ) {
        val w = size.width
        val h = size.height

        when {
            gridName.contains("GOLDEN") -> {
                val phi = 0.618f
                val x1 = w * (1f - phi)
                val x2 = w * phi
                val y1 = h * (1f - phi)
                val y2 = h * phi
                for (x in listOf(x1, x2)) {
                    drawLine(lineColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.5f)
                }
                for (y in listOf(y1, y2)) {
                    drawLine(lineColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.5f)
                }
                val rectLeft = x1
                val rectTop = y1
                val rectW = x2 - x1
                val rectH = y2 - y1
                drawArc(
                    color = accentColor,
                    startAngle = 180f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectW * 2f, rectH * 2f),
                    style = Stroke(width = 2.5f)
                )
            }
            gridName.contains("DIAG") -> {
                drawLine(lineColor, Offset(0f, 0f), Offset(w, h), strokeWidth = 1.5f)
                drawLine(lineColor, Offset(w, 0f), Offset(0f, h), strokeWidth = 1.5f)
            }
            gridName.contains("CROSS") || gridName.contains("CENTER") -> {
                val cx = w / 2f
                val cy = h / 2f
                val arm = minOf(w, h) * 0.06f
                drawLine(accentColor, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = 2f)
                drawLine(accentColor, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = 2f)
            }
            else -> {
                for (i in 1..2) {
                    val x = w * (i / 3f)
                    val y = h * (i / 3f)
                    drawLine(lineColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.5f)
                    drawLine(lineColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.5f)
                }
            }
        }
    }
}