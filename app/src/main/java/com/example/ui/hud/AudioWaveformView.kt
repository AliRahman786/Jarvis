package com.example.ui.hud

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun AudioWaveformView(
    amplitude: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 28
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        val totalWidth = size.width
        val canvasHeight = size.height
        val barSpacing = totalWidth / barCount
        val barWidth = barSpacing * 0.55f
        val effectiveAmp = amplitude.coerceIn(0f, 1f)

        for (i in 0 until barCount) {
            // Wave computation
            val x = i * barSpacing + (barSpacing - barWidth) / 2f
            val normalizedI = i.toFloat() / barCount
            val waveHeightFactor = (sin(normalizedI * 3.14f * 2f + phase) * 0.4f + 0.6f)
            // Idle breathing minimum height + amplitude expansion
            val barHeight = ((canvasHeight * 0.15f) + (canvasHeight * 0.85f * effectiveAmp * waveHeightFactor.toFloat()))
                .coerceIn(3.dp.toPx(), canvasHeight)
            val y = (canvasHeight - barHeight) / 2f

            drawRoundRect(
                color = if (effectiveAmp > 0.1f) accentColor else accentColor.copy(alpha = 0.4f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}
