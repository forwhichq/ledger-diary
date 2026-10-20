package com.ledgerdiary.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ledgerdiary.app.data.DiaryBundle
import com.ledgerdiary.app.data.DiaryEntryEntity
import com.ledgerdiary.app.data.DiaryPhotoEntity
import com.ledgerdiary.app.data.DiarySegmentEntity
import com.ledgerdiary.app.ui.AppViewModel
import com.ledgerdiary.app.ui.LockState
import com.ledgerdiary.app.ui.components.SurfaceCard
import com.ledgerdiary.app.ui.theme.MoodPalette
import com.ledgerdiary.app.util.DateUtils
import com.ledgerdiary.app.util.PhotoStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

private val WEATHERS = listOf("晴", "多云", "阴", "小雨", "大雨", "雪", "雾", "霾")
private const val MAX_PHOTOS = 9

/**
 * 写日记 / 改日记。
 *
 * 数据模型：一天可以有多篇。这里的「一篇」= diary_entries 的一行，
 * 篇内部还可以分多段文字（每段有自己的时间戳），所以早上写一段、中午写另一篇、
 * 晚上再补一段，时间线都是真实的。
 *
 * 参数：
 *   entryId —— 有值 = 改这一篇；null = 新写一篇（date 决定写在哪天）
 *   date    —— 新写时用哪天；改的时候以库里那篇的 date 为准
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditScreen(vm: AppViewModel, entryId: String?, date: String?, onDone: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var curDate by remember { mutableStateOf(date ?: DateUtils.today()) }
    var loaded by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf<DiaryEntryEntity?>(null) }
    // 新写一篇时，提示「这天已经写过几篇」，避免用户以为重复了
    var sameDayCount by remember { mutableIntStateOf(0) }
    var segments by remember { mutableStateOf<List<DiarySegmentEntity>>(emptyList()) }
    var photos by remember { mutableStateOf<List<DiaryPhotoEntity>>(emptyList()) }
    var mood by remember { mutableIntStateOf(0) }
    var tags by remember { mutableStateOf("") }
    var weatherOn by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf("") }
    var locationOn by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var fullPhoto by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        val existing = entryId?.let { vm.repo.loadDiaryById(it) }
        if (existing != null) {
            entry = existing.entry
            curDate = existing.entry.date
            segments = existing.segments
            photos = existing.photos
            mood = existing.entry.mood
            tags = existing.entry.tags
            weather = existing.entry.weather ?: ""
            weatherOn = !existing.entry.weather.isNullOrBlank()
            location = existing.entry.location ?: ""
            locationOn = !existing.entry.location.isNullOrBlank()
        } else {
            // 新写一篇：不查重、不复用当天已有的那篇
            entry = null
            segments = emptyList()
            photos = emptyList()
            mood = 0; tags = ""; weather = ""; weatherOn = false
            location = ""; locationOn = false
        }
        sameDayCount = vm.repo.loadDiaries(curDate).size
        loaded = true
    }

    // entryId 决定编辑哪一篇，只在进页面时加载一次；
    // 日期选择改的是 curDate，不重新加载（否则刚写的内容会被冲掉）。
    LaunchedEffect(entryId) { load() }

    suspend fun persist() {
        val now = System.currentTimeMillis()
        val e = entry ?: DiaryEntryEntity(
            id = UUID.randomUUID().toString(),
            date = curDate,
            createdAt = now
        )
        val saved = e.copy(
            date = curDate,
            mood = mood,
            tags = tags.trim(),
            weather = if (weatherOn && weather.isNotBlank()) weather else null,
            location = if (locationOn && location.isNotBlank()) location.trim() else null
        )
        entry = saved
        vm.repo.saveDiary(
            DiaryBundle(
                entry = saved,
                segments = segments.mapIndexed { i, s ->
                    s.copy(entryId = saved.id, sortOrder = i)
                },
                photos = photos.map { it.copy(entryId = saved.id) }
            )
        )
    }


    // 系统相册多选
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)
    ) { uris: List<Uri> ->
        LockState.endExternalActivity()
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val room = MAX_PHOTOS - photos.size
            val added = mutableListOf<DiaryPhotoEntity>()
            uris.take(room).forEach { u ->
                val f = PhotoStore.importFromUri(ctx, u)
                if (f != null) {
                    added += DiaryPhotoEntity(
                        id = UUID.randomUUID().toString(),
                        entryId = entry?.id ?: "",
                        fileName = f.name,
                        createdAt = System.currentTimeMillis()
                    )
                }
            }
            photos = photos + added
            // 立刻落盘，防止用户忘记保存导致照片变孤儿文件
            persist()
        }
    }

    fun addSegment() {
        segments = segments + DiarySegmentEntity(
            id = UUID.randomUUID().toString(),
            entryId = entry?.id ?: "",
            text = "",
            createdAt = System.currentTimeMillis()
        )
    }

    // 注意 key 必须带 loaded：否则 loaded 一直是 true，切到另一篇（那篇还没有段落）时
    // segments 为空却不会补段，页面就一个输入框都没有。
    LaunchedEffect(loaded) {
        if (loaded && segments.isEmpty()) addSegment()
    }

    // 新写一篇时，改日期要跟着刷新「这天已经写过几篇」
    LaunchedEffect(curDate, loaded) {
        if (loaded && entry == null) sameDayCount = vm.repo.loadDiaries(curDate).size
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (entry == null) "写日记" else "编辑日记",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            buildString {
                                append(DateUtils.friendlyDate(curDate))
                                if (entry == null) {
                                    // 让「一天多篇」这件事在页面上看得见，不然用户会以为重复了
                                    if (sameDayCount > 0) append(" · 这天已有 " + sameDayCount + " 篇")
                                    else append(" · 这天第 1 篇")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch { persist(); onDone() }
                    }) {
                        Icon(Icons.Filled.Check, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 日期条
            SurfaceCard {
                Row(
                    Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("日期", style = MaterialTheme.typography.labelLarge)
                        if (entry == null && sameDayCount > 0) {
                            Text(
                                "这天已经写过 " + sameDayCount + " 篇，保存后会再多一篇",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        DateUtils.friendlyDate(curDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 心情
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("心情", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    if (mood > 0) {
                        TextButton(onClick = { mood = 0 }) { Text("清除") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (1..5).forEach { m ->
                        val active = mood == m
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (active) MoodPalette[m].copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { mood = m }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                listOf("😖", "😕", "😐", "🙂", "😄")[m - 1],
                                fontSize = 22.sp
                            )
                            Text(
                                listOf("很差", "一般", "还行", "不错", "很好")[m - 1],
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 多段正文
            segments.forEachIndexed { index, seg ->
                key(seg.id) {
                    SurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                DateUtils.timeOf(seg.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "  第 " + (index + 1) + " 段",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            if (segments.size > 1) {
                                IconButton(
                                    onClick = {
                                        segments = segments.filter { it.id != seg.id }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Close, "删除这一段", Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = seg.text,
                            onValueChange = { newText ->
                                segments = segments.map {
                                    if (it.id == seg.id) it.copy(text = newText) else it
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(
                                MaterialTheme.colorScheme.primary
                            ),
                            decorationBox = { inner ->
                                Box {
                                    if (seg.text.isEmpty()) {
                                        Text(
                                            "现在想记点什么…",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }
            }

            TextButton(onClick = { addSegment() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("再加一段")
            }

            // 照片
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("照片", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        photos.size.toString() + " / " + MAX_PHOTOS,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(photos) { i, p ->
                        Box {
                            AsyncImage(
                                model = PhotoStore.fileOf(ctx, p.fileName),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { fullPhoto = p.fileName }
                            )
                            IconButton(
                                onClick = {
                                    photos = photos.filter { it.id != p.id }
                                    PhotoStore.delete(ctx, p.fileName)
                                    scope.launch { persist() }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.45f),
                                        RoundedCornerShape(topEnd = 12.dp, bottomStart = 8.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Close, "移除",
                                    Modifier.size(13.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    if (photos.size < MAX_PHOTOS) {
                        item {
                            Box(
                                Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        LockState.beginExternalActivity()
                                        pickPhotos.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Add, "添加照片",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 标签
            SurfaceCard {
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("标签（用逗号分隔，可选）") },
                    placeholder = { Text("朋友, 加班, 读书") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            // 天气 / 位置（可选，手动填）
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("天气", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "手动填写，不联网",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = weatherOn, onCheckedChange = { weatherOn = it })
                }
                if (weatherOn) {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(WEATHERS.size) { i ->
                            val w = WEATHERS[i]
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (weather == w) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { weather = w }
                            ) {
                                Text(
                                    w,
                                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (weather == w)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("位置", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "手动填写，不定位",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = locationOn, onCheckedChange = { locationOn = it })
                }
                if (locationOn) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        placeholder = { Text("例如：杭州·西湖区") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // 日期选择
    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                LocalDate.parse(curDate).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        if (!d.isAfter(LocalDate.now())) curDate = d.toString()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state = state) }
    }

    // 大图查看
    fullPhoto?.let { name ->
        Dialog(onDismissRequest = { fullPhoto = null }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                    .clickable { fullPhoto = null }
                    .padding(8.dp)
            ) {
                AsyncImage(
                    model = PhotoStore.fileOf(ctx, name),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
