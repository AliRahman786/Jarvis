package com.example.ui.hud

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ArcAmberBright
import com.example.ui.theme.ArcFlameOrange
import com.example.ui.theme.ProcessingViolet
import com.example.ui.theme.ReactorCyanGlow
import com.example.ui.theme.SuccessEmerald
import com.example.voice.VoiceState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private data class MindCoreNode3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val layerScale: Float,
    val isHubNode: Boolean
)

private data class ProjectedNode(
    val screenX: Float,
    val screenY: Float,
    val zDepth: Float,
    val scale: Float,
    val isHub: Boolean,
    val layerScale: Float
)

/**
 * Stark Mark-LIV 3D Triangular Arc Reactor & Holographic Pedestal Core.
 * Preserves all 3D Fibonacci neural nodes, voice state colors, audio spectrum reactivity,
 * and interactive pan/zoom/tap gestures while featuring the signature glowing triangular
 * Arc Reactor core and multi-ring holographic emitter base from the HUD design.
 */
@Composable
fun JarvisCoreView(
    voiceState: VoiceState,
    audioAmplitude: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onCoreTap: (() -> Unit)? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var rotationOffset by remember { mutableFloatStateOf(0f) }
    var tiltPitchOffset by remember { mutableFloatStateOf(0.25f) }

    val mindLatticeNodes = remember {
        val list = ArrayList<MindCoreNode3D>(48)
        val goldenRatio = (1f + sqrt(5f)) / 2f
        val outerCount = 32
        for (i in 0 until outerCount) {
            val theta = 2f * PI.toFloat() * i / goldenRatio
            val phi = kotlin.math.acos(1f - 2f * (i + 0.5f) / outerCount)
            list.add(
                MindCoreNode3D(
                    x = sin(phi) * cos(theta),
                    y = sin(phi) * sin(theta),
                    z = cos(phi),
                    layerScale = 1.0f,
                    isHubNode = (i % 4 == 0)
                )
            )
        }
        val innerCount = 16
        for (i in 0 until innerCount) {
            val theta = 2f * PI.toFloat() * i / goldenRatio + 0.9f
            val phi = kotlin.math.acos(1f - 2f * (i + 0.5f) / innerCount)
            list.add(
                MindCoreNode3D(
                    x = sin(phi) * cos(theta),
                    y = sin(phi) * sin(theta),
                    z = cos(phi),
                    layerScale = 0.65f,
                    isHubNode = (i % 3 == 0)
                )
            )
        }
        list
    }

    val mindSynapseEdges = remember(mindLatticeNodes) {
        val edges = ArrayList<Pair<Int, Int>>(110)
        for (i in mindLatticeNodes.indices) {
            val a = mindLatticeNodes[i]
            val ax = a.x * a.layerScale
            val ay = a.y * a.layerScale
            val az = a.z * a.layerScale
            for (j in i + 1 until mindLatticeNodes.size) {
                val b = mindLatticeNodes[j]
                val bx = b.x * b.layerScale
                val by = b.y * b.layerScale
                val bz = b.z * b.layerScale
                val dx = ax - bx
                val dy = ay - by
                val dz = az - bz
                val distSq = dx * dx + dy * dy + dz * dz
                if (distSq in 0.08f..0.40f) {
                    edges.add(i to j)
                }
            }
        }
        edges
    }

    val infiniteTransition = rememberInfiniteTransition(label = "stark_arc_reactor_core")

    val primaryRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    VoiceState.THINKING, VoiceState.PROCESSING -> 4200
                    VoiceState.SPEAKING -> 6400
                    VoiceState.LISTENING -> 8000
                    VoiceState.ERROR -> 3000
                    VoiceState.IDLE -> 15000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "primary_rot"
    )

    val secondaryRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    VoiceState.THINKING, VoiceState.PROCESSING -> 3400
                    VoiceState.SPEAKING -> 5200
                    else -> 11000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondary_rot"
    )

    val synapticWavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "synaptic_wave"
    )

    val coreBreath by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    VoiceState.SPEAKING -> 460
                    VoiceState.THINKING, VoiceState.PROCESSING -> 600
                    VoiceState.LISTENING -> 820
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_breath"
    )

    val smoothedAmplitude by animateFloatAsState(
        targetValue = audioAmplitude.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 65),
        label = "audio_amp"
    )

    val cyanElectric = Color(0xFF00E5FF)
    val deepBlueGlow = Color(0xFF0077FF)
    val goldAccent = ArcAmberBright
    val warmOrange = Color(0xFFFF8C00)

    val primaryCoreColor = when (voiceState) {
        VoiceState.IDLE -> cyanElectric
        VoiceState.LISTENING -> SuccessEmerald
        VoiceState.THINKING, VoiceState.PROCESSING -> goldAccent
        VoiceState.SPEAKING -> cyanElectric
        VoiceState.ERROR -> AlertCrimson
    }

    val secondaryHologramColor = when (voiceState) {
        VoiceState.IDLE -> goldAccent
        VoiceState.LISTENING -> cyanElectric
        VoiceState.THINKING, VoiceState.PROCESSING -> ProcessingViolet
        VoiceState.SPEAKING -> goldAccent
        VoiceState.ERROR -> ArcFlameOrange
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("jarvis_3d_core")
            .semantics {
                contentDescription =
                    "JARVIS 3D Arc Reactor Mind Core in ${voiceState.name} state. Tap to speak or interrupt immediately."
            }
            .pointerInput(onCoreTap) {
                if (onCoreTap != null) {
                    detectTapGestures(onTap = { onCoreTap() })
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotate ->
                    scale = (scale * zoom).coerceIn(0.75f, 1.85f)
                    rotationOffset += rotate + (pan.x * 0.22f)
                    tiltPitchOffset = (tiltPitchOffset + pan.y * 0.004f).coerceIn(-0.85f, 0.85f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Position orb slightly above vertical center so the holographic pedestal base sits underneath
            val orbCenter = Offset(size.width / 2f, size.height * 0.43f)
            val baseRadius = (min(size.width, size.height * 0.82f) / 2f) * 0.78f * scale
            val dynamicPulse = 1f + (smoothedAmplitude * 0.20f)

            // 1. HOLOGRAPHIC PROJECTOR PEDESTAL BASE & VERTICAL ENERGY COLUMNS
            drawHolographicPedestalBase(
                orbCenter = orbCenter,
                baseRadius = baseRadius,
                canvasHeight = size.height,
                primaryColor = primaryCoreColor,
                goldColor = secondaryHologramColor,
                wavePhase = synapticWavePhase,
                breath = coreBreath
            )

            // 2. Deep Volumetric Holographic Aura behind the Reactor Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryCoreColor.copy(alpha = 0.42f * coreBreath),
                        deepBlueGlow.copy(alpha = 0.24f),
                        secondaryHologramColor.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = orbCenter,
                    radius = baseRadius * 1.38f * dynamicPulse
                ),
                radius = baseRadius * 1.38f * dynamicPulse,
                center = orbCenter
            )

            // 3. Wide Horizontal Orbital Ring with Glowing Particle Nodes (wraps around the sphere)
            drawHorizontalOrbitalRing(
                center = orbCenter,
                radiusX = baseRadius * 1.24f,
                radiusY = baseRadius * 0.30f,
                phase = synapticWavePhase,
                cyanColor = primaryCoreColor,
                goldColor = secondaryHologramColor
            )

            // 4. Outer Mechanical Turbine Ring (Segmented Gold/Bronze + Cyan Armour Ring)
            drawMechanicalTurbineRing(
                center = orbCenter,
                outerRadius = baseRadius * 0.98f,
                innerRadius = baseRadius * 0.80f,
                rotation = primaryRotation * 0.30f + rotationOffset,
                cyanColor = primaryCoreColor,
                goldColor = secondaryHologramColor,
                orangeColor = warmOrange
            )

            // 5. 48-Bar Polar Audio Spectrum Equalizer
            drawPolarAudioSpectrumRing(
                center = orbCenter,
                innerRadius = baseRadius * 0.78f,
                amplitude = smoothedAmplitude,
                phase = synapticWavePhase,
                state = voiceState,
                primaryColor = primaryCoreColor,
                secondaryColor = secondaryHologramColor
            )

            // 6. Counter-Rotating Segmented Telemetry Astrolabe Arcs
            rotate(degrees = secondaryRotation + rotationOffset, pivot = orbCenter) {
                val ringRadius = baseRadius * 0.72f
                val arcSpan = listOf(46f, 24f, 52f, 22f, 44f, 28f)
                var angleCursor = 0f
                arcSpan.forEachIndexed { idx, sweep ->
                    drawArc(
                        color = if (idx % 2 == 0) {
                            secondaryHologramColor.copy(alpha = 0.85f)
                        } else {
                            primaryCoreColor.copy(alpha = 0.80f)
                        },
                        startAngle = angleCursor,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(orbCenter.x - ringRadius, orbCenter.y - ringRadius),
                        size = Size(ringRadius * 2f, ringRadius * 2f),
                        style = Stroke(
                            width = if (idx % 2 == 0) 5.0f else 2.6f,
                            cap = StrokeCap.Round
                        )
                    )
                    angleCursor += sweep + 24f
                }
            }

            // 7. 3D Volumetric Fibonacci Neural Lattice
            val yawRad = ((primaryRotation * 0.85f + rotationOffset) * PI / 180.0).toFloat()
            val pitchRad = tiltPitchOffset + 0.22f * sin(synapticWavePhase * 0.5f)
            drawAgeOfUltronMindLattice3D(
                center = orbCenter,
                sphereRadius = baseRadius * 0.56f * coreBreath * (1f + smoothedAmplitude * 0.14f),
                nodes = mindLatticeNodes,
                edges = mindSynapseEdges,
                yawRad = yawRad,
                pitchRad = pitchRad,
                wavePhase = synapticWavePhase,
                amplitude = smoothedAmplitude,
                primaryColor = primaryCoreColor,
                secondaryColor = secondaryHologramColor
            )

            // 8. ICONIC STARK MARK-LIV TRIANGULAR ARC REACTOR CORE (Centerpiece)
            drawTriangularArcReactorCore(
                center = orbCenter,
                coreRadius = baseRadius * 0.50f * coreBreath * (1f + smoothedAmplitude * 0.12f),
                cyanColor = primaryCoreColor,
                goldColor = secondaryHologramColor
            )

            // 9. State-Specific Holographic Overlay
            drawStateSpecificHoloprojector(
                center = orbCenter,
                baseRadius = baseRadius,
                state = voiceState,
                amplitude = smoothedAmplitude,
                primaryRotation = primaryRotation,
                wavePhase = synapticWavePhase,
                primaryColor = primaryCoreColor,
                secondaryColor = secondaryHologramColor
            )
        }
    }
}

