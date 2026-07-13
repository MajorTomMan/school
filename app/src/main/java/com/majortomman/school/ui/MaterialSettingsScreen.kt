package com.majortomman.school.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.ai.OpenAiCompatibleClient
import com.majortomman.school.data.AiSettings
import com.majortomman.school.data.material.MaterialPackState
import kotlinx.coroutines.launch

private val SettingsBlack = Color(0xFF050608)
private val SettingsWhite = Color(0xFFF5F7FA)
private val SettingsBlue = Color(0xFF2D7BFF)
private val SettingsRed = Color(0xFFFF453A)
private val SettingsYellow = Color(0xFFFFCC00)
private val SettingsMuted = SettingsWhite.copy(alpha = 0.46f)
private val SettingsLine = SettingsWhite.copy(alpha = 0.13f)

@Composable
fun MaterialSettingsScreen(
    settings: AiSettings,
    materialState: MaterialPackState,
    onSave: (AiSettings) -> Unit,
    onImportMaterial: (Uri) -> Unit,
    onRemoveMaterial: () -> Unit,
    onClearProgress: () -> Unit,
) {
    var endpoint by rememberSaveable { mutableStateOf(settings.endpoint) }
    var model by rememberSaveable { mutableStateOf(settings.model) }
    var apiKey by rememberSaveable { mutableStateOf(settings.apiKey) }
    var connectionStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var isTesting by rememberSaveable { mutableStateOf(false) }
    var confirmRemovePack by rememberSaveable { mutableStateOf(false) }
    var confirmClearProgress by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportMaterial)
    }

    LaunchedEffect(settings) {
        endpoint = settings.endpoint
        model = settings.model
        apiKey = settings.apiKey
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 30.dp),
    ) {
        Text("设置", color = SettingsWhite, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(42.dp))

        SettingsSectionTitle("教材")
        val installed = materialState.installed
        if (installed == null) {
            Text("尚未导入教材", color = SettingsWhite, fontSize = 21.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "选择符合 School Material Pack v1 的 ZIP 文件。导入后，学习页可以直接打开对应教材页。",
                color = SettingsMuted,
                lineHeight = 23.sp,
            )
        } else {
            Text(installed.manifest.title, color = SettingsWhite, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            Text(
                "${installed.manifest.subject} · ${installed.manifest.version} · ${formatBytes(installed.sizeBytes)}",
                color = SettingsMuted,
            )
            Spacer(Modifier.height(7.dp))
            Text("PDF 已通过 SHA-256 校验", color = SettingsBlue, fontSize = 13.sp)
        }
        Spacer(Modifier.height(22.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (materialState.importing) "正在导入…" else if (installed == null) "导入教材包" else "替换教材包",
                modifier = Modifier.clickable(enabled = !materialState.importing) {
                    importLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream",
                        ),
                    )
                },
                color = if (materialState.importing) SettingsMuted else SettingsBlue,
                fontWeight = FontWeight.SemiBold,
            )
            if (installed != null) {
                Text(
                    if (confirmRemovePack) "确认移除" else "移除",
                    modifier = Modifier.clickable {
                        if (confirmRemovePack) {
                            onRemoveMaterial()
                            confirmRemovePack = false
                        } else {
                            confirmRemovePack = true
                        }
                    },
                    color = SettingsRed,
                )
            }
        }
        AnimatedVisibility(
            visible = materialState.message != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SettingsInlineNotice(
                color = if (materialState.message.orEmpty().startsWith("导入失败")) SettingsRed else SettingsBlue,
                label = "教材状态",
                body = materialState.message.orEmpty(),
            )
        }

        Spacer(Modifier.height(48.dp))
        SettingsSectionTitle("AI")
        SettingsInput("接口地址", endpoint, { endpoint = it; connectionStatus = null }, KeyboardType.Uri)
        Spacer(Modifier.height(22.dp))
        SettingsInput("模型", model, { model = it; connectionStatus = null })
        Spacer(Modifier.height(22.dp))
        SettingsInput(
            "API Key",
            apiKey,
            { apiKey = it },
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "测试连接",
                modifier = Modifier.clickable(enabled = !isTesting && endpoint.isNotBlank()) {
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
                color = if (isTesting) SettingsMuted else SettingsWhite.copy(alpha = 0.68f),
            )
            Text(
                "保存",
                modifier = Modifier.clickable(enabled = endpoint.isNotBlank() && model.isNotBlank()) {
                    onSave(AiSettings(endpoint.trim(), model.trim(), apiKey.trim()))
                    connectionStatus = "已保存"
                },
                color = SettingsBlue,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AnimatedVisibility(connectionStatus != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
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
                Text(
                    "清空学习记录",
                    modifier = Modifier.clickable { confirmClearProgress = true },
                    color = SettingsRed,
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("取消", modifier = Modifier.clickable { confirmClearProgress = false }, color = SettingsMuted)
                    Text(
                        "确认清空",
                        modifier = Modifier.clickable {
                            onClearProgress()
                            confirmClearProgress = false
                        },
                        color = SettingsRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(42.dp))
    }
}

@Composable
private fun SettingsInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
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
                    if (value.isEmpty()) Text("输入…", color = SettingsWhite.copy(alpha = 0.2f), fontSize = 18.sp)
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

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
