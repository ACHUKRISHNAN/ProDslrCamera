package com.pro.dslrcamera.ui.components

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TactileJogWheel(
    onStep: (delta: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    var wheelAngleDeg by remember { mutableFloatStateOf(0f) }
    var lastTouchAngle by remember { mutableFloatStateOf(0f) }
    var notchAccumulator by remember { mutableFloatStateOf(0f) }
    val degreesPerNotch = 12f

    fun triggerDetentHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    Canvas(
        modifier = modifier
            .size(144.dp)
            .pointerInput(Unit) {
                val center = Offset(size.width / 2f, size.height / 2f)
                detectDragGestures(
                    onDragStart = { startOffset ->
                        lastTouchAngle = Math.toDegrees(
                            atan2((startOffset.y - center.y).toDouble(), (startOffset.x - center.x).toDouble())
                        ).toFloat()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pos = change.position
                        val currentAngle = Math.toDegrees(
                            atan2((pos.y - center.y).toDouble(), (pos.x - center.x).toDouble())
                        ).toFloat()

                        var delta = currentAngle - lastTouchAngle
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f

                        lastTouchAngle = currentAngle
                        wheelAngleDeg = (wheelAngleDeg + delta) % 360f
                        notchAccumulator += delta

                        val notches = (notchAccumulator / degreesPerNotch).toInt()
                        if (notches != 0) {
                            notchAccumulator -= notches * degreesPerNotch
                            triggerDetentHaptic()
                            onStep(notches)
                        }
                    }
                )
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension * 0.41f
        val innerRadius = outerRadius * 0.83f

        val guideRadius = outerRadius + 11.dp.toPx()
        val guideRect = Size(guideRadius * 2f, guideRadius * 2f)
        val guideTopLeft = Offset(center.x - guideRadius, center.y - guideRadius)

        drawArc(
            color = AmberAccent.copy(alpha = 0.85f),
            startAngle = 175f,
            sweepAngle = 36f,
            useCenter = false,
            topLeft = guideTopLeft,
            size = guideRect,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = AmberAccent.copy(alpha = 0.85f),
            startAngle = 246f,
            sweepAngle = 42f,
            useCenter = false,
            topLeft = guideTopLeft,
            size = guideRect,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )

        rotate(degrees = wheelAngleDeg, pivot = center) {
            drawCircle(
                color = Color(0xFF2B2B2B),
                radius = outerRadius,
                center = center
            )

            val ridges = 64
            for (i in 0 until ridges) {
                val rad = Math.toRadians((i * (360.0 / ridges)))
                val p1 = Offset(
                    center.x + (innerRadius * cos(rad)).toFloat(),
                    center.y + (innerRadius * sin(rad)).toFloat()
                )
                val p2 = Offset(
                    center.x + (outerRadius * cos(rad)).toFloat(),
                    center.y + (outerRadius * sin(rad)).toFloat()
                )
                drawLine(
                    color = if (i % 2 == 0) Color(0xFF8C8C8C) else Color(0xFF1F1F1F),
                    start = p1,
                    end = p2,
                    strokeWidth = 1.8.dp.toPx()
                )
            }

            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF545454),
                        Color(0xFFB5B5B5),
                        Color(0xFF474747),
                        Color(0xFF9E9E9E),
                        Color(0xFF3A3A3A),
                        Color(0xFF545454)
                    ),
                    center = center
                ),
                radius = innerRadius,
                center = center
            )

            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = innerRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = innerRadius * 0.55f,
                center = center,
                style = Stroke(width = 0.8.dp.toPx())
            )
        }
    }
}
