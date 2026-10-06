package cn.qztc.timetable.core.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
        Row(
            Modifier
                .fillMaxWidth()
                .height(QztcTopBarTokens.Height)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                QztcTopIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
            }
            Column(Modifier.weight(1f).padding(start = if (onBack != null) 2.dp else 10.dp, end = 8.dp)) {
                Text(
                    title,
                    style = QztcTypography.courseTitle,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = QztcTypography.compact,
                        color = colors.mut,
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
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.ink,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.faint,
        ),
        modifier = modifier.size(QztcTopBarTokens.IconButtonSize),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint ?: if (enabled) colors.ink else colors.faint,
            modifier = Modifier.size(QztcTopBarTokens.IconSize),
        )
    }
}
