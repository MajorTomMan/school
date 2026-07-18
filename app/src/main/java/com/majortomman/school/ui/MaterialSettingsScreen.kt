package com.majortomman.school.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.BuildConfig
import com.majortomman.school.ai.OpenAiCompatibleClient
import com.majortomman.school.data.AiSettings
import com.majortomman.school.network.AppProxy
import com.majortomman.school.network.AppProxySettings
import com.majortomman.school.update.UpdateCoordinator
import com.majortomman.school.update.UpdateState
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private val SettingsBlack = Color(0xFF050608)
private val SettingsWhite = Color(0xFFF5F7FA)
private val SettingsBlue = Color(0xFF2D7BFF)
private val SettingsRed = Color(0xFFFF453A)
private val SettingsYellow = Color(0xFFFFCC00)
private val SettingsMuted = SettingsWhite.copy(alpha = 0.46f)
private val SettingsLine = SettingsWhite.copy(alpha = 0.13f)

private enum class SettingsPage(val label: String) {
    PROXY("代理"),
    AI("AI"),
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun MaterialSettingsScreen(
    settings: AiSettings,
    onSave: (AiSettings) -> Unit,
    onOpenSubjects: () -> Unit,
    onClearProgress: () -> Unit,
) {
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.PROXY.name) }
    var endpoint by rememberSaveable { mutableStateOf(settings.endpoint) }
    var model by rememberSaveable { mutableStateOf(settings.model) }
    var apiKey by rememberSaveable { mutableStateOf(settings.apiKey) }
    var connectionStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var isTesting by rememberSaveable { mutableStateOf(false) }
    var confirmClearProgress by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val updateCoordinator = remember(appContext) { UpdateCoordinator.get(appContext) }
    val updateState by updateCoordinator.state.collectAsState()
    val updateSettings by updateCoordinator.settings.collectAsState()
    val proxySettings by AppProxy.settings.collectAsState()
    var proxyUrl by rememberSaveable { mutableStateOf(proxySettings.proxyUrl) }
    var useForUpdates by rememberSaveable { mutableStateOf(proxySettings.useForUpdates) }
    var useForAi by rememberSaveable { mutableStateOf(proxySettings.useForAi) }
    var proxyStatus by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(settings) {
        endpoint = settings.endpoint
        model = settings.model
        apiKey = settings.apiKey
    }
    LaunchedEffect(proxySettings) {
        proxyUrl = proxySettings.proxyUrl
        useForUpdates = proxySettings.useForUpdates
        useForAi = proxySettings.useForAi
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 30.dp),
    ) {
        Text("设置", color = SettingsWhite, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        SettingsPageSelector(
            selected = SettingsPage.valueOf(pageName),
            onSelect = { pageName = it.name },
        )
        Spacer(Modifier.height(38.dp))

        AnimatedContent(
            targetState = SettingsPage.valueOf(pageName),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "settingsPages",
        ) { page ->
            when (page) {
                SettingsPage.PROXY -> ProxySettingsPage(
                    proxyUrl = proxyUrl,
                    onProxyUrlChange = {
                        proxyUrl = it
                        proxyStatus = null
                    },
                    useForUpdates = useForUpdates,
                    onToggleUpdates = {
                        useForUpdates = !useForUpdates
                        proxyStatus = null
                    },
                    useForAi = useForAi,
                    onToggleAi = {
                        useForAi = !useForAi
                        proxyStatus = null
                    },
                    proxyStatus = proxyStatus,
                    onSaveProxy = {
                        runCatching {
                            AppProxy.save(
                                appContext,
                                AppProxySettings(
                                    proxyUrl = proxyUrl,
                                    useForUpdates = useForUpdates,
                                    useForAi = useForAi,
                                ),
                            )
                        }.fold(
                            onSuccess = { proxyStatus = "代理设置已保存。" },
                            onFailure = { proxyStatus = "保存失败：${it.message ?: "代理地址无效"}" },
                        )
                    },
                    updateState = updateState,
                    autoCheck = updateSettings.autoCheck,
                    wifiOnly = updateSettings.wifiOnly,
                    lastCheckedAt = updateSettings.lastCheckedAt,
                    onToggleAutoCheck = { updateCoordinator.setAutoCheck(!updateSettings.autoCheck) },
                    onToggleWifiOnly = { updateCoordinator.setWifiOnly(!updateSettings.wifiOnly) },
                    onCheckUpdate = { updateCoordinator.checkNow(force = true) },
                    onShowUpdateStatus = updateCoordinator::showDialog,
                )

                SettingsPage.AI -> AiSettingsPage(
                    endpoint = endpoint,
                    onEndpointChange = {
                        endpoint = it
                        connectionStatus = null
                    },
                    model = model,
                    onModelChange = {
                        model = it
                        connectionStatus = null
                    },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    connectionStatus = connectionStatus,
                    isTesting = isTesting,
                    aiUsesProxy = proxySettings.useForAi,
                    onTest = {
                        isTesting = true
                        connectionStatus = "正在连接…"
                        val updated = AiSettings(endpoint.trim(), model.trim(), apiKey.trim())
                        scope.launch {
                            connectionStatus = OpenAiCompatibleClient(updated).testConnection().fold(
                                onSuccess = { it },
                                onFailure = { "连接失败：${it.message ?: it::class.java.simpleName}" },
                            )
                            isTesting = false
                        }
                    },
                    onSaveAi = {
                        onSave(AiSettings(endpoint.trim(), model.trim(), apiKey.trim()))
                        connectionStatus = "已保存"
                    },
                    confirmClearProgress = confirmClearProgress,
                    onBeginClear = { confirmClearProgress = true },
                    onCancelClear = { confirmClearProgress = false },
                    onConfirmClear = {
                        onClearProgress()
                        confirmClearProgress = false
                    },
                )
            }
        }
        Spacer(Modifier.height(42.dp))
    }
}

