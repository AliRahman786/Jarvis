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

/**
 * Precomputed 3D node on a Fibonacci sphere for the *Avengers: Age of Ultron*
 * J.A.R.V.I.S. volumetric holographic Mind-Core lattice.
 */
private data class MindCoreNode3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val layerScale: Float, // 1.0f = outer cortex, 0.62f = inner synaptic shell
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
 * Movie-authentic *Avengers: Age of Ultron* (2015) J.A.R.V.I.S. 3D Holographic Mind-Core.
 *
 * Features:
 * 1. Dual-shell 3D Fibonacci Neural Cortex Lattice (68 3D nodes) with real-time 3D perspective
 *    projection, depth-sorted rendering, and glowing synaptic neural pathways.
 * 2. Authentic *Age of Ultron* dual-tone Holographic Gold/Amber (#FFE066 / #FF6D00) &
 *    Stark Quantum Cyan (#00E5FF) energy synthesis that shifts dynamically with [VoiceState].
 * 3. Three 3-axis tilted gyroscopic astrolabe rings with segmented telemetry arcs & data pips.
 * 4. 48-bar 360° Polar Audio Spectrum Equalizer around the perimeter reacting to [audioAmplitude]
 *    and full-duplex Barge-In voice interruption.
 * 5. Interactive 3D orbital rotation, pinch-to-zoom, and single-tap Barge-In / Mic trigger.
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

    // Precompute 3D Fibonacci sphere neural lattice (48 outer cortex nodes + 20 inner core nodes)
    val mindLatticeNodes = remember {
        val list = ArrayList<MindCoreNode3D>(68)
        val goldenRatio = (1f + sqrt(5f)) / 2f
        val outerCount = 48
        for (i in 0 until outerCount) {
            val theta = 2f * PI.toFloat() * i / goldenRatio
            val phi = kotlin.math.acos(1f - 2f * (i + 0.5f) / outerCount)
            val x = (sin(phi) * cos(theta))
            val y = (sin(phi) * sin(theta))
            val z = cos(phi)
            list.add(
                MindCoreNode3D(
                    x = x,
                    y = y,
                    z = z,
                    layerScale = 1.0f,
                    isHubNode = (i % 5 == 0)
                )
            )
        }
        val innerCount = 20
        for (i in 0 until innerCount) {
            val theta = 2f * PI.toFloat() * i / goldenRatio + 0.9f
            val phi = kotlin.math.acos(1f - 2f * (i + 0.5f) / innerCount)
            val x = (sin(phi) * cos(theta))
            val y = (sin(phi) * sin(theta))
            val z = cos(phi)
            list.add(
                MindCoreNode3D(
                    x = x,
                    y = y,
                    z = z,
                    layerScale = 0.62f,
                    isHubNode = (i % 4 == 0)
                )
            )
        }
        list
    }

    // Precompute synaptic edges between nearby 3D nodes
    val mindSynapseEdges = remember(mindLatticeNodes) {
        val edges = ArrayList<Pair<Int, Int>>(160)
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
                // Connect close neighbors within a shell and radial bridges between inner/outer cortex
                if (distSq in 0.08f..0.38f) {
                    edges.add(i to j)
                }
            }
        }
        edges
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ultron_jarvis_mind_core")

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
            animation = tween(durationMillis = 2800, easing = LinearEasing),
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
                    VoiceState.SPEAKING -> 480
                    VoiceState.THINKING, VoiceState.PROCESSING -> 620
                    VoiceState.LISTENING -> 850
                    else -> 2100
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

    // Age of Ultron J.A.R.V.I.S. Mind-Core Palette:
    // Combines Golden-Amber Synaptic Core with Tactical Cyan/State Accents
    val primaryCoreColor = when (voiceState) {
        VoiceState.IDLE -> ArcAmberBright
        VoiceState.LISTENING -> SuccessEmerald
        VoiceState.THINKING, VoiceState.PROCESSING -> ArcAmberBright
        VoiceState.SPEAKING -> ArcAmberBright
        VoiceState.ERROR -> AlertCrimson
    }

    val secondaryHologramColor = when (voiceState) {
        VoiceState.IDLE -> accentColor
        VoiceState.LISTENING -> ReactorCyanGlow
        VoiceState.THINKING, VoiceState.PROCESSING -> ProcessingViolet
        VoiceState.SPEAKING -> ReactorCyanGlow
        VoiceState.ERROR -> ArcFlameOrange
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("jarvis_3d_core")
            .semantics {
                contentDescription =
                    "JARVIS Age of Ultron 3D Mind Core in ${voiceState.name} state. Tap to speak or interrupt immediately."
            }
            .pointerInput(onCoreTap) {
                if (onCoreTap != null) {
                    detectTapGestures(
                        onTap = { onCoreTap() }
                    )
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
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (min(size.width, size.height) / 2f) * 0.82f * scale
            val dynamicPulse = 1f + (smoothedAmplitude * 0.22f)

            // 1. Deep Volumetric Holographic Aura (Gold + Cyan multi-stop radial field)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryCoreColor.copy(alpha = 0.36f * coreBreath),
                        secondaryHologramColor.copy(alpha = 0.18f),
                        primaryCoreColor.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.32f * dynamicPulse
                ),
                radius = baseRadius * 1.32f * dynamicPulse,
                center = center
            )

            // 2. Outer Degree Vernier Ring & Precision Compass Ticks
            drawUltronPerimeterRing(
                center = center,
                radius = baseRadius * 1.02f,
                rotation = primaryRotation * 0.35f + rotationOffset,
                primaryColor = primaryCoreColor,
                secondaryColor = secondaryHologramColor
            )

            // 3. 48-Bar 360° Polar Audio Spectrum Equalizer (Reacts to Voice & Barge-In)
            drawPolarAudioSpectrumRing(
                center = center,
                innerRadius = baseRadius * 0.86f,
                amplitude = smoothedAmplitude,
                phase = synapticWavePhase,
                state = voiceState,
                primaryColor = primaryCoreColor,
                secondaryColor = secondaryHologramColor
            )

            // 4. Counter-Rotating Segmented Telemetry Astrolabe Arcs
            rotate(degrees = secondaryRotation + rotationOffset, pivot = center) {
                val ringRadius = baseRadius * 0.80f
                val arcSpan = listOf(58f, 32f, 74f, 28f, 52f, 36f)
                var angleCursor = 0f
                arcSpan.forEachIndexed { idx, sweep ->
                    drawArc(
                        color = if (idx % 2 == 0) {
                            primaryCoreColor.copy(alpha = 0.72f)
                        } else {
                            secondaryHologramColor.copy(alpha = 0.65f)
                        },
                        startAngle = angleCursor,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                        size = Size(ringRadius * 2f, ringRadius * 2f),
                        style = Stroke(
                            width = if (idx % 2 == 0) 4.2f else 2.2f,
                            cap = StrokeCap.Round
                        )
                    )
                    angleCursor += sweep + 20f
                }
            }

            // 5. Three 3-Axis Perspective Tilted Gyroscopic Rings
            draw3DGyroscopicRings(
                center = center,
                radius = baseRadius * 0.72f * dynamicPulse,
                spinDeg = primaryRotation + rotationOffset,
                counterSpinDeg = secondaryRotation,
                primaryColor = primaryCoreColor,
                secondaryColor = secondaryHologramColor
            )

            // 6. 3D Volumetric Fibonacci Neural Lattice (Age of Ultron Mind-Core Sphere)
            val yawRad = ((primaryRotation * 0.85f + rotationOffset) * PI / 180.0).toFloat()
            val pitchRad = tiltPitchOffset + 0.22f * sin(synapticWavePhase * 0.5f)
            drawAgeOfUltronMindLattice3D(
                center = center,
                sphereRadius = baseRadius * 0.58f * coreBreath * (1f + smoothedAmplitude * 0.14f),
                nodes = mindLatticeNodes,
                edges = mindSynapseEdges,
                yawRad = yawRad,
                pitchRad = pitchRad,
                wavePhase = synapticWavePhase,
                amplitude = smoothedAmplitude,
                primaryColor = primaryCoreColor,
                secondaryColor = secondaryHologramColor
            )

            // 7. Inner Quantum Singularity Nucleus & Hexagonal Containment Matrix
            val nucleusRadius = baseRadius * 0.21f * coreBreath * (1f + smoothedAmplitude * 0.30f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.96f),
                        primaryCoreColor.copy(alpha = 0.88f),
                        secondaryHologramColor.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = nucleusRadius * 1.85f
                ),
                radius = nucleusRadius * 1.85f,
                center = center
            )

            // Rotating inner dual-geometry containment core
            rotate(degrees = -secondaryRotation * 1.4f, pivot = center) {
                val hexPath = createPolygonPath(center, nucleusRadius * 1.05f, sides = 6)
                drawPath(
                    path = hexPath,
                    color = Color.White.copy(alpha = 0.82f),
                    style = Stroke(width = 2.4f)
                )
            }
            rotate(degrees = primaryRotation * 1.8f, pivot = center) {
                val octPath = createPolygonPath(center, nucleusRadius * 0.72f, sides = 8)
                drawPath(
                    path = octPath,
                    color = primaryCoreColor.copy(alpha = 0.9f),
                    style = Stroke(width = 1.8f)
                )
            }

            // 8. State-Specific Holographic Telemetry Overlay (Listening Barge-In / Thinking / Speaking)
            drawStateSpecificHoloprojector(
                center = center,
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

private fun DrawScope.drawUltronPerimeterRing(
    center: Offset,
    radius: Float,
    rotation: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    rotate(degrees = rotation, pivot = center) {
        val tickCount = 72
        for (i in 0 until tickCount) {
            val angleDeg = i * (360f / tickCount)
            val rad = (angleDeg * PI / 180.0).toFloat()
            val isCardinal = i % 18 == 0
            val isMajor = i % 6 == 0
            val innerR = when {
                isCardinal -> radius * 0.90f
                isMajor -> radius * 0.94f
                else -> radius * 0.97f
            }
            val outerR = if (isCardinal) radius * 1.03f else radius
            val tickColor = when {
                isCardinal -> primaryColor.copy(alpha = 0.92f)
                isMajor -> secondaryColor.copy(alpha = 0.65f)
                else -> primaryColor.copy(alpha = 0.30f)
            }
            drawLine(
                color = tickColor,
                start = Offset(center.x + innerR * cos(rad), center.y + innerR * sin(rad)),
                end = Offset(center.x + outerR * cos(rad), center.y + outerR * sin(rad)),
                strokeWidth = if (isCardinal) 3.2f else if (isMajor) 2f else 1.1f,
                cap = StrokeCap.Round
            )
        }
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

private fun DrawScope.draw3DGyroscopicRings(
    center: Offset,
    radius: Float,
    spinDeg: Float,
    counterSpinDeg: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    // Ring 1: Tilted X-Y Astrolabe Ellipse (Gold/Amber)
    rotate(degrees = spinDeg * 0.7f, pivot = center) {
        val ovalWidth = radius * 2f
        val ovalHeight = radius * 0.76f
        drawOval(
            color = primaryColor.copy(alpha = 0.42f),
            topLeft = Offset(center.x - ovalWidth / 2f, center.y - ovalHeight / 2f),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = 2.0f)
        )
    }

    // Ring 2: Orthogonal Tilted Gimbal Ellipse (Cyan)
    rotate(degrees = -counterSpinDeg * 0.85f + 60f, pivot = center) {
        val ovalWidth = radius * 1.88f
        val ovalHeight = radius * 0.64f
        drawOval(
            color = secondaryColor.copy(alpha = 0.38f),
            topLeft = Offset(center.x - ovalWidth / 2f, center.y - ovalHeight / 2f),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = 1.8f)
        )
    }

    // Ring 3: High-Inclination Synaptic Orbit (Gold + Data Node Pips)
    rotate(degrees = spinDeg * 1.15f - 35f, pivot = center) {
        val ovalWidth = radius * 1.72f
        val ovalHeight = radius * 0.92f
        drawOval(
            color = primaryColor.copy(alpha = 0.30f),
            topLeft = Offset(center.x - ovalWidth / 2f, center.y - ovalHeight / 2f),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = 1.4f)
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

    // Project all 3D nodes into 2D canvas coordinates with perspective depth
    val projected = ArrayList<ProjectedNode>(nodes.size)
    for (i in nodes.indices) {
        val n = nodes[i]
        // Organic neural oscillation per node
        val pulse = 1f + 0.05f * sin(wavePhase * 1.5f + i * 0.37f) + (amplitude * 0.08f)
        val r = n.layerScale * pulse

        val x0 = n.x * r
        val y0 = n.y * r
        val z0 = n.z * r

        // Rotate around Y-axis (yaw)
        val x1 = x0 * cosYaw + z0 * sinYaw
        val z1 = -x0 * sinYaw + z0 * cosYaw

        // Rotate around X-axis (pitch)
        val y2 = y0 * cosPitch - z1 * sinPitch
        val z2 = y0 * sinPitch + z1 * cosPitch

        // Perspective factor (camera distance = 2.6)
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

    // Draw 3D synaptic filaments connecting nearby nodes
    for (edgeIndex in edges.indices) {
        val (idxA, idxB) = edges[edgeIndex]
        val a = projected[idxA]
        val b = projected[idxB]
        val avgZ = (a.zDepth + b.zDepth) * 0.5f

        // Depth-cull far back edges slightly for clean 3D volumetric clarity
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
            strokeWidth = if (isCrossShellBridge) 1.3f else 1.7f * ((avgZ + 1.5f) * 0.45f),
            cap = StrokeCap.Round
        )
    }

    // Draw 3D neural cortex nodes (front nodes brighter and larger than back-hemisphere nodes)
    for (i in projected.indices) {
        val p = projected[i]
        val depthFactor = ((p.zDepth + 1.15f) / 2.3f).coerceIn(0.18f, 1.0f)
        val nodeRadius = (if (p.isHub) 4.4f else 2.6f) * p.scale * (0.75f + 0.4f * depthFactor)
        val nodeColor = if (p.layerScale < 0.8f) {
            secondaryColor
        } else if (p.isHub) {
            Color.White
        } else {
            primaryColor
        }

        if (p.isHub && p.zDepth > -0.2f) {
            // Subtle holographic halo around foreground hub synapses
            drawCircle(
                color = primaryColor.copy(alpha = 0.28f * depthFactor),
                radius = nodeRadius * 2.4f,
                center = Offset(p.screenX, p.screenY)
            )
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
            // Full-Duplex Barge-In Ready Acoustic Ripples
            val rippleCount = 3
            for (r in 1..rippleCount) {
                val expand = ((wavePhase / (2f * PI.toFloat())) + r * 0.33f) % 1f
                val rippleR = baseRadius * (0.35f + expand * 0.65f) * (1f + amplitude * 0.15f)
                val alpha = (1f - expand) * (0.35f + amplitude * 0.40f)
                drawCircle(
                    color = secondaryColor.copy(alpha = alpha.coerceIn(0f, 0.75f)),
                    radius = rippleR,
                    center = center,
                    style = Stroke(width = 2.2f)
                )
            }
        }
        VoiceState.LISTENING -> {
            // Active Voice Lock Reticle & Sonar Sweep
            val sonarRadius = baseRadius * (0.50f + amplitude * 0.38f)
            drawCircle(
                color = SuccessEmerald.copy(alpha = 0.65f),
                radius = sonarRadius,
                center = center,
                style = Stroke(width = 2.8f)
            )
        }
        VoiceState.THINKING, VoiceState.PROCESSING -> {
            // Fast Synaptic Computation Orbiting Data Clusters
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
                        radius = 4.5f,
                        center = nodePos
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.45f),
                        radius = 9.5f,
                        center = nodePos
                    )
                }
            }
        }
        VoiceState.ERROR -> {
            // Crimson Diagnostic Alert Brackets
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

private fun createPolygonPath(center: Offset, radius: Float, sides: Int): Path {
    val path = Path()
    for (i in 0 until sides) {
        val angleRad = (2.0 * PI * i / sides) - (PI / 2.0)
        val x = center.x + (radius * cos(angleRad)).toFloat()
        val y = center.y + (radius * sin(angleRad)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
