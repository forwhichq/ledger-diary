package com.ledgerdiary.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ledgerdiary.app.data.ProfileEntity
import com.ledgerdiary.app.ui.AppViewModel
import com.ledgerdiary.app.ui.LockState
import com.ledgerdiary.app.ui.components.SurfaceCard
import com.ledgerdiary.app.util.PhotoStore
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * 首次启动引导。只做两件事：认一下脸（昵称+头像），顺便说明数据只在本机。
 * 全部可跳过 —— 用户如果只想赶紧记一笔，点「以后再说」直接进主界面。
 */
@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf("") }
    var avatarName by remember { mutableStateOf<String?>(null) }

    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        LockState.endExternalActivity()
        if (uri != null) {
            scope.launch {
                PhotoStore.importAvatar(ctx, uri)?.let { avatarName = it.name }
            }
        }
    }

    fun save() {
        val name = nickname.trim()
        if (name.isNotEmpty() || avatarName != null) {
            scope.launch {
                vm.repo.saveProfile(
                    ProfileEntity(
                        nickname = name,
                        avatarFile = avatarName,
                        deviceId = UUID.randomUUID().toString().take(8).uppercase(),
                        deviceLabel = android.os.Build.MODEL ?: "Android"
                    )
                )
                onDone()
            }
        } else {
            onDone()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.EditNote, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("记账日记", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "钱记清楚，日子也记下来",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(36.dp))

        // 头像
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    LockState.beginExternalActivity()
                    pickAvatar.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val f = avatarName?.let { File(ctx.filesDir, it) }
            if (f != null && f.exists()) {
                AsyncImage(
                    model = f,
                    contentDescription = "头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Outlined.AddAPhoto, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "点一下换个头像（可以跳过）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { if (it.length <= 16) nickname = it },
            label = { Text("昵称") },
            placeholder = { Text("比如：小记") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        Spacer(Modifier.height(20.dp))

        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WifiOff, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("不联网，数据只在这台手机上", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "App 没有申请网络权限，账和日记不会被上传；换手机时用「备份与恢复」搬过去。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("开始使用") }

        TextButton(onClick = { onDone() }, modifier = Modifier.fillMaxWidth()) {
            Text("以后再说", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "头像和昵称只存在本机，可以随时在「我的」里改",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
    }
}