@Composable
private fun SettingsPageSelector(selected: SettingsPage, onSelect: (SettingsPage) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        SettingsPage.entries.forEach { page ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(page) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    page.label,
                    color = if (selected == page) SettingsWhite else SettingsMuted,
                    fontSize = 20.sp,
                    fontWeight = if (selected == page) FontWeight.Bold else FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (selected == page) 3.dp else 1.dp)
                        .background(if (selected == page) SettingsBlue else SettingsLine),
                )
            }
        }
    }
}

@Composable
private fun ProxySettingsPage(
    proxyUrl: String,
    onProxyUrlChange: (String) -> Unit,
    useForUpdates: Boolean,
    onToggleUpdates: () -> Unit,
    useForAi: Boolean,
    onToggleAi: () -> Unit,
    proxyStatus: String?,
    onSaveProxy: () -> Unit,
    updateState: UpdateState,
    autoCheck: Boolean,
    wifiOnly: Boolean,
    lastCheckedAt: Long,
    onToggleAutoCheck: () -> Unit,
    onToggleWifiOnly: () -> Unit,
    onCheckUpdate: () -> Unit,
    onShowUpdateStatus: () -> Unit,
) {
    Column {
        SettingsSectionTitle("代理")
        SettingsInput(
            label = "代理地址",
            value = proxyUrl,
            onValueChange = onProxyUrlChange,
            keyboardType = KeyboardType.Uri,
            placeholder = "http://192.168.1.2:7890",
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "支持 HTTP、HTTPS、SOCKS 和 SOCKS5。未写协议时按 HTTP 处理；未写端口时 HTTP 使用 8080，SOCKS 使用 1080。",
            color = SettingsMuted,
            fontSize = 12.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(20.dp))
        SettingsToggleRow("版本更新走代理", useForUpdates, onToggleUpdates)
        SettingsToggleRow("AI 请求走代理", useForAi, onToggleAi)
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "保存代理",
                modifier = Modifier.clickable(onClick = onSaveProxy),
                color = SettingsBlue,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AnimatedVisibility(
            visible = proxyStatus != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SettingsInlineNotice(
                color = if (proxyStatus.orEmpty().startsWith("保存失败")) SettingsRed else SettingsBlue,
                label = "代理状态",
                body = proxyStatus.orEmpty(),
            )
        }

        Spacer(Modifier.height(48.dp))
        SettingsSectionTitle("应用更新")
        Text(
            "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
            color = SettingsWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Text("开发通道 · GitHub dev-latest", color = SettingsMuted, lineHeight = 22.sp)
        Spacer(Modifier.height(7.dp))
        Text(
            if (useForUpdates) "更新清单、签名与 APK 下载：通过代理" else "更新清单、签名与 APK 下载：直接连接",
            color = if (useForUpdates) SettingsBlue else SettingsMuted,
            fontSize = 12.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(20.dp))
        SettingsToggleRow("自动检查更新", autoCheck, onToggleAutoCheck)
        SettingsToggleRow("仅在 Wi-Fi 下载", wifiOnly, onToggleWifiOnly)
        Spacer(Modifier.height(12.dp))
        Text("上次检查：${formatUpdateCheckTime(lastCheckedAt)}", color = SettingsMuted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "检查更新",
                modifier = Modifier.clickable(enabled = updateState !is UpdateState.Checking, onClick = onCheckUpdate),
                color = if (updateState is UpdateState.Checking) SettingsMuted else SettingsBlue,
                fontWeight = FontWeight.SemiBold,
            )
            if (
                updateState is UpdateState.Available ||
                updateState is UpdateState.Downloading ||
                updateState is UpdateState.Ready ||
                updateState is UpdateState.Error ||
                updateState is UpdateState.UpToDate
            ) {
                Text(
                    "查看状态",
                    modifier = Modifier.clickable(onClick = onShowUpdateStatus),
                    color = SettingsWhite.copy(alpha = 0.72f),
                )
            }
        }
        SettingsInlineNotice(
            color = when (updateState) {
                is UpdateState.Error -> SettingsRed
                is UpdateState.Available,
                is UpdateState.Downloading,
                is UpdateState.Ready,
                -> SettingsBlue
                else -> SettingsYellow
            },
            label = "更新状态",
            body = updateState.settingsDescription(),
        )
    }
}

