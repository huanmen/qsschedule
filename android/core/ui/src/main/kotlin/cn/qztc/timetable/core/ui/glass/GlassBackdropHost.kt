package cn.qztc.timetable.core.ui.glass

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

internal val LocalGlassHazeState = staticCompositionLocalOf<HazeState?> { null }
internal val LocalGlassEffectsSuspended = staticCompositionLocalOf { false }

/** A single source layer keeps backdrop capture bounded regardless of the number of glass controls. */
@Composable
fun GlassBackdropHost(
    background: @Composable BoxScope.() -> Unit,
    content: @Composable () -> Unit,
    suspendEffects: Boolean = false,
) {
    val hazeState = remember { HazeState() }
    CompositionLocalProvider(
        LocalGlassHazeState provides if (suspendEffects) null else hazeState,
        LocalGlassEffectsSuspended provides suspendEffects,
    ) {
        Box(Modifier.fillMaxSize()) {
            val sourceModifier = if (suspendEffects) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize().hazeSource(state = hazeState)
            }
            Box(sourceModifier, content = background)
            content()
        }
    }
}
