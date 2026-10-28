package love.panyi.forever.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import love.panyi.forever.data.AiEngine
import love.panyi.forever.data.CloudConfig
import love.panyi.forever.data.Schedule
import love.panyi.forever.data.ScheduleTemplate
import love.panyi.forever.data.TimeBlock
import love.panyi.forever.data.buildTimeline

private const val MAX_INPUT = 2000

/** AI 导入的全部界面：输入面板、设置面板、预览、Snackbar 提示。 */
@Composable
fun ImportScreen(viewModel: ImportViewModel, snackbar: SnackbarHostState) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 输入的文字留在这一层：生成成功后面板关闭、重新生成或预览关闭后再打开时仍在
    var text by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    if (state.open) {
        ImportInputSheet(
            text = text,
            onTextChange = { text = it.take(MAX_INPUT) },
            engineLabel = if (state.ready) "云端 API · ${state.cloud.model.trim()}" else "未配置",
            ready = state.ready,
            generating = state.generating,
            error = state.error,
            onEngineClick = viewModel::openSettings,
            onGenerate = { viewModel.generate(text) },
            onCancelGenerate = viewModel::cancelGenerate,
            onDismiss = viewModel::close,
        )
    }

    if (state.showSettings) {
        ImportSettingsSheet(
            engine = state.engine,
            cloud = state.cloud,
            testing = state.testing,
            testResult = state.testResult,
            onTest = viewModel::testCloud,
            onSave = {
                viewModel.saveCloud(it)
                viewModel.closeSettings()
            },
            onDismiss = viewModel::closeSettings,
        )
    }

    state.preview?.let { groups ->
        ImportPreview(
            groups = groups,
            tab = state.tab,
            onSelectTab = viewModel::selectTab,
            onItemClick = viewModel::editItem,
            onRegenerate = viewModel::regenerate,
            onSaveTemplates = viewModel::saveTemplates,
            onClose = viewModel::closePreview,
        )
        state.editing?.let { initial ->
            ScheduleDialog(
                initial = initial,
                error = state.editError,
                onSave = viewModel::saveItem,
                onDelete = viewModel::deleteItem,
                onDismiss = viewModel::dismissEdit,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportInputSheet(
    text: String,
    onTextChange: (String) -> Unit,
    engineLabel: String,
    ready: Boolean,
    generating: Boolean,
    error: String?,
    onEngineClick: () -> Unit,
    onGenerate: () -> Unit,
    onCancelGenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("用一段话生成日程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !generating,
                minLines = 6,
                placeholder = { Text("例如：早上 7 点起床，8 点到 12 点上课，午饭后午休到 2 点……") },
                supportingText = {
                    Text("${text.length} / $MAX_INPUT", Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                },
            )
            Text(
                "AI 引擎：$engineLabel ›",
                Modifier
                    .clickable(onClick = onEngineClick)
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            if (generating) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("正在理解…", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onCancelGenerate) { Text("取消") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onGenerate, enabled = ready && text.isNotBlank()) { Text("生成") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSettingsSheet(
    engine: AiEngine,
    cloud: CloudConfig,
    testing: Boolean,
    testResult: String?,
    onTest: (CloudConfig) -> Unit,
    onSave: (CloudConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(cloud.url) }
    var apiKey by remember { mutableStateOf(cloud.apiKey) }
    var model by remember { mutableStateOf(cloud.model) }
    val config = CloudConfig(url, apiKey, model)
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("AI 引擎", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            DisabledEngineCard(AiEngine.NANO)
            DisabledEngineCard(AiEngine.GEMMA)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceContainerLow,
                border = BorderStroke(1.5.dp, if (engine == AiEngine.CLOUD) colors.primary else colors.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(AiEngine.CLOUD.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    val badUrl = url.isNotBlank() && !url.trim().startsWith("https://")
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("url") },
                        placeholder = { Text("https://api.deepseek.com/v1") },
                        singleLine = true,
                        isError = badUrl,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        supportingText = if (badUrl) ({ Text("必须以 https:// 开头") }) else null,
                    )
                    val badKey = apiKey.trim().any { it !in '!'..'~' }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key") },
                        singleLine = true,
                        isError = badKey,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = if (badKey) ({ Text("API Key 含有不可见或全角字符，请重新粘贴") }) else null,
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("模型") },
                        placeholder = { Text("deepseek-chat") },
                        singleLine = true,
                    )
                    if (testResult != null) {
                        Text(
                            testResult,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (testResult == "连接成功") colors.primary else colors.error,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { onTest(config) }, enabled = !testing) {
                            if (testing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("测试连接")
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { onSave(config) }) { Text("保存") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisabledEngineCard(engine: AiEngine) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(engine.label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface.copy(alpha = 0.38f))
            Text("即将支持", style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(alpha = 0.38f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportPreview(
    groups: List<ScheduleTemplate>,
    tab: Int,
    onSelectTab: (Int) -> Unit,
    onItemClick: (Schedule) -> Unit,
    onRegenerate: () -> Unit,
    onSaveTemplates: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val group = groups.getOrNull(tab) ?: return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.surface) {
            Box(contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "预览生成结果",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        TextButton(onClick = onClose) { Text("关闭") }
                    }
                    if (groups.size > 1) {
                        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
                            groups.forEachIndexed { i, g ->
                                Tab(selected = i == tab, onClick = { onSelectTab(i) }, text = { Text(g.name) })
                            }
                        }
                    }
                    Surface(
                        Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.tertiaryContainer,
                        contentColor = colors.onTertiaryContainer,
                    ) {
                        Text("AI 生成，请检查。点某一条可以修改。", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    // 只显示第一条到最后一条之间的块，首尾的大段空闲不展示
                    val blocks = remember(group) {
                        val first = group.items.minOfOrNull { it.start } ?: 0
                        val last = group.items.maxOfOrNull { it.end } ?: 0
                        buildTimeline(group.items).filter { it.start >= first && it.end <= last }
                    }
                    key(tab) {
                        LazyColumn(Modifier.weight(1f)) {
                            items(blocks, key = { "b${it.start}" }) { block ->
                                TimeRow(
                                    block = block,
                                    backup = null,
                                    done = false,
                                    record = null,
                                    readOnly = true,
                                    onClick = { if (block is TimeBlock.Busy) onItemClick(block.schedule) },
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = onRegenerate, modifier = Modifier.weight(1f)) { Text("重新生成") }
                        Button(onClick = onSaveTemplates, modifier = Modifier.weight(1f)) { Text("存为模板") }
                    }
                }
            }
        }
    }
}

/** 日期栏上的 ✨ 入口。 */
@Composable
internal fun ImportEntryButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Text("✨", Modifier.clearAndSetSemantics { contentDescription = "AI 导入" }, style = MaterialTheme.typography.titleMedium)
    }
}
