package com.pro.dslrcamera.ui.components

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

val AmberAccent = Color(0xFFFF8C00)
val DarkSurface = Color(0xFF161616)

@Composable
fun RotaryValueCarousel(
    items: List<String>,
    selectedIndex: Int,
    onIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    fun triggerMechanicalTick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val stepWidthPx = 68f

    Box(
        modifier = modifier
            .width(420.dp)
            .height(94.dp)
            .pointerInput(items.size, selectedIndex) {
                detectHorizontalDragGestures(
                    onDragEnd = { dragAccumulator = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                        val stepsMoved = (dragAccumulator / stepWidthPx).toInt()
                        if (stepsMoved != 0) {
                            dragAccumulator -= stepsMoved * stepWidthPx
                            val target = (selectedIndex - stepsMoved).coerceIn(0, items.lastIndex)
                            if (target != selectedIndex) {
                                triggerMechanicalTick()
                                onIndexChanged(target)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val arcRadius = size.width * 0.72f
            val centerX = size.width / 2f
            val centerY = arcRadius + 16.dp.toPx()

            val arcRectTopLeft = Offset(centerX - arcRadius, centerY - arcRadius)
            val arcRectSize = Size(arcRadius * 2f, arcRadius * 2f)

            drawArc(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xD91A1A1A), Color(0xF2101010))
                ),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = arcRectTopLeft,
                size = arcRectSize
            )

            drawArc(
                color = Color.White.copy(alpha = 0.22f),
                startAngle = 228f,
                sweepAngle = 84f,
                useCenter = false,
                topLeft = arcRectTopLeft,
                size = arcRectSize,
                style = Stroke(width = 1.5.dp.toPx())
            )

            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, AmberAccent, Color.Transparent)
                ),
                start = Offset(size.width * 0.28f, size.height - 4.dp.toPx()),
                end = Offset(size.width * 0.72f, size.height - 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )

            val pipPath = Path().apply {
                moveTo(centerX, size.height - 11.dp.toPx())
                lineTo(centerX - 5.dp.toPx(), size.height - 4.dp.toPx())
                lineTo(centerX + 5.dp.toPx(), size.height - 4.dp.toPx())
                close()
            }
            drawPath(pipPath, color = AmberAccent)
        }

        val visibleOffsets = -2..2
        visibleOffsets.forEach { offset ->
            val idx = selectedIndex + offset
            if (idx in items.indices) {
                val angleDeg = offset * 15f
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val radiusPx = 155f
                val xShift = (sin(angleRad) * radiusPx).dp
                val yLift = ((1.0 - cos(angleRad)) * radiusPx * 0.65).dp
                val isCenter = offset == 0

                Box(
                    modifier = Modifier
                        .offset(x = xShift, y = (-16).dp + yLift)
                        .graphicsLayer {
                            rotationZ = angleDeg * 0.85f
                            alpha = when (abs(offset)) {
                                0 -> 1f
                                1 -> 0.78f
                                else -> 0.42f
                            }
                        }
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (idx != selectedIndex) {
                                triggerMechanicalTick()
                                onIndexChanged(idx)
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[idx],
                        color = if (isCenter) AmberAccent else Color(0xFFDADADA),
                        fontSize = if (isCenter) 16.sp else 13.sp,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun MicroStepButton(
    isLeft: Boolean,
    onClick: () -> Unit
) {
    val view = LocalView.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1D1D1D))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isLeft) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isLeft) "Step Down" else "Step Up",
            tint = Color(0xFFB0B0B0),
            modifier = Modifier.size(20.dp)
        )
    }
}
