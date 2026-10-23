package love.panyi.forever

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE", Locale.CHINA)

private fun currentMinute() = LocalTime.now().let { it.hour * 60 + it.minute }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(store: ScheduleStore) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var schedules by remember(date) { mutableStateOf(store.schedules(date)) }
    var backups by remember(date) { mutableStateOf(store.backups(date)) }
    val timeline = remember(schedules) { buildTimeline(schedules) }

    var nowMinute by remember { mutableIntStateOf(currentMinute()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMinute = currentMinute()
        }
    }
    val isToday = date == LocalDate.now()

    // 正在编辑的日程（id == 0 表示新建）与正在编辑后备活动的空闲块
    var editingSchedule by remember { mutableStateOf<Schedule?>(null) }
    var editingBackup by remember { mutableStateOf<TimeBlock.Free?>(null) }

    val listState = rememberLazyListState()
    LaunchedEffect(date) {
        val index = if (isToday) timeline.indexOfFirst { nowMinute < it.end } else 8
        listState.scrollToItem(maxOf(index - 1, 0))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        date.format(dateFormatter),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                },
                navigationIcon = { TextButton(onClick = { date = date.minusDays(1) }) { Text("‹ 前一天") } },
                actions = {
                    if (!isToday) TextButton(onClick = { date = LocalDate.now() }) { Text("今天") }
                    TextButton(onClick = { date = date.plusDays(1) }) { Text("后一天 ›") }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 当天还没有日程时，可一键填入平常日作息
                if (schedules.isEmpty()) {
                    ExtendedFloatingActionButton(onClick = {
                        schedules = weekdayRoutine()
                        store.saveSchedules(date, schedules)
                    }) { Text("📋 套用作息") }
                }
                FloatingActionButton(onClick = {
                    val start = if (isToday) nowMinute / 60 * 60 else 9 * 60
                    editingSchedule = Schedule(0, start, minOf(start + 60, DAY_MINUTES), "")
                }) { Text("＋", style = MaterialTheme.typography.headlineSmall) }
            }
        }
    ) { padding ->
        LazyColumn(state = listState, contentPadding = padding) {
            items(timeline, key = { it.start }) { block ->
                TimeRow(
                    block = block,
                    backup = backups[block.start],
                    isNow = isToday && nowMinute >= block.start && nowMinute < block.end,
                    onClick = {
                        when (block) {
                            is TimeBlock.Busy -> editingSchedule = block.schedule
                            is TimeBlock.Free -> editingBackup = block
                        }
                    }
                )
            }
        }
    }

    editingSchedule?.let { initial ->
        ScheduleDialog(
            initial = initial,
            validate = { s ->
                when {
                    s.title.isBlank() -> "请填写标题"
                    s.end <= s.start -> "结束时间需晚于开始时间"
                    hasOverlap(schedules, s.start, s.end, ignoreId = s.id) -> "与已有日程时间重叠"
                    else -> null
                }
            },
            onSave = { s ->
                val saved = if (s.id == 0L) s.copy(id = System.currentTimeMillis()) else s
                schedules = schedules.filter { it.id != saved.id } + saved
                store.saveSchedules(date, schedules)
                editingSchedule = null
            },
            onDelete = if (initial.id == 0L) null else {
                {
                    schedules = schedules.filter { it.id != initial.id }
                    store.saveSchedules(date, schedules)
                    editingSchedule = null
                }
            },
            onDismiss = { editingSchedule = null }
        )
    }

    editingBackup?.let { block ->
        BackupDialog(
            block = block,
            initial = backups[block.start].orEmpty(),
            onSave = { text ->
                backups = if (text.isBlank()) backups - block.start else backups + (block.start to text.trim())
                store.saveBackups(date, backups)
                editingBackup = null
            },
            onDismiss = { editingBackup = null }
        )
    }
}

@Composable
private fun TimeRow(block: TimeBlock, backup: String?, isNow: Boolean, onClick: () -> Unit) {
    val minutes = block.end - block.start
    val height = maxOf(72f, minutes * 72f / 60).dp
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        // 左侧：时间段起点（区块首尾相接，下一块的起点即本块终点）
        Text(
            formatMinute(block.start),
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
            color = if (isNow) colors.primary else colors.onSurface
        )
        // 右侧：日程 / 后备活动
        val (container, content, label) = when {
            block is TimeBlock.Busy -> Triple(colors.primaryContainer, colors.onPrimaryContainer, "📌 ${block.schedule.title}")
            backup != null -> Triple(colors.tertiaryContainer, colors.onTertiaryContainer, "🌱 $backup")
            else -> Triple(colors.surfaceVariant.copy(alpha = 0.4f), colors.outline, "＋ 后备活动")
        }
        Surface(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = MaterialTheme.shapes.medium,
            color = container,
            contentColor = content,
            border = if (isNow) BorderStroke(2.dp, colors.primary) else null
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Column(horizontalAlignment = Alignment.End) {
                    if (block is TimeBlock.Busy) {
                        Text(
                            "${formatMinute(block.start)}–${formatMinute(block.end)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (isNow) Text("现在", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
            }
        }
    }
}

@Composable
private fun ScheduleDialog(
    initial: Schedule,
    validate: (Schedule) -> String?,
    onSave: (Schedule) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial.title) }
    var start by remember { mutableIntStateOf(initial.start) }
    var end by remember { mutableIntStateOf(initial.end) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<String?>(null) } // "start" / "end"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "添加日程" else "编辑日程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picking = "start" }) { Text("开始 ${formatMinute(start)}") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { picking = "end" }) { Text("结束 ${formatMinute(end)}") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = initial.copy(title = title.trim(), start = start, end = end)
                error = validate(s)
                if (error == null) onSave(s)
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("删除", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )

    picking?.let { which ->
        TimePickDialog(
            minute = if (which == "start") start else end,
            onPick = {
                if (which == "start") start = it
                // 结束时间选 00:00 视为当天 24:00
                else end = if (it == 0) DAY_MINUTES else it
                picking = null
            },
            onDismiss = { picking = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(minute: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = minute / 60 % 24, initialMinute = minute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun BackupDialog(block: TimeBlock.Free, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("后备活动 ${formatMinute(block.start)}–${formatMinute(block.end)}") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("如：阅读、运动") })
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("保存") } },
        dismissButton = {
            Row {
                if (initial.isNotEmpty()) TextButton(onClick = { onSave("") }) { Text("清空") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
