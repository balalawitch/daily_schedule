package love.panyi.forever.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import love.panyi.forever.data.Completion
import love.panyi.forever.data.DayProgress
import love.panyi.forever.data.Schedule
import love.panyi.forever.data.ScheduleRecord
import love.panyi.forever.data.TimeBlock
import love.panyi.forever.data.formatMinute
import love.panyi.forever.data.splitTitle
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.CHINA)
private val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loaded = state.day != null
    // 今天且有日程时才有此刻卡
    val progress = state.progress?.takeIf { state.schedules.isNotEmpty() }
    var pickingDate by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 停在此刻时展开；一拖动列表就缩回时间轴里的一行
    var expanded by remember(state.date) { mutableStateOf(true) }

    // 今天的时间轴：已过去的日程（过去的空档不显示）→ 此刻 → 后面全部
    val now = state.nowMinute
    val nowBlock = state.timeline.firstOrNull { now in it.start until it.end }
    val before = if (progress == null) emptyList() else state.timeline.filter {
        it is TimeBlock.Busy && (it.end <= now || (it == nowBlock && state.isDone(it.schedule)))
    }
    val after = if (progress == null) emptyList() else state.timeline.filter { it.start > now }
    val current = progress?.current
    val freeBlock = (nowBlock as? TimeBlock.Free)?.takeIf { current == null }
    val next = progress?.upcoming?.firstOrNull { it.start >= now }
    val showHint = expanded && before.isNotEmpty()
    // 手机横屏等高度不足 480dp 时，日期栏放进列表跟着滚走，不再固定占位
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val lead = if (compactHeight) 1 else 0
    val nowIndex = lead + before.size + if (showHint) 1 else 0

    val anchorIndex = when {
        progress != null -> lead + before.size
        state.schedules.isEmpty() -> 0
        else -> lead + state.timeline.indexOfFirst { it is TimeBlock.Busy }
    }
    LaunchedEffect(state.date, loaded) {
        if (loaded) listState.scrollToItem(anchorIndex)
    }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) expanded = false }
    }
    // 停下时此刻在屏幕上部附近，就重新展开
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) return@collect
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == "now" } ?: return@collect
            if (item.offset in -item.size / 2..(info.viewportSize.height * 0.35f).toInt()) expanded = true
        }
    }
    val backToNow: () -> Unit = {
        expanded = true
        scope.launch { listState.animateScrollToItem(lead + before.size) }
    }

    // 此刻那一行滚出屏幕后，在它离开的一侧浮出小卡
    val floatAlignment by remember(progress != null, nowIndex) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            when {
                progress == null || visible.isEmpty() || visible.any { it.key == "now" } -> null
                visible.first().index > nowIndex -> Alignment.TopCenter
                else -> Alignment.BottomCenter
            }
        }
    }

    val openBlock: (TimeBlock) -> Unit = { block ->
        when {
            block is TimeBlock.Free -> viewModel.editBackup(block)
            state.isDone((block as TimeBlock.Busy).schedule) -> viewModel.editRecord(block.schedule)
            else -> viewModel.editSchedule(block.schedule)
        }
    }
    val row: @Composable (TimeBlock) -> Unit = { block ->
        val s = (block as? TimeBlock.Busy)?.schedule
        TimeRow(
            block = block,
            backup = state.backups[block.start],
            done = s != null && state.isDone(s),
            record = s?.let { state.records[it.id] },
            onClick = { openBlock(block) },
        )
    }

    val nowTitle = current?.let { splitTitle(it.title).name } ?: freeBlock?.let { state.backups[it.start] } ?: "空闲时间"
    val nextText = next?.let { "${fromNow(it.start, now)}：${splitTitle(it.title).name}" } ?: "今天的安排都做完了"
    val fraction = current?.let { (now - it.start).toFloat() / (it.end - it.start) }
    val header: @Composable () -> Unit = {
        WeekHeader(
            date = state.date,
            today = state.today,
            onSelect = viewModel::selectDate,
            onToday = viewModel::goToToday,
            onPickDate = { pickingDate = true },
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::newSchedule,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(LineIcons.Plus, contentDescription = "添加日程") }
        }
    ) { padding ->
        // 横屏、平板上内容限宽 600dp 居中，避免拉满整屏、被右下角新建按钮压住
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 600.dp)) {
                if (!compactHeight) header()
                Box(Modifier.weight(1f)) {
                    LazyColumn(state = listState, contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)) {
                        if (compactHeight) item("header") { header() }
                        if (!loaded) return@LazyColumn
                        if (state.schedules.isEmpty()) item("empty") { EmptyDay(onApply = viewModel::applyRoutine) }

                        if (progress == null) {
                            items(state.timeline, key = { "b${it.start}" }) { row(it) }
                            return@LazyColumn
                        }

                        items(before, key = { "b${it.start}" }) { row(it) }
                        if (showHint) item("hint") {
                            DoneHint(progress.done.size, onClick = { scope.launch { listState.animateScrollToItem(0) } })
                        }
                        item("now") {
                            if (expanded) {
                                NowCard(
                                    progress = progress,
                                    nowMinute = now,
                                    freeBlock = freeBlock,
                                    backups = state.backups,
                                    onCompleteEarly = viewModel::completeEarly,
                                    onEditBackup = viewModel::editBackup,
                                    onShowDone = {
                                        expanded = false
                                        scope.launch { listState.animateScrollToItem(0) }
                                    },
                                    onShowAll = {
                                        expanded = false
                                        scope.launch { listState.animateScrollToItem(lead + before.size + 1) }
                                    },
                                )
                            } else {
                                NowInlineRow(
                                    start = current?.start ?: nowBlock?.start ?: now,
                                    nowMinute = now,
                                    title = nowTitle,
                                    subtitle = "此刻 · " + (current?.let { "还剩 ${duration(it.end - now)}" } ?: nextText),
                                    length = current?.let { duration(it.end - it.start) },
                                    minutes = current?.let { it.end - it.start } ?: 0,
                                    fraction = fraction,
                                    onClick = backToNow,
                                )
                            }
                        }
                        items(after, key = { "b${it.start}" }) { row(it) }
                    }

                    floatAlignment?.let { alignment ->
                        val atTop = alignment == Alignment.TopCenter
                        // 浮在顶部时带一条页面底色，盖住后面被截断的列表；浮在底部时让出右下角的新建按钮（16 + 56 + 12）
                        Box(
                            Modifier
                                .align(alignment)
                                .then(if (atTop) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
                                .padding(start = 16.dp, end = 16.dp, top = if (atTop) 4.dp else 0.dp, bottom = if (atTop) 8.dp else 84.dp)
                        ) {
                            FloatingNow(
                                title = nowTitle,
                                nowMinute = now,
                                subtitle = (current?.let { "还剩 ${duration(it.end - now)}" } ?: nextText) + " · 点按回到此刻",
                                fraction = fraction,
                                pointsUp = atTop,
                                action = when {
                                    current != null -> Triple(LineIcons.Check, "提前完成") { viewModel.completeEarly(current) }
                                    freeBlock != null -> Triple(LineIcons.Plus, "后备活动") { viewModel.editBackup(freeBlock) }
                                    else -> null
                                },
                                onClick = backToNow,
                                modifier = Modifier,
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.selectDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickingDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("取消") } },
        ) { DatePicker(pickerState) }
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

    state.editingRecord?.let { s ->
        RecordSheet(
            schedule = s,
            record = state.records[s.id],
            onSave = viewModel::saveRecord,
            onReopen = viewModel::reopenRecord,
            onEditPlan = { viewModel.editSchedule(s) },
            onDismiss = viewModel::dismissDialog,
        )
    }
}

/** 时长：35 分钟 / 1 小时 / 7 小时 35 分钟。 */
internal fun duration(minutes: Int) = when {
    minutes < 60 -> "$minutes 分钟"
    minutes % 60 == 0 -> "${minutes / 60} 小时"
    else -> "${minutes / 60} 小时 ${minutes % 60} 分钟"
}

/** 距现在多久：35 分钟后 / 1.5 小时后。 */
private fun fromNow(start: Int, now: Int): String {
    val d = start - now
    return when {
        d <= 0 -> "已到时间"
        d < 60 -> "$d 分钟后"
        d % 60 == 0 -> "${d / 60} 小时后"
        d % 60 == 30 -> "${d / 60}.5 小时后"
        else -> "${d / 60} 小时 ${d % 60} 分后"
    }
}

@Composable
private fun WeekHeader(
    date: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onPickDate: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(date.format(dateFormatter), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(10.dp))
            Text(date.format(weekdayFormatter), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            if (date == today) Tag("今天", colors.primaryContainer, colors.onPrimaryContainer)
            Spacer(Modifier.weight(1f))
            if (date != today) {
                OutlinedButton(onClick = onToday, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("回到今天") }
            }
            IconButton(onClick = onPickDate) {
                Icon(LineIcons.Calendar, contentDescription = "选择日期", tint = colors.onSurfaceVariant)
            }
        }
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0..6) {
                val day = monday.plusDays(i.toLong())
                val selected = day == date
                Surface(
                    onClick = { onSelect(day) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) colors.primary else colors.surface,
                    contentColor = when {
                        selected -> colors.onPrimary
                        day == today -> colors.primary
                        else -> colors.onSurfaceVariant
                    },
                    border = if (day == today && !selected) BorderStroke(1.5.dp, colors.primary) else null,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(weekLabels[i], style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${day.dayOfMonth}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, container: Color, content: Color) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = content,
    )
}

@Composable
private fun EmptyDay(onApply: () -> Unit) {
    Surface(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("这天还没有日程", style = MaterialTheme.typography.titleMedium)
                Text(
                    "先套用平常日作息，再按需要改。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onApply) { Text("套用作息") }
        }
    }
}

@Composable
private fun DoneHint(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Icon(LineIcons.ChevronUp, contentDescription = null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text("上划查看已完成的 $count 项", style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun NowCard(
    progress: DayProgress,
    nowMinute: Int,
    freeBlock: TimeBlock.Free?,
    backups: Map<Int, String>,
    onCompleteEarly: (Schedule) -> Unit,
    onEditBackup: (TimeBlock.Free) -> Unit,
    onShowDone: () -> Unit,
    onShowAll: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onPrimary.copy(alpha = 0.82f)
    val current = progress.current
    val next = progress.upcoming.firstOrNull { it.start >= nowMinute }
    Surface(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = colors.primary,
        contentColor = colors.onPrimary,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.onPrimary)
                )
                Spacer(Modifier.width(8.dp))
                Text("此刻 ${formatMinute(nowMinute)}", style = MaterialTheme.typography.bodyMedium, color = muted)
                Spacer(Modifier.weight(1f))
                current?.let {
                    Text("${formatMinute(it.start)}–${formatMinute(it.end)}", style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }

            if (current != null) {
                val parts = splitTitle(current.title)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(parts.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    parts.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                }
                val fraction = (nowMinute - current.start).toFloat() / (current.end - current.start)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.onPrimary.copy(alpha = 0.3f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(colors.onPrimary)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "还剩 ${duration(current.end - nowMinute)}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Button(
                        onClick = { onCompleteEarly(current) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.onPrimary, contentColor = colors.primary),
                    ) { Text("提前完成") }
                }
            } else {
                val backup = freeBlock?.let { backups[it.start] }
                Text(backup ?: "空闲时间", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        next?.let { "${fromNow(it.start, nowMinute)}：${splitTitle(it.title).name}" } ?: "今天的安排都做完了",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                    )
                    freeBlock?.let { block ->
                        Button(
                            onClick = { onEditBackup(block) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.onPrimary, contentColor = colors.primary),
                        ) { Text(if (backup == null) "＋ 后备活动" else "改后备活动") }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .drawBehind {
                        drawRoundRect(muted.copy(alpha = 0.5f), cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.dp.toPx()))
                    }
            ) {
                StatCell("已完成", progress.done.size, muted, Modifier.weight(1f), onShowDone)
                StatCell("进行中", if (current != null) 1 else 0, muted, Modifier.weight(1f), null)
                StatCell("还剩", progress.upcoming.size, muted, Modifier.weight(1f), onShowAll)
            }
        }
    }
}

@Composable
private fun StatCell(label: String, count: Int, muted: Color, modifier: Modifier, onClick: (() -> Unit)?) {
    Column(
        modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(" 项", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/** 日程块高度：40 + 分钟数 × 1.4，限制在 48–180dp。 */
private fun blockHeight(minutes: Int) = (40 + minutes * 1.4f).coerceIn(48f, 180f).dp

@Composable
private fun ProgressLine(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.3f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

/** 离开此刻后，此刻卡缩回时间轴的样子：仍是卡片蓝底，时间列多一个现在时间。 */
@Composable
private fun NowInlineRow(
    start: Int,
    nowMinute: Int,
    title: String,
    subtitle: String,
    length: String?,
    minutes: Int,
    fraction: Float?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onPrimary.copy(alpha = 0.82f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
    ) {
        Column(
            Modifier
                .width(48.dp)
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(formatMinute(start), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                formatMinute(nowMinute),
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.primary)
                    .padding(horizontal = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimary,
            )
        }
        Surface(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = blockHeight(minutes)),
            shape = RoundedCornerShape(12.dp),
            color = colors.primary,
            contentColor = colors.onPrimary,
        ) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    length?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = muted)
                fraction?.let {
                    Spacer(Modifier.height(4.dp))
                    ProgressLine(it, colors.onPrimary)
                }
            }
        }
    }
}

/** 此刻那一行滚出屏幕后浮在顶部或底部的小卡；箭头指向此刻所在的方向。 */
@Composable
private fun FloatingNow(
    title: String,
    nowMinute: Int,
    subtitle: String,
    fraction: Float?,
    pointsUp: Boolean,
    action: Triple<ImageVector, String, () -> Unit>?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onPrimary.copy(alpha = 0.82f)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp),
        shape = RoundedCornerShape(20.dp),
        color = colors.primary,
        contentColor = colors.onPrimary,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (fraction ?: 0f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(40.dp),
                    color = colors.onPrimary,
                    trackColor = colors.onPrimary.copy(alpha = 0.3f),
                    strokeWidth = 4.dp,
                )
                Icon(if (pointsUp) LineIcons.ChevronUp else LineIcons.ChevronDown, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        title,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("此刻 ${formatMinute(nowMinute)}", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            action?.let { (icon, label, onAction) ->
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onAction,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(50),
                    color = colors.onPrimary.copy(alpha = 0.16f),
                    contentColor = colors.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, Modifier.size(20.dp)) }
                }
            }
        }
    }
}

