package com.example.ui.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.ui.theme.ArcAmberBright
import com.example.ui.theme.ReactorCyanGlow
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Reusable J.A.R.V.I.S. Sci-Fi Tactical Scan Line Overlay Component.
 *
 * Uses Jetpack Compose [Canvas] and [Animatable] coroutines to render a futuristic
 * Stark / Age-of-Ultron HUD overlay featuring:
 * - Animated vertical & horizontal laser tactical scan lines with phosphor wake gradients
 * - Dynamic scan-intersection lock reticle
 * - 360° rotating polar radar sweep & concentric range-finder rings
 * - Chamfered octagonal HUD border, glowing corner circuitry brackets, and vernier azimuth ticks
 *
 * Designed to sit on top of the Map view (or any tactical feed) without intercepting pointer gestures.
 */
@Composable
fun SciFiTacticalScanOverlay(
    modifier: Modifier = Modifier,
    accentColor: Color = ReactorCyanGlow,
    secondaryColor: Color = ArcAmberBright,
    isSatelliteMode: Boolean = true,
    scanDurationMillis: Int = 3400,
    showRadarSweep: Boolean = true,
    showCornerBrackets: Boolean = true,
    showGridMatrix: Boolean = true
) {
    // Core Animatable instances driving the Sci-Fi tactical scan line & HUD telemetry effects
    val verticalScanAnim = remember { Animatable(0f) }
    val horizontalScanAnim = remember { Animatable(0f) }
    val radarSweepAnim = remember { Animatable(0f) }
    val hudPulseAnim = remember { Animatable(0.6f) }

    LaunchedEffect(scanDurationMillis) {
        // 1. Primary Vertical Tactical Scan Line (0f -> 1f -> 0f)
        launch {
            verticalScanAnim.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = scanDurationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
        // 2. Orthogonal Horizontal Telemetry Scan Line (0f -> 1f)
        launch {
            horizontalScanAnim.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = (scanDurationMillis * 1.45f).toInt(), easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        // 3. 360-Degree Polar Radar Sector Sweep
        launch {
            radarSweepAnim.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        // 4. Holographic Corner Circuitry & Reticle Pulse
        launch {
            hudPulseAnim.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
    }

    val verticalScanProgress = verticalScanAnim.value
    val horizontalScanProgress = horizontalScanAnim.value
    val radarSweepAngle = radarSweepAnim.value
    val hudPulse = hudPulseAnim.value

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val center = Offset(w / 2f, h / 2f)
        val maxRadarRadius = min(w, h) * 0.36f

        // 1. Tactical Holographic Grid Matrix
        if (showGridMatrix) {
            drawTacticalGridMatrix(
                width = w,
                height = h,
                accentColor = accentColor,
                isSatelliteMode = isSatelliteMode
            )
        }

        // 2. Primary Vertical Tactical Scan Line + Trailing Phosphor Wake
        drawTacticalScanLines(
            width = w,
            height = h,
            verticalProgress = verticalScanProgress,
            horizontalProgress = horizontalScanProgress,
            hudPulse = hudPulse,
            accentColor = accentColor,
            secondaryColor = secondaryColor
        )

        // 3. Concentric Polar Radar Range Rings & Rotating Sweep
        if (showRadarSweep) {
            drawPolarRadarSweep(
                center = center,
                maxRadius = maxRadarRadius,
                sweepAngle = radarSweepAngle,
                accentColor = accentColor,
                secondaryColor = secondaryColor
            )
        }

        // 4. Perimeter Vernier Azimuth Ruler Ticks
        drawPerimeterVernierTicks(
            width = w,
            height = h,
            scanY = h * verticalScanProgress,
            accentColor = accentColor,
            secondaryColor = secondaryColor
        )

        // 5. Chamfered Octagonal Sci-Fi Frame & Corner Circuitry Brackets
        if (showCornerBrackets) {
            drawJarvisChamferedFrameAndBrackets(
                width = w,
                height = h,
                hudPulse = hudPulse,
                accentColor = accentColor,
                secondaryColor = secondaryColor
            )
        }
    }
}

private fun DrawScope.drawTacticalGridMatrix(
    width: Float,
    height: Float,
    accentColor: Color,
    isSatelliteMode: Boolean
) {
    val gridCols = 8
    val gridRows = 5
    val gridColor = accentColor.copy(alpha = if (isSatelliteMode) 0.11f else 0.08f)

    for (c in 1 until gridCols) {
        val x = width * (c.toFloat() / gridCols)
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f
        )
    }
    for (r in 1 until gridRows) {
        val y = height * (r.toFloat() / gridRows)
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawTacticalScanLines(
    width: Float,
    height: Float,
    verticalProgress: Float,
    horizontalProgress: Float,
    hudPulse: Float,
    accentColor: Color,
    secondaryColor: Color
) {
    val scanY = height * verticalProgress
    val bandHeight = height * 0.16f
    val topBandY = (scanY - bandHeight).coerceAtLeast(0f)
    val bottomBandY = (scanY + bandHeight * 0.35f).coerceAtMost(height)

    // Glowing phosphor wake behind the primary scan line
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                accentColor.copy(alpha = 0.16f * hudPulse),
                secondaryColor.copy(alpha = 0.24f * hudPulse),
                Color.Transparent
            ),
            startY = topBandY,
            endY = bottomBandY
        ),
        topLeft = Offset(0f, topBandY),
        size = Size(width, (bottomBandY - topBandY).coerceAtLeast(1f))
    )

    // Primary crisp horizontal laser scan beam
    drawLine(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                accentColor.copy(alpha = 0.78f),
                secondaryColor.copy(alpha = 0.95f),
                accentColor.copy(alpha = 0.78f),
                Color.Transparent
            )
        ),
        start = Offset(0f, scanY),
        end = Offset(width, scanY),
        strokeWidth = 2.4f
    )

    // Micro-ticks riding along the active scan line
    val tickSegments = 16
    for (i in 1 until tickSegments) {
        val tx = width * (i.toFloat() / tickSegments)
        val tickH = if (i % 4 == 0) 6f else 3f
        drawLine(
            color = secondaryColor.copy(alpha = 0.68f),
            start = Offset(tx, (scanY - tickH).coerceAtLeast(0f)),
            end = Offset(tx, (scanY + tickH).coerceAtMost(height)),
            strokeWidth = 1.2f
        )
    }

    // Secondary orthogonal vertical scan line
    val scanX = width * horizontalProgress
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                accentColor.copy(alpha = 0.42f),
                secondaryColor.copy(alpha = 0.58f),
                Color.Transparent
            )
        ),
        start = Offset(scanX, 0f),
        end = Offset(scanX, height),
        strokeWidth = 1.4f
    )

    // Dynamic target-lock reticle at the intersection of vertical & horizontal scan lines
    val lockPoint = Offset(scanX, scanY)
    drawCircle(
        color = secondaryColor.copy(alpha = 0.90f),
        radius = 4.2f,
        center = lockPoint
    )
    drawCircle(
        color = accentColor.copy(alpha = 0.55f * hudPulse),
        radius = 11.5f,
        center = lockPoint,
        style = Stroke(width = 1.6f)
    )
}

