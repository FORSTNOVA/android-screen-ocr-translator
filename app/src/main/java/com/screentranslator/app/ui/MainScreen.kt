package com.screentranslator.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.screentranslator.app.engine.CloudConfig
import com.screentranslator.app.engine.EngineMode
import com.screentranslator.app.engine.LanguageOption

private val PageBackground = Color(0xFFF4F7FC)
private val CardBorderColor = Color(0xFFE2E8F0)
private val PrimaryBlue = Color(0xFF2563EB)
private val SecondaryIndigo = Color(0xFF4F46E5)
private val SuccessGreen = Color(0xFF16A34A)
private val WarningAmber = Color(0xFFD97706)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    isServiceRunning: Boolean,
    sourceLanguage: LanguageOption,
    targetLanguage: LanguageOption,
    cloudConfig: CloudConfig,
    availableModels: List<String>,
    isFetchingModels: Boolean,
    fetchModelsMessage: String?,
    isDownloadingModel: Boolean,
    modelStatusMessage: String?,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSourceLanguageChange: (LanguageOption) -> Unit,
    onTargetLanguageChange: (LanguageOption) -> Unit,
    onSwapLanguages: () -> Unit,
    onCloudConfigChange: (CloudConfig) -> Unit,
    onFetchModels: () -> Unit,
    onDownloadModels: () -> Unit,
    onToggleService: () -> Unit
) {
    val allPermissionsGranted = hasOverlayPermission && hasNotificationPermission

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = PageBackground
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(listOf(PrimaryBlue, SecondaryIndigo))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "屏幕悬浮翻译",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 10.dp,
                border = BorderStroke(0.5.dp, CardBorderColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onToggleService,
                        enabled = allPermissionsGranted,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = if (isServiceRunning) {
                            ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        } else {
                            ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        }
                    ) {
                        Icon(
                            imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                isServiceRunning -> "停止悬浮球翻译"
                                !allPermissionsGranted -> "请先授予上方基础权限"
                                else -> "开启悬浮球并授权录屏"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusBanner(
                isServiceRunning = isServiceRunning,
                allPermissionsGranted = allPermissionsGranted,
                engineMode = cloudConfig.mode
            )

            PermissionCard(
                hasOverlayPermission = hasOverlayPermission,
                hasNotificationPermission = hasNotificationPermission,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onRequestNotificationPermission = onRequestNotificationPermission
            )

            LanguageCard(
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                isOfflineMode = cloudConfig.mode == EngineMode.OFFLINE_MLKIT,
                isDownloadingModel = isDownloadingModel,
                modelStatusMessage = modelStatusMessage,
                onSourceLanguageChange = onSourceLanguageChange,
                onTargetLanguageChange = onTargetLanguageChange,
                onSwapLanguages = onSwapLanguages,
                onDownloadModels = onDownloadModels
            )

            EngineConfigCard(
                cloudConfig = cloudConfig,
                availableModels = availableModels,
                isFetchingModels = isFetchingModels,
                fetchModelsMessage = fetchModelsMessage,
                onCloudConfigChange = onCloudConfigChange,
                onFetchModels = onFetchModels
            )

            GuideCard()

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatusBanner(
    isServiceRunning: Boolean,
    allPermissionsGranted: Boolean,
    engineMode: EngineMode
) {
    val gradientColors = when {
        isServiceRunning -> listOf(Color(0xFF059669), Color(0xFF10B981))
        allPermissionsGranted -> listOf(PrimaryBlue, SecondaryIndigo)
        else -> listOf(Color(0xFF475569), Color(0xFF64748B))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradientColors))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isServiceRunning -> "悬浮球服务运行中"
                        allPermissionsGranted -> "准备就绪，可开启悬浮翻译"
                        else -> "还差一步：完成权限授权"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "当前引擎：${engineMode.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isServiceRunning) Color(0xFFBBF7D0) else Color.White.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun SectionCard(
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun PermissionCard(
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "基础权限",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
            )
            if (hasOverlayPermission && hasNotificationPermission) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SuccessGreen.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "全部已就绪",
                        style = MaterialTheme.typography.labelMedium,
                        color = SuccessGreen,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        PermissionRow(
            title = "悬浮窗权限",
            subtitle = "用于在其他应用上方显示悬浮球与译文",
            granted = hasOverlayPermission,
            onRequest = onRequestOverlayPermission
        )

        PermissionRow(
            title = "通知权限",
            subtitle = "用于维持前台截屏翻译服务稳定运行",
            granted = hasNotificationPermission,
            onRequest = onRequestNotificationPermission
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) SuccessGreen else WarningAmber,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1E293B)
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
        }
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onRequest,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("去授权")
            }
        }
    }
}