private fun DrawScope.drawHolographicPedestalBase(
    orbCenter: Offset,
    baseRadius: Float,
    canvasHeight: Float,
    primaryColor: Color,
    goldColor: Color,
    wavePhase: Float,
    breath: Float
) {
    val pedestalY = (orbCenter.y + baseRadius * 1.18f).coerceAtMost(canvasHeight * 0.89f)
    val pedestalWidth = baseRadius * 1.95f
    val pedestalHeight = baseRadius * 0.36f

    // Vertical holographic light beam column from pedestal up into the core
    val beamWidth = baseRadius * 0.54f
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.05f),
                primaryColor.copy(alpha = 0.36f * breath),
                Color.White.copy(alpha = 0.55f * breath),
                primaryColor.copy(alpha = 0.12f)
            ),
            startY = orbCenter.y + baseRadius * 0.25f,
            endY = pedestalY
        ),
        topLeft = Offset(orbCenter.x - beamWidth / 2f, orbCenter.y + baseRadius * 0.25f),
        size = Size(beamWidth, (pedestalY - (orbCenter.y + baseRadius * 0.25f)).coerceAtLeast(8f))
    )

    // Vertical laser filaments inside the beam
    val lineCount = 9
    for (i in 0 until lineCount) {
        val frac = (i.toFloat() / (lineCount - 1)) - 0.5f
        val xPos = orbCenter.x + frac * beamWidth * 0.9f
        val alpha = (1f - abs(frac) * 1.6f).coerceIn(0.15f, 0.85f)
        drawLine(
            color = if (i % 2 == 0) Color.White.copy(alpha = alpha * 0.7f) else primaryColor.copy(alpha = alpha),
            start = Offset(xPos, orbCenter.y + baseRadius * 0.42f),
            end = Offset(xPos, pedestalY),
            strokeWidth = if (i == lineCount / 2) 2.6f else 1.2f
        )
    }

    // Concentric 3D perspective emitter ellipses on the pedestal platform
    val rings = listOf(1.0f, 0.82f, 0.64f, 0.45f, 0.26f)
    rings.forEachIndexed { idx, factor ->
        val w = pedestalWidth * factor
        val h = pedestalHeight * factor
        val ringColor = if (idx % 2 == 0) primaryColor else goldColor
        drawOval(
            color = ringColor.copy(alpha = if (idx == 0) 0.55f else 0.78f),
            topLeft = Offset(orbCenter.x - w / 2f, pedestalY - h / 2f),
            size = Size(w, h),
            style = Stroke(width = if (idx == 1 || idx == 2) 3.2f else 1.8f)
        )
    }

    // Bright core emitter hotspot on pedestal center
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.9f),
                primaryColor.copy(alpha = 0.65f),
                Color.Transparent
            ),
            center = Offset(orbCenter.x, pedestalY),
            radius = pedestalWidth * 0.35f
        ),
        topLeft = Offset(orbCenter.x - pedestalWidth * 0.32f, pedestalY - pedestalHeight * 0.32f),
        size = Size(pedestalWidth * 0.64f, pedestalHeight * 0.64f)
    )
}