/** 行内的操作提示，样式像文字按钮；点击由整行处理，读屏时只有一个焦点。 */
@Composable
private fun ActionHint(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 事项名；需要掐点的在后面加「掐点」标签。 */
@Composable
private fun TitleLine(name: String, timed: Boolean, weight: FontWeight) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
        if (timed) {
            Spacer(Modifier.width(6.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = MaterialTheme.colorScheme.onTertiaryContainer
                Icon(LineIcons.Clock, null, Modifier.size(11.dp), tint = color)
                Spacer(Modifier.width(3.dp))
                Text("掐点", style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
private fun TimeRow(block: TimeBlock, backup: String?, done: Boolean, record: ScheduleRecord?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val minutes = block.end - block.start
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
    ) {
        Text(
            formatMinute(block.start),
            Modifier
                .width(48.dp)
                .padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        val shape = RoundedCornerShape(12.dp)
        val blockModifier = Modifier.weight(1f)
        when {
            block is TimeBlock.Busy && done -> DoneBlock(block.schedule, record, minutes, onClick, blockModifier)

            block is TimeBlock.Busy -> {
                val parts = splitTitle(block.schedule.title)
                Surface(
                    onClick = onClick,
                    modifier = blockModifier.heightIn(min = blockHeight(minutes)),
                    shape = shape,
                    color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Column(Modifier.weight(1f)) {
                            TitleLine(parts.name, parts.timed, FontWeight.Medium)
                            parts.hint?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(duration(minutes), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            backup != null -> Surface(
                onClick = onClick,
                modifier = blockModifier.heightIn(min = 44.dp),
                shape = shape,
                color = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("后备 · $backup", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(duration(minutes), style = MaterialTheme.typography.bodySmall)
                }
            }

            else -> Row(
                blockModifier
                    .height(44.dp)
                    .clip(shape)
                    .drawBehind {
                        drawRoundRect(
                            colors.outlineVariant,
                            cornerRadius = CornerRadius(12.dp.toPx()),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                    .clickable(onClick = onClick)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "空档 ${duration(minutes)}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                ActionHint("＋ 后备活动")
            }
        }
    }
}

@Composable
private fun DoneBlock(s: Schedule, record: ScheduleRecord?, minutes: Int, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val parts = splitTitle(s.title)
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurfaceVariant,
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(LineIcons.Check, contentDescription = "已完成", Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        parts.name,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    record?.status?.takeIf { it == Completion.OVERTIME || it == Completion.SKIPPED }?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            it.label,
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.tertiaryContainer)
                                .padding(horizontal = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onTertiaryContainer,
                        )
                    }
                }
                record?.note?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        it,
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceContainerLowest)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                }
            }
            if (record?.note.isNullOrEmpty()) {
                ActionHint("＋ 记一笔")
            } else {
                Text(duration(minutes), Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
