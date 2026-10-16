package cn.qztc.timetable.core.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.glass.LocalGlassForeground

/** 与周课表顶栏对齐的导航尺寸。 */
object QztcTopBarTokens {
    val Height = 48.dp
    val HorizontalPadding = 14.dp
    val VerticalPadding = 6.dp
    val IconButtonSize = 40.dp
    val IconSize = 22.dp
}

@Composable
fun QztcPageTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    centeredTitle: Boolean = false,
    navigation: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    centerContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalQztcColors.current
    GlassSurface(
        role = GlassRole.FUNCTIONAL,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = QztcTopBarTokens.HorizontalPadding, vertical = QztcTopBarTokens.VerticalPadding),
        pressGlint = true,
    ) {
        val foreground = LocalGlassForeground.current ?: colors.ink
        Box(contentAlignment=Alignment.Center) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(QztcTopBarTokens.Height)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigation != null) navigation()
            else if (onBack != null) {
                QztcTopIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
            }
            if (centeredTitle && onBack == null && navigation == null) androidx.compose.foundation.layout.Spacer(Modifier.size(40.dp))
            Column(Modifier.weight(1f).padding(start = if (onBack != null || centeredTitle) 2.dp else 10.dp, end = if (centeredTitle) 2.dp else 8.dp),
                horizontalAlignment = if (centeredTitle) Alignment.CenterHorizontally else Alignment.Start) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    title,
                    modifier = if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier,
                    style = QztcTypography.courseTitle,
                    color = foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                titleTrailing?.invoke()
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = QztcTypography.compact,
                        color = foreground.copy(alpha = .75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                content = actions,
            )
        }
        centerContent?.invoke()
        }
    }
}

@Composable
fun QztcTopIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    val foreground = LocalGlassForeground.current ?: colors.ink
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = foreground,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.faint,
        ),
        modifier = modifier.size(QztcTopBarTokens.IconButtonSize),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint ?: if (enabled) foreground else foreground.copy(alpha = .4f),
            modifier = Modifier.size(QztcTopBarTokens.IconSize),
        )
    }
}