private fun DrawScope.drawHorizontalOrbitalRing(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    phase: Float,
    cyanColor: Color,
    goldColor: Color
) {
    drawOval(
        color = cyanColor.copy(alpha = 0.55f),
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f),
        style = Stroke(width = 2.0f)
    )
    drawOval(
        color = goldColor.copy(alpha = 0.28f),
        topLeft = Offset(center.x - radiusX * 1.04f, center.y - radiusY * 1.12f),
        size = Size(radiusX * 2.08f, radiusY * 2.24f),
        style = Stroke(width = 1.1f)
    )

    // Orbiting bright energy sparkles along the ellipse
    for (i in 0 until 6) {
        val angle = phase + i * (PI.toFloat() / 3f)
        val px = center.x + radiusX * cos(angle)
        val py = center.y + radiusY * sin(angle)
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = 3.2f,
            center = Offset(px, py)
        )
        drawCircle(
            color = cyanColor.copy(alpha = 0.45f),
            radius = 7.5f,
            center = Offset(px, py)
        )
    }
}

private fun DrawScope.drawMechanicalTurbineRing(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    rotation: Float,
    cyanColor: Color,
    goldColor: Color,
    orangeColor: Color
) {
    // Dark metallic backing ring
    drawCircle(
        color = Color(0xFF050D1A).copy(alpha = 0.78f),
        radius = (outerRadius + innerRadius) * 0.5f,
        center = center,
        style = Stroke(width = outerRadius - innerRadius)
    )

    // Outer & inner glowing rims
    drawCircle(
        color = cyanColor.copy(alpha = 0.85f),
        radius = outerRadius,
        center = center,
        style = Stroke(width = 2.5f)
    )
    drawCircle(
        color = goldColor.copy(alpha = 0.75f),
        radius = innerRadius,
        center = center,
        style = Stroke(width = 2.2f)
    )

    // Segmented turbine ribs & glowing amber/cyan reactor cells
    rotate(degrees = rotation, pivot = center) {
        val segmentCount = 24
        val sweepPerSegment = 360f / segmentCount
        val midRadius = (outerRadius + innerRadius) * 0.5f
        val ringThickness = (outerRadius - innerRadius) * 0.68f

        for (i in 0 until segmentCount) {
            val startAngle = i * sweepPerSegment + 2f
            val isGoldCell = i % 3 == 0
            val cellColor = when {
                isGoldCell -> orangeColor.copy(alpha = 0.82f)
                i % 2 == 0 -> cyanColor.copy(alpha = 0.55f)
                else -> goldColor.copy(alpha = 0.45f)
            }
            drawArc(
                color = cellColor,
                startAngle = startAngle,
                sweepAngle = sweepPerSegment - 4.5f,
                useCenter = false,
                topLeft = Offset(center.x - midRadius, center.y - midRadius),
                size = Size(midRadius * 2f, midRadius * 2f),
                style = Stroke(width = ringThickness, cap = StrokeCap.Butt)
            )
        }
    }
}

