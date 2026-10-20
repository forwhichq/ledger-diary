package com.ledgerdiary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ledgerdiary.app.ui.AppViewModel
import com.ledgerdiary.app.ui.components.SurfaceCard
import com.ledgerdiary.app.util.AppSettings
import com.ledgerdiary.app.util.DateUtils
import java.io.File

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onOpenProfile: () -> Unit,
    onOpenBackup: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val profile by vm.profile.collectAsState()
    val all by vm.transactions.collectAsState()
    val entries by vm.diaryEntries.collectAsState()
    val photoCount by vm.photoCount.collectAsState()
    var lockOn by remember { mutableStateOf(AppSettings.isLockEnabled(ctx)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("我的", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 8.dp))

        SurfaceCard {
            Row(
                Modifier.fillMaxWidth().clickable { onOpenProfile() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val avatarFile = profile?.avatarFile?.let { File(ctx.filesDir, it) }
                if (avatarFile != null && avatarFile.exists()) {
                    AsyncImage(
                        model = avatarFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(50))
                    )
                } else {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (profile?.nickname?.take(1) ?: "记"),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile?.nickname?.ifBlank { null } ?: "点这里设置昵称",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "本机档案 · " + (profile?.deviceLabel ?: "未命名设备"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SurfaceCard {
            Row(Modifier.fillMaxWidth()) {
                MiniStat("记账", all.size.toString() + " 笔", Modifier.weight(1f))
                MiniStat("日记", entries.size.toString() + " 篇", Modifier.weight(1f))
                MiniStat("照片", photoCount.toString() + " 张", Modifier.weight(1f))
            }
        }

        SettingGroup {
            SettingRow(
                icon = Icons.Outlined.FileUpload,
                title = "备份与恢复",
                subtitle = "导出 zip / CSV，或从旧手机导入",
                onClick = onOpenBackup
            )
        }

        SettingGroup {
            SettingSwitch(
                icon = Icons.Outlined.Lock,
                title = "应用锁",
                subtitle = "打开 App 时需要指纹或图案",
                checked = lockOn,
                onChange = {
                    lockOn = it
                    AppSettings.setLockEnabled(ctx, it)
                }
            )
        }

        SettingGroup {
            SettingInfo(
                icon = Icons.Outlined.WifiOff,
                title = "这个 App 不联网",
                subtitle = "没有申请网络权限，数据只存在这台手机上"
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "记账日记 v" + com.ledgerdiary.app.BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingInfo(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
