package cn.qztc.timetable.core.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.motion.MotionSpec
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcCorners
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private const val LIQUID_EDGE_SHADER = """
    uniform float2 viewport;
    half4 main(float2 p) {
        float2 uv = p / viewport;
        float edge = 1.0 - smoothstep(0.0, 0.16,
            min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y)));
        float wave = 0.5 + 0.5 * sin(uv.x * 13.0 + sin(uv.y * 10.0) * 1.7);
        half3 spectral = half3(
            0.35 + 0.55 * wave,
            0.62 + 0.30 * (1.0 - wave),
            0.92
        );
        return half4(spectral, edge * (0.045 + wave * 0.055));
    }
"""

/**
 * The only glass rendering boundary used by business features. Haze captures one shared backdrop;
 * this layer adds tint, optical edge lighting and interaction feedback without touching text.
 */
@Composable
@OptIn(ExperimentalHazeApi::class)
fun GlassSurface(
    role: GlassRole,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(
        if (role == GlassRole.FUNCTIONAL) QztcCorners.functional else QztcCorners.content,
    ),
    pressGlint: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalQztcColors.current
    val settings = LocalGlassSettings.current
    val quality = settings.effectiveQuality
    val hazeState = LocalGlassHazeState.current
    val effectsSuspended = LocalGlassEffectsSuspended.current
    val glintProgress = remember { Animatable(-1f) }
    var glintTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(glintTrigger, quality, settings.reduceMotion) {
        if (glintTrigger > 0 && quality == GlassQuality.FULL_LENS && !settings.reduceMotion) {
            glintProgress.snapTo(-.35f)
            glintProgress.animateTo(1.35f, tween(MotionSpec.GLINT_SWEEP_MS, easing = LinearEasing))
            glintProgress.snapTo(-1f)
        }
    }

    val base = if (role == GlassRole.FUNCTIONAL) colors.glassFunctionalBase else colors.glassContentBase
    val alpha = if (role == GlassRole.FUNCTIONAL) settings.functionalAlpha else settings.contentAlpha
    val blur = if (role == GlassRole.FUNCTIONAL) settings.functionalBlur else settings.contentBlur
    // Keep a light content veil even when a user-selected background is very
    // dark. This preserves the glass treatment while keeping the app's dark
    // text readable without per-text contrast bookkeeping.
    val legibleSurface = colors.solid.copy(alpha = .78f)
    val fallback = when {
        quality == GlassQuality.SOLID_SURFACE -> colors.solid
        role == GlassRole.FUNCTIONAL -> base.copy(alpha = alpha.coerceIn(.12f, .92f))
        else -> legibleSurface
    }
    val usesBackdrop = !effectsSuspended && quality != GlassQuality.SOLID_SURFACE && hazeState != null
    val hazeModifier = if (usesBackdrop) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = colors.appBg,
                tint = HazeTint(base.copy(alpha = if (role == GlassRole.FUNCTIONAL) {
                    (alpha * .54f).coerceIn(.08f, .52f)
                } else {
                    maxOf(alpha * .54f, .28f).coerceAtMost(.66f)
                })),
                blurRadius = blur,
                noiseFactor = if (quality == GlassQuality.FULL_LENS) .045f else .018f,
                fallbackTint = HazeTint(fallback),
            ),
        ) {
            // A fixed 0.66 scale keeps backdrop blur responsive on phone GPUs.
            inputScale = HazeInputScale.Fixed(.66f)
        }
    } else {
        Modifier.background(fallback)
    }
    val pointerModifier = if (pressGlint && !effectsSuspended && quality == GlassQuality.FULL_LENS && !settings.reduceMotion) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                glintTrigger++
                waitForUpOrCancellation()
            }
        }
    } else {
        Modifier
    }

    val opticalModifier = Modifier.drawWithCache {
        val fullOptics = !effectsSuspended && quality == GlassQuality.FULL_LENS
        val borderWidth = if (fullOptics) 1.4.dp.toPx() else 1.dp.toPx()
        val opticalCorner = (if (role == GlassRole.FUNCTIONAL) QztcCorners.functional else QztcCorners.content).toPx()
        val borderBrush = when (quality) {
            GlassQuality.FULL_LENS -> if (fullOptics) Brush.sweepGradient(
                listOf(
                    Color(0xFF6EE7F2).copy(alpha = .72f),
                    Color.White.copy(alpha = .76f),
                    Color(0xFFFFA7D8).copy(alpha = .58f),
                    Color(0xFFFFE08A).copy(alpha = .62f),
                    Color(0xFF6EE7F2).copy(alpha = .72f),
                ),
            ) else SolidColor(colors.glassBorder)
            GlassQuality.BLUR_TINT -> SolidColor(colors.glassBorder)
            GlassQuality.SOLID_SURFACE -> SolidColor(colors.line2)
        }
        val opticalBrush = if (fullOptics && Build.VERSION.SDK_INT >= 33) {
            runtimeOpticalBrush(size)
        } else null
        val progress = glintProgress.value
        val glintBrush = if (progress >= -.35f) {
            val center = size.width * progress
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = .42f), Color.Transparent),
                start = Offset(center - size.width * .22f, size.height),
                end = Offset(center + size.width * .22f, 0f),
            )
        } else null

        onDrawWithContent {
            drawContent()
            if (opticalBrush != null) {
                drawRoundRect(opticalBrush, cornerRadius = CornerRadius(opticalCorner), blendMode = BlendMode.Screen)
            }
            drawRoundRect(borderBrush, cornerRadius = CornerRadius(opticalCorner), style = Stroke(borderWidth))
            drawLine(
                colors.glassHighlight.copy(alpha = if (quality == GlassQuality.SOLID_SURFACE) .18f else .72f),
                Offset(size.width * .12f, borderWidth),
                Offset(size.width * .88f, borderWidth),
                strokeWidth = borderWidth,
            )
            if (glintBrush != null) {
                drawRoundRect(glintBrush, cornerRadius = CornerRadius(opticalCorner), blendMode = BlendMode.Screen)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(hazeModifier)
            .then(pointerModifier)
            .then(opticalModifier),
    ) {
        if (quality != GlassQuality.SOLID_SURFACE) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colors.glassHighlight.copy(alpha = if (role == GlassRole.FUNCTIONAL) .17f else .09f),
                                base.copy(alpha = .025f),
                                Color.Black.copy(alpha = if (colors.isDark) .08f else .025f),
                            ),
                        ),
                    ),
            )
        }
        content()
    }
}

@RequiresApi(33)
private fun runtimeOpticalBrush(size: Size): ShaderBrush {
    val shader = RuntimeShader(LIQUID_EDGE_SHADER)
    shader.setFloatUniform("viewport", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
    return ShaderBrush(shader)
}