private fun DrawScope.drawPolarRadarSweep(
    center: Offset,
    maxRadius: Float,
    sweepAngle: Float,
    accentColor: Color,
    secondaryColor: Color
) {
    val ringFractions = listOf(0.38f, 0.68f, 1.0f)
    ringFractions.forEachIndexed { idx, frac ->
        drawCircle(
            color = if (idx == 1) secondaryColor.copy(alpha = 0.22f) else accentColor.copy(alpha = 0.20f),
            radius = maxRadius * frac,
            center = center,
            style = Stroke(width = if (idx == 2) 1.6f else 1.1f)
        )
    }

    // Crosshair axes through radar center
    val crossLen = maxRadius * 1.08f
    drawLine(
        color = accentColor.copy(alpha = 0.18f),
        start = Offset(center.x - crossLen, center.y),
        end = Offset(center.x + crossLen, center.y),
        strokeWidth = 1f
    )
    drawLine(
        color = accentColor.copy(alpha = 0.18f),
        start = Offset(center.x, center.y - crossLen),
        end = Offset(center.x, center.y + crossLen),
        strokeWidth = 1f
    )

    // Rotating 360-degree radar sweep cone
    rotate(degrees = sweepAngle, pivot = center) {
        drawArc(
            brush = Brush.sweepGradient(
                0.0f to Color.Transparent,
                0.78f to Color.Transparent,
                0.92f to accentColor.copy(alpha = 0.14f),
                1.0f to secondaryColor.copy(alpha = 0.34f),
                center = center
            ),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = true,
            topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
            size = Size(maxRadius * 2f, maxRadius * 2f)
        )
        drawLine(
            color = secondaryColor.copy(alpha = 0.80f),
            start = center,
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawPerimeterVernierTicks(
    width: Float,
    height: Float,
    scanY: Float,
    accentColor: Color,
    secondaryColor: Color
) {
    val topTicks = 28
    for (i in 1 until topTicks) {
        val tx = width * (i.toFloat() / topTicks)
        val isMajor = i % 4 == 0
        val tickLen = if (isMajor) 8f else 4f
        val tickColor = if (isMajor) secondaryColor.copy(alpha = 0.65f) else accentColor.copy(alpha = 0.38f)

        // Top ruler ticks (below top telemetry header)
        drawLine(
            color = tickColor,
            start = Offset(tx, 30f),
            end = Offset(tx, 30f + tickLen),
            strokeWidth = if (isMajor) 1.8f else 1f
        )
        // Bottom ruler ticks
        drawLine(
            color = tickColor,
            start = Offset(tx, height - 4f),
            end = Offset(tx, height - 4f - tickLen),
            strokeWidth = if (isMajor) 1.8f else 1f
        )
    }

    // Left & Right elevation ruler ticks that highlight near the active scan line
    val sideTicks = 18
    for (j in 2 until sideTicks - 1) {
        val ty = height * (j.toFloat() / sideTicks)
        val distToScan = kotlin.math.abs(ty - scanY)
        val isNearScan = distToScan < height * 0.08f
        val tickLen = if (isNearScan) 10f else 5f
        val tickColor = if (isNearScan) secondaryColor.copy(alpha = 0.90f) else accentColor.copy(alpha = 0.35f)

        drawLine(
            color = tickColor,
            start = Offset(6f, ty),
            end = Offset(6f + tickLen, ty),
            strokeWidth = if (isNearScan) 2.0f else 1.1f
        )
    }
}

private fun DrawScope.drawJarvisChamferedFrameAndBrackets(
    width: Float,
    height: Float,
    hudPulse: Float,
    accentColor: Color,
    secondaryColor: Color
) {
    val inset = 5f
    val chamfer = 18f
    val framePath = Path().apply {
        moveTo(inset + chamfer, inset)
        lineTo(width - inset - chamfer, inset)
        lineTo(width - inset, inset + chamfer)
        lineTo(width - inset, height - inset - chamfer)
        lineTo(width - inset - chamfer, height - inset)
        lineTo(inset + chamfer, height - inset)
        lineTo(inset, height - inset - chamfer)
        lineTo(inset, inset + chamfer)
        close()
    }
    drawPath(
        path = framePath,
        color = accentColor.copy(alpha = 0.48f * hudPulse),
        style = Stroke(width = 1.6f)
    )

    val bracketArm = 28f
    val cornerStroke = 3.0f
    val cornerColor = secondaryColor.copy(alpha = 0.88f * hudPulse)

    // Top-Left Bracket
    val tlPath = Path().apply {
        moveTo(inset, inset + chamfer + bracketArm)
        lineTo(inset, inset + chamfer)
        lineTo(inset + chamfer, inset)
        lineTo(inset + chamfer + bracketArm, inset)
    }
    drawPath(tlPath, color = cornerColor, style = Stroke(width = cornerStroke, cap = StrokeCap.Round))

    // Top-Right Bracket
    val trPath = Path().apply {
        moveTo(width - inset - chamfer - bracketArm, inset)
        lineTo(width - inset - chamfer, inset)
        lineTo(width - inset, inset + chamfer)
        lineTo(width - inset, inset + chamfer + bracketArm)
    }
    drawPath(trPath, color = cornerColor, style = Stroke(width = cornerStroke, cap = StrokeCap.Round))

    // Bottom-Left Bracket
    val blPath = Path().apply {
        moveTo(inset, height - inset - chamfer - bracketArm)
        lineTo(inset, height - inset - chamfer)
        lineTo(inset + chamfer, height - inset)
        lineTo(inset + chamfer + bracketArm, height - inset)
    }
    drawPath(blPath, color = cornerColor, style = Stroke(width = cornerStroke, cap = StrokeCap.Round))

    // Bottom-Right Bracket
    val brPath = Path().apply {
        moveTo(width - inset - chamfer - bracketArm, height - inset)
        lineTo(width - inset - chamfer, height - inset)
        lineTo(width - inset, height - inset - chamfer)
        lineTo(width - inset, height - inset - chamfer - bracketArm)
    }
    drawPath(brPath, color = cornerColor, style = Stroke(width = cornerStroke, cap = StrokeCap.Round))
}
