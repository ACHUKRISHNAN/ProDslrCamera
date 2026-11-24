package com.pro.dslrcamera.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pro.dslrcamera.model.GridOverlayMode

@Composable
fun ViewfinderInteractiveOverlay(
    gridMode: GridOverlayMode,
    mirrorSpiral: Boolean,
    focusNorm: Offset,
    isFocusLocked: Boolean,
    onTapToFocus: (Offset) -> Unit,
    onDragFocus: (Offset) -> Unit,
    onPinchZoom: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) onPinchZoom(zoom)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val norm = Offset(
                        (offset.x / size.width).coerceIn(0f, 1f),
                        (offset.y / size.height).coerceIn(0f, 1f)
                    )
                    onTapToFocus(norm)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val norm = Offset(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        (change.position.y / size.height).coerceIn(0f, 1f)
                    )
                    onDragFocus(norm)
                }
            }
    ) {
        val w = size.width
        val h = size.height

        when (gridMode) {
            GridOverlayMode.RULE_OF_THIRDS -> {
                val gridColor = Color.White.copy(alpha = 0.35f)
                val stroke = 1.dp.toPx()
                for (i in 1..2) {
                    drawLine(gridColor, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), stroke)
                    drawLine(gridColor, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), stroke)
                }
            }
            GridOverlayMode.GOLDEN_SPIRAL -> {
                scale(scaleX = if (mirrorSpiral) -1f else 1f, scaleY = 1f, pivot = center) {
                    drawFibonacciSpiral(w, h)
                }
            }
            GridOverlayMode.NONE -> Unit
        }

        val fx = focusNorm.x * w
        val fy = focusNorm.y * h
        val boxSize = 54.dp.toPx()
        val half = boxSize / 2f
        val bracketLen = 12.dp.toPx()
        val reticleColor = if (isFocusLocked) AmberAccent else Color.White.copy(alpha = 0.9f)
        val strokeW = 1.8.dp.toPx()

        drawLine(reticleColor, Offset(fx - half, fy - half), Offset(fx - half + bracketLen, fy - half), strokeW)
        drawLine(reticleColor, Offset(fx - half, fy - half), Offset(fx - half, fy - half + bracketLen), strokeW)
        drawLine(reticleColor, Offset(fx + half, fy - half), Offset(fx + half - bracketLen, fy - half), strokeW)
        drawLine(reticleColor, Offset(fx + half, fy - half), Offset(fx + half, fy - half + bracketLen), strokeW)
        drawLine(reticleColor, Offset(fx - half, fy + half), Offset(fx - half + bracketLen, fy + half), strokeW)
        drawLine(reticleColor, Offset(fx - half, fy + half), Offset(fx - half, fy + half - bracketLen), strokeW)
        drawLine(reticleColor, Offset(fx + half, fy + half), Offset(fx + half - bracketLen, fy + half), strokeW)
        drawLine(reticleColor, Offset(fx + half, fy + half), Offset(fx + half, fy + half - bracketLen), strokeW)

        drawCircle(color = reticleColor, radius = 2.5.dp.toPx(), center = Offset(fx, fy))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFibonacciSpiral(w: Float, h: Float) {
    val lineColor = AmberAccent.copy(alpha = 0.55f)
    val stroke = 1.2.dp.toPx()
    var left = 0f
    var top = 0f
    var right = w
    var bottom = h
    val phi = 1.6180339f

    for (step in 0 until 7) {
        val rectW = right - left
        val rectH = bottom - top
        when (step % 4) {
            0 -> {
                val sq = rectW / phi
                drawLine(lineColor, Offset(left + sq, top), Offset(left + sq, bottom), stroke)
                drawArc(lineColor, 180f, 90f, false, Offset(left, top), Size(sq * 2, rectH * 2), style = Stroke(stroke))
                left += sq
            }
            1 -> {
                val sq = rectH / phi
                drawLine(lineColor, Offset(left, top + sq), Offset(right, top + sq), stroke)
                drawArc(lineColor, 270f, 90f, false, Offset(right - rectW * 2, top), Size(rectW * 2, sq * 2), style = Stroke(stroke))
                top += sq
            }
            2 -> {
                val sq = rectW / phi
                drawLine(lineColor, Offset(right - sq, top), Offset(right - sq, bottom), stroke)
                drawArc(lineColor, 0f, 90f, false, Offset(right - sq * 2, bottom - rectH * 2), Size(sq * 2, rectH * 2), style = Stroke(stroke))
                right -= sq
            }
            3 -> {
                val sq = rectH / phi
                drawLine(lineColor, Offset(left, bottom - sq), Offset(right, bottom - sq), stroke)
                drawArc(lineColor, 90f, 90f, false, Offset(left, bottom - sq * 2), Size(rectW * 2, sq * 2), style = Stroke(stroke))
                bottom -= sq
            }
        }
    }
}
