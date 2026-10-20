package com.ledgerdiary.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ledgerdiary.app.data.DiaryBundle
import com.ledgerdiary.app.data.DiaryEntryEntity
import com.ledgerdiary.app.ui.AppViewModel
import com.ledgerdiary.app.ui.components.EmptyHint
import com.ledgerdiary.app.ui.components.MoodDot
import com.ledgerdiary.app.ui.components.UndoBar
import com.ledgerdiary.app.util.DateUtils
import com.ledgerdiary.app.util.DiaryUtils
import com.ledgerdiary.app.util.PhotoStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 日记列表：按「天」分组，一天下面列出当天所有篇。
 * 点某一篇进编辑页改那一篇；点日期头的「再写一篇」给这天新加一篇。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListScreen(
    vm: AppViewModel,
    onOpenEntry: (String) -> Unit,
    onNewEntry: (String) -> Unit
) {
    val entries by vm.diaryEntries.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var previews by remember { mutableStateOf<Map<String, Pair<String?, String>>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    val days = remember(entries) { DiaryUtils.groupByDay(entries) }
    // 天内序号：按写入时间正序编号（和日历页「第 N 篇」一致），
    // 这样同一分钟写两篇也能分清谁是谁。
    val ordinals = remember(entries) {
        entries.groupBy { it.date }
            .flatMap { (_, list) ->
                list.sortedBy { it.createdAt }
                    .mapIndexed { i, e -> e.id to (i + 1) }
            }
            .toMap()
    }

    // 删除日记的 5 秒撤销：先把整篇（含段落与照片引用）读出来暂存，撤销时原样写回
    var pendingDelete by remember { mutableStateOf<DiaryBundle?>(null) }
    var undoVisible by remember { mutableStateOf(false) }
    var undoJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun deleteWithUndo(e: DiaryEntryEntity) {
        scope.launch {
            pendingDelete = vm.repo.loadDiaryById(e.id)
            vm.repo.deleteDiary(e.id)
            undoVisible = true
            undoJob?.cancel()
            undoJob = launch {
                delay(5000)
                undoVisible = false
                pendingDelete = null
            }
        }
    }

    // 列表页要显示每篇的第一段文字和第一张图，单独查一次
    LaunchedEffect(entries) {
        val map = mutableMapOf<String, Pair<String?, String>>()
        entries.forEach { e ->
            val segs = vm.db.diaryDao().segments(e.id)
            val photos = vm.db.diaryDao().photos(e.id)
            val text = segs.joinToString("\n") { it.text }.trim()
            map[e.id] = text to (photos.firstOrNull()?.fileName ?: "")
        }
        previews = map
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNewEntry(DateUtils.today()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("写日记", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "日记",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
            if (entries.isEmpty()) {
                item { EmptyHint("还没有日记\n点右下角开始写第一篇") }
            }
            days.forEach { day ->
                item(key = "day-" + day.date) {
                    DayHeader(
                        day = day,
                        onNew = { onNewEntry(day.date) }
                    )
                }
                items(day.entries, key = { it.id }) { e ->
                    val (rawText, photoName) = previews[e.id] ?: ("" to "")
                    DiaryCard(
                        entry = e,
                        ordinal = ordinals[e.id] ?: 1,
                        text = rawText ?: "",
                        photoFile = if (photoName.isBlank()) null else PhotoStore.fileOf(ctx, photoName),
                        onClick = { onOpenEntry(e.id) },
                        onLongClick = { deleteWithUndo(e) }
                    )
                }
            }
        }

        Box(
            Modifier.fillMaxSize().padding(bottom = 88.dp, start = 16.dp, end = 16.dp),
            Alignment.BottomCenter
        ) {
            UndoBar("已删除这篇日记", undoVisible, onUndo = {
                pendingDelete?.let { b ->
                    scope.launch {
                        vm.repo.saveDiary(b)
                        undoVisible = false
                        pendingDelete = null
                    }
                }
            })
        }
    }
}

/** 一天的标题行：日期 + 这天几篇 + 「再写一篇」。 */
@Composable
private fun DayHeader(day: DiaryUtils.DiaryDay, onNew: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                DateUtils.relativeLabel(day.date),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                DateUtils.friendlyDate(day.date).substringAfter(' ') +
                        if (day.count > 1) " · " + day.count + " 篇" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (day.mood > 0) {
            MoodDot(day.mood, 12)
            Spacer(Modifier.width(8.dp))
        }
        TextButton(onClick = onNew, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(2.dp))
            Text("再写一篇", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiaryCard(
    entry: DiaryEntryEntity,
    ordinal: Int,
    text: String,
    photoFile: File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 同一天多篇时，靠「第 N 篇 + 时间」区分是哪一篇
                Text(
                    "第 " + ordinal + " 篇",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    DateUtils.timeOf(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (entry.mood > 0) MoodDot(entry.mood, 12)
                entry.weather?.let {
                    if (it.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // 每篇日记自己的地址
            entry.location?.let { loc ->
                if (loc.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Place, null,
                            Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            loc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (photoFile != null) {
                Spacer(Modifier.height(10.dp))
                AsyncImage(
                    model = photoFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            if (entry.tags.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.tags.split(",").filter { it.isNotBlank() }.take(6).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "#" + tag.trim(),
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