@Composable
private fun LanguageCard(
    sourceLanguage: LanguageOption,
    targetLanguage: LanguageOption,
    isOfflineMode: Boolean,
    isDownloadingModel: Boolean,
    modelStatusMessage: String?,
    onSourceLanguageChange: (LanguageOption) -> Unit,
    onTargetLanguageChange: (LanguageOption) -> Unit,
    onSwapLanguages: () -> Unit,
    onDownloadModels: () -> Unit
) {
    SectionCard {
        Text(
            "翻译语言",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F172A)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LanguageDropdown(
                label = "源语言",
                selected = sourceLanguage,
                onSelect = onSourceLanguageChange,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(
                onClick = onSwapLanguages,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "交换语言")
            }
            LanguageDropdown(
                label = "目标语言",
                selected = targetLanguage,
                onSelect = onTargetLanguageChange,
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedVisibility(visible = isOfflineMode) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDownloadModels,
                    enabled = !isDownloadingModel,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDownloadingModel) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在准备离线语言包...")
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("预下载 / 检查离线翻译模型")
                    }
                }

                if (!modelStatusMessage.isNullOrBlank()) {
                    Text(
                        text = modelStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageDropdown(
    label: String,
    selected: LanguageOption,
    onSelect: (LanguageOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, CardBorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        selected.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color(0xFF64748B)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LanguageOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineConfigCard(
    cloudConfig: CloudConfig,
    availableModels: List<String>,
    isFetchingModels: Boolean,
    fetchModelsMessage: String?,
    onCloudConfigChange: (CloudConfig) -> Unit,
    onFetchModels: () -> Unit
) {
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    SectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "识别与翻译引擎",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
            )
        }

        val modes = EngineMode.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = cloudConfig.mode == mode,
                    onClick = { onCloudConfigChange(cloudConfig.copy(mode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                ) {
                    Text(
                        text = if (mode == EngineMode.OFFLINE_MLKIT) "纯离线 ML Kit" else "云端大模型识图",
                        maxLines = 1
                    )
                }
            }
        }

        AnimatedVisibility(visible = cloudConfig.mode == EngineMode.CLOUD_VISION_LLM) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cloudConfig.baseUrl,
                    onValueChange = { onCloudConfigChange(cloudConfig.copy(baseUrl = it)) },
                    label = { Text("Base URL (OpenAI 兼容格式)") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cloudConfig.apiKey,
                    onValueChange = { onCloudConfigChange(cloudConfig.copy(apiKey = it)) },
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = cloudConfig.model,
                            onValueChange = { onCloudConfigChange(cloudConfig.copy(model = it)) },
                            label = { Text("模型名称 (自定义/下拉)") },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            trailingIcon = {
                                if (availableModels.isNotEmpty()) {
                                    IconButton(onClick = { modelDropdownExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "选择拉取的模型"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false }
                        ) {
                            availableModels.forEach { modelId ->
                                DropdownMenuItem(
                                    text = { Text(modelId) },
                                    onClick = {
                                        onCloudConfigChange(cloudConfig.copy(model = modelId))
                                        modelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = onFetchModels,
                        enabled = !isFetchingModels,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .height(54.dp)
                    ) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "拉取模型",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("拉取")
                        }
                    }
                }

                if (!fetchModelsMessage.isNullOrBlank()) {
                    Text(
                        text = fetchModelsMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEFF6FF),
        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "快速上手",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E3A8A)
            )
            Text("• 单击悬浮球“译”：翻译全屏（支持段落合并），轻点空白处关闭。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1E40AF))
            Text("• 长按悬浮球“译”：拖动框选局部区域翻译（适合字幕、漫画、游戏）。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1E40AF))
            Text("• 云端模式若遇仅支持纯文本的模型，会自动回退为本地 OCR + 云端翻译。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1E40AF))
        }
    }
}
