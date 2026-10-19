package com.ledgerdiary.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ledgerdiary.app.backup.BackupManager
import com.ledgerdiary.app.util.FileSaver
import com.ledgerdiary.app.ui.AppViewModel
import com.ledgerdiary.app.ui.LockState
import com.ledgerdiary.app.ui.components.SurfaceCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val mgr = remember { BackupManager(ctx, vm.repo) }

    var busy by remember { mutableStateOf(false) }
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    // 导入流程状态
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingInfo by remember { mutableStateOf<BackupManager.BackupInfo?>(null) }
    var importPassword by remember { mutableStateOf("") }
    var needPassword by remember { mutableStateOf(false) }

    // 导出：先写 App 私有临时文件，再「存到下载目录」或「分享出去」。
    // 注意：这里故意不再默认弹系统文件选择器。拉起选择器会让本页退到后台，
    // 回来时 rememberCoroutineScope 可能已被取消，写入协程还没跑就死了，
    // 系统就会留下一个 0 字节的空文件（2026-10-19 实测踩到过）。
    var exportFile by remember { mutableStateOf<java.io.File?>(null) }
    var exportIsCsv by remember { mutableStateOf(false) }

    // 「换个位置另存」：只有用户主动点它时才走系统选择器，且写入是同步的，不依赖协程活着
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        LockState.endExternalActivity()
        val f = exportFile
        if (uri != null && f != null) {
            val r = runCatching { FileSaver.saveToUri(ctx, f, uri) }
            r.onSuccess { n ->
                scope.launch { snackbar.showSnackbar("已保存 " + FileSaver.sizeText(n) + " 到 " + (uri.lastPathSegment ?: "所选位置")) }
            }.onFailure {
                scope.launch { snackbar.showSnackbar("保存失败：" + (it.message ?: "未知错误")) }
            }
        }
        // 注意：这里不要把 exportFile 置空。「刚导出的文件」卡片留着，
        // 用户另存到别处之后仍然可以直接点「分享」再发一次。
    }

    /** 存到系统「下载/记账日记」目录，写完核对字节数。 */
    fun downloadExport(f: java.io.File, csv: Boolean) {
        scope.launch {
            busy = true
            runCatching {
                FileSaver.saveToDownloads(ctx, f, if (csv) "text/csv" else "application/zip")
            }.onSuccess { s ->
                snackbar.showSnackbar("已保存 " + FileSaver.sizeText(s.bytes) + " 到 " + s.displayPath)
            }.onFailure {
                snackbar.showSnackbar("保存失败：" + (it.message ?: "未知错误"))
            }
            busy = false
        }
    }

    /** 拉起系统分享面板，发给微信 / 网盘 / 邮件。 */
    fun shareExport(f: java.io.File, csv: Boolean) {
        LockState.beginExternalActivity()
        runCatching { FileSaver.share(ctx, f, if (csv) "text/csv" else "application/zip") }
            .onFailure {
                LockState.endExternalActivity()
                scope.launch { snackbar.showSnackbar("分享失败：" + (it.message ?: "未知错误")) }
            }
    }

    /** 导出成功后统一的后续动作：存下载目录 + 提示还可以分享 / 另存。 */
    fun afterExport(f: java.io.File, csv: Boolean) {
        exportFile = f
        exportIsCsv = csv
        downloadExport(f, csv)
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        LockState.endExternalActivity()
        if (uri != null) {
            pendingUri = uri
            scope.launch {
                busy = true
                val info = runCatching { mgr.peek(uri, null) }.getOrNull()
                busy = false
                if (info == null) {
                    needPassword = true
                    pendingInfo = null
                } else {
                    pendingInfo = info
                    needPassword = false
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SurfaceCard {
                Text("导出备份", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "打包成一个 zip：包含全部账目、日记、照片和头像。换手机时用它搬过去。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("设置密码", style = MaterialTheme.typography.bodyLarge)
                        Text("密码只用于加密这个 zip，忘了就打不开",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = usePassword, onCheckedChange = {
                        usePassword = it
                        if (!it) password = ""
                    })
                }

                if (usePassword) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("备份密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (usePassword && password.length < 4) {
                            scope.launch { snackbar.showSnackbar("密码至少 4 位") }
                            return@Button
                        }
                        scope.launch {
                            busy = true
                            runCatching { mgr.exportZip(if (usePassword) password else null) }
                                .onSuccess { r -> afterExport(r.file, csv = false) }
                                .onFailure {
                                    snackbar.showSnackbar("导出失败：" + (it.message ?: "未知错误"))
                                }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (busy) "处理中…" else "导出备份 zip") }
            }

            SurfaceCard {
                Text("导出账单表格", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "CSV 文件，可以用 Excel / WPS 打开，只含账目，不含日记。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching { mgr.exportCsv() }
                                .onSuccess { f -> afterExport(f, csv = true) }
                                .onFailure {
                                    snackbar.showSnackbar("导出失败：" + (it.message ?: "未知错误"))
                                }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("导出 CSV") }
            }

            // 导出完成后出现的后续动作卡片：分享 / 换个位置另存 / 再存一份
            val last = exportFile
            if (last != null) {
                SurfaceCard {
                    Text("刚导出的文件", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        last.name + " · " + FileSaver.sizeText(last.length()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { shareExport(last, exportIsCsv) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("分享") }
                        OutlinedButton(
                            onClick = { downloadExport(last, exportIsCsv) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("再存一份") }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            LockState.beginExternalActivity()
                            saveLauncher.launch(last.name)
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) { Text("另存到我选的位置") }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "「分享」可以发到微信、网盘或邮件，换手机时最方便。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SurfaceCard {
                Text("从备份恢复", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "选择之前导出的 zip。如果备份设了密码，会提示你输入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        // 只传 */*：多传几个 mime 会让系统选择器顶部出现筛选条，
                        // 用户点了 Audio/Documents 就会把 zip 过滤掉，看起来像「找不到备份文件」。
                        LockState.beginExternalActivity()
                        openLauncher.launch(arrayOf("*/*"))
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("选择备份文件") }

                val info = pendingInfo
                if (info != null) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("这个备份包含", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    InfoRow("导出时间", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                        .format(Date(info.exportedAt)))
                    InfoRow("来自设备", info.deviceLabel.ifBlank { "未知" })
                    InfoRow("账目", info.transactions.toString() + " 笔")
                    InfoRow("日记", info.diaryEntries.toString() + " 篇")
                    InfoRow("照片", info.photos.toString() + " 张")
                    Spacer(Modifier.height(12.dp))

                    Text(
                        "「合并」会把备份里的记录追加进来，重复的自动跳过；「覆盖还原」会先自动备份当前数据，然后清空并替换。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val uri = pendingUri ?: return@OutlinedButton
                                scope.launch {
                                    busy = true
                                    // 加密备份：解锁时输入的密码必须继续传下去，
                                    // 否则 importZip 里 require(password != null) 会直接抛「这个备份设置了密码」。
                                    runCatching {
                                        mgr.importZip(uri, importPassword.takeIf { it.isNotEmpty() }, overwrite = false)
                                    }
                                        .onSuccess { r ->
                                            pendingInfo = null
                                            pendingUri = null
                                            snackbar.showSnackbar(
                                                "已合并 " + r.transactions + " 笔账、" + r.diaryEntries + " 篇日记"
                                            )
                                        }
                                        .onFailure {
                                            snackbar.showSnackbar("合并失败：" + (it.message ?: "未知错误"))
                                        }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("合并") }

                        Button(
                            onClick = {
                                val uri = pendingUri ?: return@Button
                                scope.launch {
                                    busy = true
                                    runCatching {
                                        mgr.importZip(uri, importPassword.takeIf { it.isNotEmpty() }, overwrite = true)
                                    }
                                        .onSuccess { r ->
                                            pendingInfo = null
                                            pendingUri = null
                                            snackbar.showSnackbar(
                                                "已还原 " + r.transactions + " 笔账、" + r.diaryEntries + " 篇日记"
                                            )
                                        }
                                        .onFailure {
                                            snackbar.showSnackbar("还原失败：" + (it.message ?: "未知错误"))
                                        }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("覆盖还原") }
                    }
                }

                if (needPassword) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("这个备份有密码", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text("备份密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val uri = pendingUri ?: return@Button
                            scope.launch {
                                busy = true
                                val info2 = runCatching { mgr.peek(uri, importPassword) }.getOrNull()
                                busy = false
                                if (info2 == null) {
                                    snackbar.showSnackbar("密码不对，或文件已损坏")
                                } else {
                                    pendingInfo = info2
                                    needPassword = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("解锁并查看") }
                }
            }

            SurfaceCard {
                Text("提醒", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "这个 App 不联网，备份文件也不会自动上传。请自己把 zip 存到网盘或电脑上——手机丢了就真的找不回来了。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
