package love.panyi.forever.ui.schedule

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import love.panyi.forever.data.DAY_MINUTES
import love.panyi.forever.data.Schedule
import love.panyi.forever.data.TimeBlock
import love.panyi.forever.data.formatMinute
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE", Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loaded = state.day != null

    val listState = rememberLazyListState()
    LaunchedEffect(state.date, loaded) {
        if (!loaded) return@LaunchedEffect
        val index = if (state.isToday) state.timeline.indexOfFirst { state.nowMinute < it.end } else 8
        listState.scrollToItem(maxOf(index - 1, 0))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        state.date.format(dateFormatter),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                },
                navigationIcon = { TextButton(onClick = viewModel::previousDay) { Text("‹ 前一天") } },
                actions = {
                    if (!state.isToday) TextButton(onClick = viewModel::goToToday) { Text("今天") }
                    TextButton(onClick = viewModel::nextDay) { Text("后一天 ›") }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 当天还没有日程时，可一键填入平常日作息
                if (loaded && state.schedules.isEmpty()) {
                    ExtendedFloatingActionButton(onClick = viewModel::applyRoutine) { Text("📋 套用作息") }
                }
                FloatingActionButton(onClick = viewModel::newSchedule) {
                    Text("＋", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    ) { padding ->
        LazyColumn(state = listState, contentPadding = padding) {
            if (loaded) items(state.timeline, key = { it.start }) { block ->
                TimeRow(
                    block = block,
                    backup = state.backups[block.start],
                    isNow = state.isToday && state.nowMinute >= block.start && state.nowMinute < block.end,
                    onClick = {
                        when (block) {
                            is TimeBlock.Busy -> viewModel.editSchedule(block.schedule)
                            is TimeBlock.Free -> viewModel.editBackup(block)
                        }
                    }
                )
            }
        }
    }

    state.editingSchedule?.let { initial ->
        ScheduleDialog(
            initial = initial,
            error = state.scheduleError,
            onSave = viewModel::saveSchedule,
            onDelete = if (initial.id == 0L) null else viewModel::deleteSchedule,
            onDismiss = viewModel::dismissDialog
        )
    }

    state.editingBackup?.let { block ->
        BackupDialog(
            block = block,
            initial = state.backups[block.start].orEmpty(),
            onSave = viewModel::saveBackup,
            onDismiss = viewModel::dismissDialog
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
    error: String?,
    onSave: (Schedule) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial.title) }
    var start by remember { mutableIntStateOf(initial.start) }
    var end by remember { mutableIntStateOf(initial.end) }
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
            TextButton(onClick = { onSave(initial.copy(title = title.trim(), start = start, end = end)) }) { Text("保存") }
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