private fun DrawScope.drawTriangularArcReactorCore(
    center: Offset,
    coreRadius: Float,
    cyanColor: Color,
    goldColor: Color
) {
    // Downward-pointing iconic Iron Man Mark-VI / Mark-LIV Arc Reactor Triangle
    val anglesDeg = listOf(-30f, 90f, 210f)
    val outerVertices = anglesDeg.map { deg ->
        val rad = (deg * PI / 180.0).toFloat()
        Offset(
            x = center.x + coreRadius * cos(rad),
            y = center.y + coreRadius * sin(rad)
        )
    }

    val innerVertices = anglesDeg.map { deg ->
        val rad = (deg * PI / 180.0).toFloat()
        Offset(
            x = center.x + coreRadius * 0.58f * cos(rad),
            y = center.y + coreRadius * 0.58f * sin(rad)
        )
    }

    val outerTriangle = Path().apply {
        moveTo(outerVertices[0].x, outerVertices[0].y)
        lineTo(outerVertices[1].x, outerVertices[1].y)
        lineTo(outerVertices[2].x, outerVertices[2].y)
        close()
    }

    val innerTriangle = Path().apply {
        moveTo(innerVertices[0].x, innerVertices[0].y)
        lineTo(innerVertices[1].x, innerVertices[1].y)
        lineTo(innerVertices[2].x, innerVertices[2].y)
        close()
    }

    // Intense cyan-white radial core glow behind triangle
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f),
                cyanColor.copy(alpha = 0.85f),
                cyanColor.copy(alpha = 0.25f),
                Color.Transparent
            ),
            center = center,
            radius = coreRadius * 1.15f
        ),
        radius = coreRadius * 1.15f,
        center = center
    )

    // Fill outer triangle with glowing cyan gradient
    drawPath(
        path = outerTriangle,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                cyanColor.copy(alpha = 0.92f),
                Color(0xFF0055AA).copy(alpha = 0.85f)
            ),
            center = center,
            radius = coreRadius
        )
    )

    // Outer triangle metallic cyan + white border
    drawPath(
        path = outerTriangle,
        color = cyanColor,
        style = Stroke(width = 5.5f, join = StrokeJoin.Round)
    )
    drawPath(
        path = outerTriangle,
        color = Color.White.copy(alpha = 0.92f),
        style = Stroke(width = 2.2f, join = StrokeJoin.Round)
    )

    // Inner brilliant white-cyan triangle
    drawPath(
        path = innerTriangle,
        color = Color.White.copy(alpha = 0.95f)
    )
    drawPath(
        path = innerTriangle,
        color = cyanColor,
        style = Stroke(width = 2.5f, join = StrokeJoin.Round)
    )

    // Internal lattice struts connecting outer & inner triangle vertices
    for (i in 0..2) {
        drawLine(
            color = goldColor.copy(alpha = 0.85f),
            start = outerVertices[i],
            end = innerVertices[i],
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawPolarAudioSpectrumRing(
    center: Offset,
    innerRadius: Float,
    amplitude: Float,
    phase: Float,
    state: VoiceState,
    primaryColor: Color,
    secondaryColor: Color
) {
    val barCount = 48
    val isActiveVoice = state == VoiceState.SPEAKING || state == VoiceState.LISTENING || amplitude > 0.05f
    for (i in 0 until barCount) {
        val angleDeg = i * (360f / barCount)
        val rad = (angleDeg * PI / 180.0).toFloat()
        val harmonic = abs(sin(phase * 2f + i * 0.45f)) * 0.55f +
            abs(cos(phase * 1.3f - i * 0.25f)) * 0.45f
        val dynamicBoost = if (isActiveVoice) {
            (0.18f + amplitude * 0.95f) * harmonic
        } else {
            0.08f * harmonic
        }
        val barLength = innerRadius * (0.03f + dynamicBoost * 0.16f)
        val rStart = innerRadius
        val rEnd = innerRadius + barLength

        val barColor = if (i % 3 == 0) {
            secondaryColor.copy(alpha = (0.40f + dynamicBoost * 0.55f).coerceIn(0.25f, 0.95f))
        } else {
            primaryColor.copy(alpha = (0.45f + dynamicBoost * 0.50f).coerceIn(0.30f, 0.98f))
        }

        drawLine(
            color = barColor,
            start = Offset(center.x + rStart * cos(rad), center.y + rStart * sin(rad)),
            end = Offset(center.x + rEnd * cos(rad), center.y + rEnd * sin(rad)),
            strokeWidth = if (isActiveVoice) 3.2f else 2.0f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawAgeOfUltronMindLattice3D(
    center: Offset,
    sphereRadius: Float,
    nodes: List<MindCoreNode3D>,
    edges: List<Pair<Int, Int>>,
    yawRad: Float,
    pitchRad: Float,
    wavePhase: Float,
    amplitude: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    val cosYaw = cos(yawRad)
    val sinYaw = sin(yawRad)
    val cosPitch = cos(pitchRad)
    val sinPitch = sin(pitchRad)

    val projected = ArrayList<ProjectedNode>(nodes.size)
    for (i in nodes.indices) {
        val n = nodes[i]
        val pulse = 1f + 0.05f * sin(wavePhase * 1.5f + i * 0.37f) + (amplitude * 0.08f)
        val r = n.layerScale * pulse

        val x0 = n.x * r
        val y0 = n.y * r
        val z0 = n.z * r

        val x1 = x0 * cosYaw + z0 * sinYaw
        val z1 = -x0 * sinYaw + z0 * cosYaw

        val y2 = y0 * cosPitch - z1 * sinPitch
        val z2 = y0 * sinPitch + z1 * cosPitch

        val perspective = 2.6f / (2.6f - z2.coerceIn(-1.2f, 1.2f))
        val sx = center.x + x1 * sphereRadius * perspective
        val sy = center.y + y2 * sphereRadius * perspective

        projected.add(
            ProjectedNode(
                screenX = sx,
                screenY = sy,
                zDepth = z2,
                scale = perspective,
                isHub = n.isHubNode,
                layerScale = n.layerScale
            )
        )
    }

    for (edgeIndex in edges.indices) {
        val (idxA, idxB) = edges[edgeIndex]
        val a = projected[idxA]
        val b = projected[idxB]
        val avgZ = (a.zDepth + b.zDepth) * 0.5f

        val depthAlpha = ((avgZ + 1.15f) / 2.3f).coerceIn(0.10f, 0.92f)
        val isCrossShellBridge = a.layerScale != b.layerScale
        val pulseBoost = 0.5f + 0.5f * sin(wavePhase * 2.2f + edgeIndex * 0.31f)

        val filamentColor = if (isCrossShellBridge) {
            secondaryColor.copy(alpha = (depthAlpha * (0.28f + 0.35f * pulseBoost)).coerceIn(0.08f, 0.78f))
        } else {
            primaryColor.copy(alpha = (depthAlpha * (0.32f + 0.38f * pulseBoost)).coerceIn(0.10f, 0.85f))
        }

        drawLine(
            color = filamentColor,
            start = Offset(a.screenX, a.screenY),
            end = Offset(b.screenX, b.screenY),
            strokeWidth = if (isCrossShellBridge) 1.2f else 1.5f * ((avgZ + 1.5f) * 0.45f),
            cap = StrokeCap.Round
        )
    }

    for (i in projected.indices) {
        val p = projected[i]
        val depthFactor = ((p.zDepth + 1.15f) / 2.3f).coerceIn(0.18f, 1.0f)
        val nodeRadius = (if (p.isHub) 3.8f else 2.2f) * p.scale * (0.75f + 0.4f * depthFactor)
        val nodeColor = if (p.layerScale < 0.8f) {
            secondaryColor
        } else if (p.isHub) {
            Color.White
        } else {
            primaryColor
        }

        drawCircle(
            color = nodeColor.copy(alpha = (0.30f + 0.70f * depthFactor).coerceIn(0.2f, 1f)),
            radius = nodeRadius,
            center = Offset(p.screenX, p.screenY)
        )
    }
}

private fun DrawScope.drawStateSpecificHoloprojector(
    center: Offset,
    baseRadius: Float,
    state: VoiceState,
    amplitude: Float,
    primaryRotation: Float,
    wavePhase: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    when (state) {
        VoiceState.SPEAKING -> {
            val rippleCount = 3
            for (r in 1..rippleCount) {
                val expand = ((wavePhase / (2f * PI.toFloat())) + r * 0.33f) % 1f
                val rippleR = baseRadius * (0.35f + expand * 0.65f) * (1f + amplitude * 0.15f)
                val alpha = (1f - expand) * (0.35f + amplitude * 0.40f)
                drawCircle(
                    color = primaryColor.copy(alpha = alpha.coerceIn(0f, 0.75f)),
                    radius = rippleR,
                    center = center,
                    style = Stroke(width = 2.2f)
                )
            }
        }
        VoiceState.LISTENING -> {
            val sonarRadius = baseRadius * (0.50f + amplitude * 0.38f)
            drawCircle(
                color = SuccessEmerald.copy(alpha = 0.65f),
                radius = sonarRadius,
                center = center,
                style = Stroke(width = 2.8f)
            )
        }
        VoiceState.THINKING, VoiceState.PROCESSING -> {
            rotate(degrees = primaryRotation * 2.1f, pivot = center) {
                val orbitR = baseRadius * 0.68f
                for (k in 0 until 8) {
                    val rad = (k * 45.0 * PI / 180.0).toFloat()
                    val nodePos = Offset(
                        center.x + orbitR * cos(rad),
                        center.y + orbitR * sin(rad)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.92f),
                        radius = 4.2f,
                        center = nodePos
                    )
                }
            }
        }
        VoiceState.ERROR -> {
            val alertR = baseRadius * 0.92f
            drawCircle(
                color = AlertCrimson.copy(alpha = 0.75f),
                radius = alertR,
                center = center,
                style = Stroke(width = 3.5f)
            )
        }
        VoiceState.IDLE -> Unit
    }
}
