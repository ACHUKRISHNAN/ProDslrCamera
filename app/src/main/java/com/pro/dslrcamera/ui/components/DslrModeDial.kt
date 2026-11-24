package com.pro.dslrcamera.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pro.dslrcamera.model.ExposureMode
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DslrModeDial(
    currentMode: ExposureMode,
    onModeSelected: (ExposureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val textMeasurer = rememberTextMeasurer()
    val modes = ExposureMode.entries
    val activeIdx = modes.indexOf(currentMode)

    Canvas(
        modifier = modifier
            .size(84.dp)
            .pointerInput(currentMode) {
                detectTapGestures {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val next = modes[(activeIdx + 1) % modes.size]
                    onModeSelected(next)
                }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.46f

        for (i in 0 until 36) {
            val rad = Math.toRadians(i * 10.0)
            drawLine(
                color = Color(0xFF4A4A4A),
                start = Offset(center.x + ((radius - 4.dp.toPx()) * cos(rad)).toFloat(), center.y + ((radius - 4.dp.toPx()) * sin(rad)).toFloat()),
                end = Offset(center.x + ((radius + 2.dp.toPx()) * cos(rad)).toFloat(), center.y + ((radius + 2.dp.toPx()) * sin(rad)).toFloat()),
                strokeWidth = 2.dp.toPx()
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF2B2B2B), Color(0xFF141414)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.15f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        drawCircle(
            color = AmberAccent,
            radius = 2.5.dp.toPx(),
            center = Offset(center.x - radius - 5.dp.toPx(), center.y)
        )

        val rotationOffset = 180f - (activeIdx * (360f / modes.size))
        rotate(degrees = rotationOffset, pivot = center) {
            modes.forEachIndexed { idx, mode ->
                val angleDeg = idx * (360f / modes.size)
                val rad = Math.toRadians(angleDeg.toDouble())
                val labelRadius = radius * 0.66f
                val pos = Offset(
                    center.x + (labelRadius * cos(rad)).toFloat(),
                    center.y + (labelRadius * sin(rad)).toFloat()
                )
                val isSelected = mode == currentMode
                val layoutResult = textMeasurer.measure(
                    text = mode.label,
                    style = TextStyle(
                        color = if (isSelected) AmberAccent else Color(0xFFD0D0D0),
                        fontSize = if (mode.label.length > 1) 8.sp else 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                rotate(degrees = -rotationOffset, pivot = pos) {
                    drawText(
                        textLayoutResult = layoutResult,
                        topLeft = Offset(
                            pos.x - layoutResult.size.width / 2f,
                            pos.y - layoutResult.size.height / 2f
                        )
                    )
                }
            }
        }
    }
}
