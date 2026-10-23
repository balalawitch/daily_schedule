package love.panyi.forever.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import love.panyi.forever.data.Completion
import love.panyi.forever.data.DAY_MINUTES
import love.panyi.forever.data.Schedule
import love.panyi.forever.data.ScheduleRecord
import love.panyi.forever.data.TimeBlock
import love.panyi.forever.data.formatMinute
import love.panyi.forever.data.splitTitle

@Composable
internal fun ScheduleDialog(
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
internal fun BackupDialog(block: TimeBlock.Free, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
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

/** 补记一条已完成日程：实际时间、完成情况、记一笔。没有记录时按计划预填。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RecordSheet(
    schedule: Schedule,
    record: ScheduleRecord?,
    onSave: (ScheduleRecord) -> Unit,
    onReopen: () -> Unit,
    onEditPlan: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val initial = record?.takeIf { it.status != Completion.UNDONE }
        ?: ScheduleRecord(Completion.ON_TIME, schedule.start, schedule.end)
    var start by rememberSaveable { mutableIntStateOf(initial.actualStart) }
    var end by rememberSaveable { mutableIntStateOf(initial.actualEnd) }
    var status by rememberSaveable { mutableStateOf(initial.status) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var picking by remember { mutableStateOf<String?>(null) } // "start" / "end"
    val parts = splitTitle(schedule.title)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(parts.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(50), color = colors.surfaceContainerHigh, contentColor = colors.onSurfaceVariant) {
                        Text("已完成", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(
                    listOfNotNull("计划 ${formatMinute(schedule.start)}–${formatMinute(schedule.end)}", parts.hint).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("实际时间", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeTile("开始", start, Modifier.weight(1f)) { picking = "start" }
                    Text(
                        if (end > start) duration(end - start) else "",
                        Modifier.width(72.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TimeTile("结束", end, Modifier.weight(1f)) { picking = "end" }
                }
                val diff = (end - start) - (schedule.end - schedule.start)
                when {
                    end <= start -> Text("结束时间需晚于开始时间", style = MaterialTheme.typography.bodySmall, color = colors.error)
                    diff > 0 -> Text("比计划多 ${duration(diff)}", style = MaterialTheme.typography.bodySmall, color = colors.tertiary)
                    diff < 0 -> Text("比计划少 ${duration(-diff)}", style = MaterialTheme.typography.bodySmall, color = colors.tertiary)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("完成情况", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in Completion.entries - Completion.UNDONE) {
                        // 按时、提前用蓝色；超时、没做用杏橙提醒
                        val warn = option == Completion.OVERTIME || option == Completion.SKIPPED
                        FilterChip(
                            selected = status == option,
                            onClick = { status = option },
                            label = { Text(option.label) },
                            leadingIcon = if (status == option) {
                                { Icon(LineIcons.Check, null, Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (warn) colors.tertiaryContainer else colors.primaryContainer,
                                selectedLabelColor = if (warn) colors.onTertiaryContainer else colors.onPrimaryContainer,
                                selectedLeadingIconColor = if (warn) colors.onTertiaryContainer else colors.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("记一笔") },
                placeholder = { Text("吃了多少、情绪怎样、明天要注意什么") },
                minLines = 3,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onReopen) { Text("改回未完成") }
                TextButton(onClick = onEditPlan) { Text("修改计划") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(ScheduleRecord(status, start, end, note)) }, enabled = end > start) { Text("保存") }
            }
        }
    }

    picking?.let { which ->
        TimePickDialog(
            minute = if (which == "start") start else end,
            onPick = {
                if (which == "start") start = it
                else end = if (it == 0) DAY_MINUTES else it
                picking = null
            },
            onDismiss = { picking = null }
        )
    }
}

@Composable
private fun TimeTile(label: String, minute: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(formatMinute(minute), style = MaterialTheme.typography.headlineSmall)
        }
    }
}
