package cn.qztc.timetable.qnu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcCorners
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography

@Composable
fun QnuAccountSettingsScreen(
    initialCredentials: QnuSavedCredentials?,
    onSave: (QnuSavedCredentials) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    var username by remember(initialCredentials) { mutableStateOf(initialCredentials?.username.orEmpty()) }
    var password by remember(initialCredentials) { mutableStateOf(initialCredentials?.password.orEmpty()) }
    var passwordVisible by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = "教务账号", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(QztcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.lg),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(QztcCorners.content))
                    .background(colors.solid)
                    .padding(QztcSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; validationMessage = null },
                    label = { Text("账号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; validationMessage = null },
                    label = { Text("密码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                validationMessage?.let {
                    Text(it, style = QztcTypography.meta, color = colors.danger)
                }
                Text(
                    "凭据由 Android Keystore 加密，仅用于教务登录页自动填充。后台同步优先复用现有会话。",
                    style = QztcTypography.meta,
                    color = colors.mut,
                )
                Button(
                    onClick = {
                        when {
                            username.isBlank() -> validationMessage = "请输入教务账号"
                            password.isEmpty() -> validationMessage = "请输入教务密码"
                            else -> onSave(QnuSavedCredentials(username.trim(), password))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("加密保存")
                }
                if (initialCredentials != null) {
                    OutlinedButton(
                        onClick = {
                            username = ""
                            password = ""
                            onClear()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        Text("清除已保存账号", modifier = Modifier.padding(start = QztcSpacing.sm))
                    }
                }
            }
        }
    }
}