@Composable
private fun AiSettingsPage(
    endpoint: String,
    onEndpointChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    connectionStatus: String?,
    isTesting: Boolean,
    aiUsesProxy: Boolean,
    onTest: () -> Unit,
    onSaveAi: () -> Unit,
    confirmClearProgress: Boolean,
    onBeginClear: () -> Unit,
    onCancelClear: () -> Unit,
    onConfirmClear: () -> Unit,
) {
    Column {
        SettingsSectionTitle("AI")
        Text(
            if (aiUsesProxy) "当前 AI 请求通过代理连接。" else "当前 AI 请求直接连接，不使用代理。",
            color = if (aiUsesProxy) SettingsBlue else SettingsMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(20.dp))
        SettingsInput("接口地址", endpoint, onEndpointChange, KeyboardType.Uri, placeholder = "http://192.168.1.2:7777/v1")
        Spacer(Modifier.height(22.dp))
        SettingsInput("模型", model, onModelChange, placeholder = "gemma-4")
        Spacer(Modifier.height(22.dp))
        SettingsInput(
            "API Key",
            apiKey,
            onApiKeyChange,
            visualTransformation = PasswordVisualTransformation(),
            placeholder = "可留空",
        )
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "测试连接",
                modifier = Modifier.clickable(enabled = !isTesting && endpoint.isNotBlank(), onClick = onTest),
                color = if (isTesting) SettingsMuted else SettingsWhite.copy(alpha = 0.68f),
            )
            Text(
                "保存",
                modifier = Modifier.clickable(enabled = endpoint.isNotBlank() && model.isNotBlank(), onClick = onSaveAi),
                color = SettingsBlue,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AnimatedVisibility(
            visible = connectionStatus != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SettingsInlineNotice(
                color = if (connectionStatus.orEmpty().startsWith("连接失败")) SettingsRed else SettingsBlue,
                label = "AI 状态",
                body = connectionStatus.orEmpty(),
            )
        }

        Spacer(Modifier.height(48.dp))
        SettingsSectionTitle("学习数据")
        Text("答案、反馈、复习计划和掌握状态保存在本机。", color = SettingsWhite.copy(alpha = 0.72f))
        Spacer(Modifier.height(18.dp))
        AnimatedContent(
            targetState = confirmClearProgress,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "clearLearningData",
        ) { confirming ->
            if (!confirming) {
                Text("清空学习记录", modifier = Modifier.clickable(onClick = onBeginClear), color = SettingsRed)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("取消", modifier = Modifier.clickable(onClick = onCancelClear), color = SettingsMuted)
                    Text(
                        "确认清空",
                        modifier = Modifier.clickable(onClick = onConfirmClear),
                        color = SettingsRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = SettingsWhite.copy(alpha = 0.78f))
        Text(
            if (enabled) "开启" else "关闭",
            color = if (enabled) SettingsBlue else SettingsMuted,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(SettingsLine))
}

@Composable
private fun SettingsInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    placeholder: String = "输入…",
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, color = SettingsMuted, style = MaterialTheme.typography.labelMedium)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            textStyle = TextStyle(color = SettingsWhite, fontSize = 18.sp),
            cursorBrush = SolidColor(SettingsBlue),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = SettingsWhite.copy(alpha = 0.2f), fontSize = 18.sp)
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(SettingsLine))
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(bottom = 18.dp),
        color = SettingsYellow,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun SettingsInlineNotice(color: Color, label: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(color))
        Text(label, color = color, fontWeight = FontWeight.Bold)
        Text(body, color = SettingsWhite.copy(alpha = 0.72f), lineHeight = 23.sp)
    }
}

private fun UpdateState.settingsDescription(): String = when (this) {
    UpdateState.Idle -> "尚未发现可用更新。"
    UpdateState.Checking -> "正在获取并验证更新清单。"
    is UpdateState.UpToDate -> "当前版本已是最新版本。"
    is UpdateState.Available -> "发现 ${manifest.versionName}，可查看变更并下载。"
    is UpdateState.Downloading -> "正在下载 ${manifest.versionName}：$progress%。"
    is UpdateState.Ready -> "${manifest.versionName} 已下载并通过校验，可立即安装。"
    is UpdateState.Error -> message
}

private fun formatUpdateCheckTime(timestamp: Long): String {
    if (timestamp <= 0L) return "尚未检查"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
}
