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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by vm.profile.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var nickname by remember(profile?.id) { mutableStateOf(profile?.nickname ?: "") }
    var avatarFile by remember(profile?.id) { mutableStateOf(profile?.avatarFile) }
    var deviceId by remember(profile?.id) {
        mutableStateOf(profile?.deviceId?.ifBlank { null } ?: UUID.randomUUID().toString().take(8).uppercase())
    }

    // 注意：Kotlin 局部函数必须先声明后使用，所以 persist() 放在所有 launcher 之前
    fun persist() {
        val base = profile ?: ProfileEntity()
        scope.launch {
            vm.repo.saveProfile(
                base.copy(
                    nickname = nickname.trim(),
                    avatarFile = avatarFile,
                    deviceId = deviceId,
                    deviceLabel = base.deviceLabel.ifBlank { android.os.Build.MODEL ?: "Android" }
                )
            )
            snackbar.showSnackbar("已保存")
        }
    }

    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        LockState.endExternalActivity()
        if (uri != null) {
            scope.launch {
                val f = PhotoStore.importAvatar(ctx, uri)
                if (f != null) {
                    avatarFile = f.name
                    persist()
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("本地档案") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { persist(); onBack() }) { Text("保存") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            val avatar = avatarFile?.let { File(ctx.filesDir, "avatar.jpg") }
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        LockState.beginExternalActivity()
                        pickAvatar.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (avatar != null && avatar.exists()) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = "头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        nickname.take(1).ifBlank { "记" },
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text("点一下换头像", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            SurfaceCard {
                Text("昵称", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 16) nickname = it },
                    placeholder = { Text("给自己起个名字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("最多 16 个字，可以随时改", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SurfaceCard {
                Text("这台设备", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("设备标识", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(deviceId, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "这个标识只是给数据留个来源记号，换手机不影响读取旧数据。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SurfaceCard {
                Text("关于档案", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(
                    "档案只存在这台手机里，没有账号、没有服务器。换手机时用「备份与恢复」把数据搬过去。",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { persist(); onBack() },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("保存") }
            Spacer(Modifier.height(20.dp))
        }
    }
}
