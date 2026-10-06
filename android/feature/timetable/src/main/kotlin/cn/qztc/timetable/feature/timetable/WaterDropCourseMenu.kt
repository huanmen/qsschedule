package cn.qztc.timetable.feature.timetable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.glass.GlassQuality
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.glass.LocalGlassSettings
import cn.qztc.timetable.core.ui.motion.MotionSpec
import cn.qztc.timetable.core.ui.motion.pressable
import cn.qztc.timetable.core.ui.theme.CoursePalette
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcTypography

private val menuHeight = 76.dp
private val menuGap = 14.dp

@Composable
internal fun WaterDropCourseMenu(
    occurrence: DemoOccurrence,
    expanded: Boolean,
    anchorFraction: Float,
    onDetails: () -> Unit,
    onReminder: () -> Unit,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    val settings = LocalGlassSettings.current
    val animateLiquid = !settings.reduceMotion && settings.effectiveQuality != GlassQuality.SOLID_SURFACE
    val progress = remember(occurrence.id) { Animatable(0f) }
    val height by animateDpAsState(
        targetValue = if (expanded) menuHeight + menuGap else 0.dp,
        animationSpec = tween(if (expanded && animateLiquid) 180 else 120, easing = FastOutSlowInEasing),
        label = "course-menu-height",
    )

    LaunchedEffect(expanded, animateLiquid) {
        if (expanded) {
            if (!animateLiquid) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(
                    1f,
                    animationSpec = tween(
                        durationMillis = 540,
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                )
            }
        } else if (progress.value > 0f) {
            progress.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
        }
    }

    if (height > 0.dp) {
        val liquid = occurrence.visualColor(colors.isDark).bar
        // These phase boundaries mirror the prototype: the menu begins to show as
        // the drop spreads, then its actions settle in a short stagger.
        val reveal = phase(progress.value, .54f, .80f)
        val actions = phase(progress.value, .70f, .90f)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clipToBounds(),
        ) {
            if (animateLiquid && progress.value < .88f) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLiquidDrop(
                        progress = progress.value,
                        anchorFraction = anchorFraction,
                        liquidColor = liquid.copy(alpha = .90f),
                    )
                }
            }
            GlassSurface(
                role = GlassRole.FUNCTIONAL,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 2.dp)
                    .fillMaxWidth()
                    .height(menuHeight)
                    .graphicsLayer {
                        alpha = if (animateLiquid) reveal else 1f
                        scaleY = if (animateLiquid) 0.90f + reveal * .10f else 1f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    },
                shape = RoundedCornerShape(18.dp),
                pressGlint = true,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp)
                        .alpha(if (animateLiquid) actions else 1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MenuAction(Icons.Outlined.Info, "详情", onDetails)
                    MenuAction(Icons.Outlined.Notifications, "提醒", onReminder)
                    MenuAction(Icons.Outlined.NearMe, "导航", onNavigate)
                }
            }
        }
    }
}

@Composable
private fun MenuAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalQztcColors.current
    Row(
        modifier = Modifier
            .height(52.dp)
            .clickable(onClick = onClick)
            .pressable()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
        Text(label, style = QztcTypography.meta, color = colors.ink)
    }
}

private fun DrawScope.drawLiquidDrop(
    progress: Float,
    anchorFraction: Float,
    liquidColor: Color,
) {
    val anchorX = size.width * anchorFraction.coerceIn(.12f, .88f)
    val appear = phase(progress, 0f, .22f)
    val fall = phase(progress, .20f, .54f)
    val spread = phase(progress, .54f, .86f)
    val fade = phase(progress, .54f, .72f)
    val top = 2.dp.toPx()
    val floor = size.height - 11.dp.toPx()
    val centerY = top + (floor - top) * easeIn(fall)
    val radius = 10.5.dp.toPx() * appear * (1f + .24f * fall)
    if (radius > .5f && fade < 1f) {
        val stretchY = 1f + .38f * fall - .65f * spread
        val stretchX = 1f - .10f * fall + .88f * spread
        val rX = radius * stretchX
        val rY = radius * stretchY.coerceAtLeast(.35f)
        val drop = Path().apply {
            // A rounded diamond gives the drop the pointed top used by the prototype.
            moveTo(anchorX, centerY - rY * 1.45f)
            cubicTo(anchorX - rX * .12f, centerY - rY * 1.08f, anchorX - rX, centerY - rY * .52f, anchorX - rX, centerY)
            cubicTo(anchorX - rX, centerY + rY * .72f, anchorX - rX * .45f, centerY + rY, anchorX, centerY + rY)
            cubicTo(anchorX + rX * .45f, centerY + rY, anchorX + rX, centerY + rY * .72f, anchorX + rX, centerY)
            cubicTo(anchorX + rX, centerY - rY * .52f, anchorX + rX * .12f, centerY - rY * 1.08f, anchorX, centerY - rY * 1.45f)
            close()
        }
        drawPath(drop, liquidColor.copy(alpha = liquidColor.alpha * (1f - fade)))
    }

    if (spread > 0f) {
        val poolWidth = 26.dp.toPx() + (size.width - 30.dp.toPx()) * spread
        val poolHeight = 12.dp.toPx() + (size.height - 22.dp.toPx()) * spread
        drawRoundRect(
            color = liquidColor.copy(alpha = .80f),
            topLeft = androidx.compose.ui.geometry.Offset(anchorX - poolWidth / 2f, floor - poolHeight),
            size = androidx.compose.ui.geometry.Size(poolWidth, poolHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
        )
        val splash = kotlin.math.sin((spread.coerceIn(0f, .75f) / .75f) * Math.PI).toFloat()
        if (splash > .02f) {
            drawCircle(
                liquidColor.copy(alpha = splash * .78f),
                radius = 3.5.dp.toPx() * (1f - spread * .35f),
                center = androidx.compose.ui.geometry.Offset(anchorX - 26.dp.toPx() * spread, floor - 22.dp.toPx() * splash),
            )
            drawCircle(
                liquidColor.copy(alpha = splash * .72f),
                radius = 3.2.dp.toPx() * (1f - spread * .30f),
                center = androidx.compose.ui.geometry.Offset(anchorX + 22.dp.toPx() * spread, floor - 18.dp.toPx() * splash),
            )
        }
    }
}

private fun phase(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun easeIn(value: Float): Float = value * value
