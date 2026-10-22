package cn.qztc.timetable.qnu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.*

/** Account configuration never requires a school network connection. */
@Composable
internal fun EducationAccountScreen(saved: QnuSavedCredentials?, onBack: () -> Unit, onSave: (QnuSavedCredentials) -> Unit) {
    var username by rememberSaveable { mutableStateOf(saved?.username.orEmpty()) }
    var password by remember { mutableStateOf(saved?.password.orEmpty()) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        QztcPageTopBar("教务账号", onBack = onBack)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("教务账号") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation())
            Button(enabled = username.isNotBlank() && password.isNotEmpty(), onClick = {
                onSave(QnuSavedCredentials(username.trim(), password))
            }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
        }
    }
}
