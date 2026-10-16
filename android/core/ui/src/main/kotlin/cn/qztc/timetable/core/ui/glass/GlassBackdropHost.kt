package cn.qztc.timetable.core.ui.glass

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import cn.qztc.timetable.core.ui.theme.LocalQztcColors

internal val LocalGlassHazeState = staticCompositionLocalOf<HazeState?> { null }
// Suspension changes on navigation/scroll; only its readers need recomposition.
internal val LocalGlassEffectsSuspended = compositionLocalOf { false }

/** A single source layer keeps backdrop capture bounded regardless of the number of glass controls. */
@Composable
fun GlassBackdropHost(
    background: @Composable BoxScope.() -> Unit,
    content: @Composable () -> Unit,
    suspendEffects: Boolean = false,
    backdropColor: androidx.compose.ui.graphics.Color? = null,
) {
    val hazeState = remember { HazeState() }
    val base = LocalQztcColors.current.appBg
    CompositionLocalProvider(
        LocalGlassHazeState provides hazeState,
        LocalGlassEffectsSuspended provides suspendEffects,
        LocalBackdropColor provides backdropColor,
    ) {
        Box(Modifier.fillMaxSize().background(base)) {
            // Keep the source attached so navigation never changes the material.
            // Only interaction animation is paused; the shared background is stable.
            val sourceModifier = Modifier.fillMaxSize().hazeSource(state = hazeState).background(base)
            Box(sourceModifier, content = background)
            content()
        }
    }
}
