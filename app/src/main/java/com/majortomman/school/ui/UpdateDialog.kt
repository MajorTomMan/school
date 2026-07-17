package com.majortomman.school.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.majortomman.school.update.InstallLaunchResult
import com.majortomman.school.update.UpdateInstaller
import com.majortomman.school.update.UpdateManifest
import com.majortomman.school.update.UpdateState
import java.util.Locale

private val UpdateBlack = Color(0xFF050608)
private val UpdateWhite = Color(0xFFF5F7FA)
private val UpdateBlue = Color(0xFF2D7BFF)
private val UpdateYellow = Color(0xFFFFCC00)
private val UpdateRed = Color(0xFFFF453A)
private val UpdateMuted = UpdateWhite.copy(alpha = 0.52f)
private val UpdateLine = UpdateWhite.copy(alpha = 0.15f)

@Composable
fun SchoolUpdateDialog(
    state: UpdateState,
    isMandatory: (UpdateManifest) -> Boolean,
    onDismiss: () -> Unit,
    onLater: (UpdateManifest) -> Unit,
    onIgnore: (UpdateManifest) -> Unit,
    onDownload: (UpdateManifest) -> Unit,
    onCancelDownload: () -> Unit,
) {
    val manifest = state.manifestOrNull()
    val mandatory = manifest?.let(isMandatory) == true
    val context = LocalContext.current
    var installNotice by remember(state) { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!mandatory && state !is UpdateState.Downloading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !mandatory && state !is UpdateState.Downloading,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .background(UpdateBlack)
                .padding(horizontal = 24.dp, vertical = 26.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(state.accentColor()))
            Spacer(Modifier.height(20.dp))
            Text(
                text = state.title(),
                color = UpdateWhite,
                fontSize = 29.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.subtitle(),
                color = UpdateMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )

            if (manifest != null) {
                Spacer(Modifier.height(22.dp))
                UpdateMetaRow("版本", "${manifest.versionName}（${manifest.versionCode}）")
                UpdateMetaRow("大小", formatBytes(manifest.apk.size))
                if (mandatory) UpdateMetaRow("升级要求", "必须升级")
            }

            when (state) {
                is UpdateState.Available -> {
                    UpdateNotes("修改点", state.manifest.changes, UpdateBlue)
                    UpdateNotes("修复点", state.manifest.fixes, UpdateYellow)
                }
                is UpdateState.Downloading -> {
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("下载进度", color = UpdateMuted, fontSize = 13.sp)
                        Text("${state.progress}%", color = UpdateBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).background(UpdateLine)) {
                        Box(
                            Modifier
                                .fillMaxWidth(state.progress.coerceIn(0, 100) / 100f)
                                .height(3.dp)
                                .background(UpdateBlue),
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.manifest.apk.size)}",
                        color = UpdateMuted,
                        fontSize = 12.sp,
                    )
                }
                is UpdateState.Ready -> {
                    Spacer(Modifier.height(24.dp))
                    Text("APK 文件、版本号、包名和签名证书均已验证通过。", color = UpdateWhite.copy(alpha = 0.78f), lineHeight = 22.sp)
                    installNotice?.let {
                        Spacer(Modifier.height(14.dp))
                        Text(it, color = UpdateYellow, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
                is UpdateState.Error -> {
                    Spacer(Modifier.height(22.dp))
                    Text(state.message, color = UpdateRed, lineHeight = 22.sp)
                }
                is UpdateState.UpToDate -> {
                    Spacer(Modifier.height(22.dp))
                    Text("当前安装包已经是最新开发版本。", color = UpdateWhite.copy(alpha = 0.78f), lineHeight = 22.sp)
                }
                UpdateState.Checking -> {
                    Spacer(Modifier.height(22.dp))
                    Text("正在验证远端更新清单与数字签名…", color = UpdateWhite.copy(alpha = 0.78f))
                }
                UpdateState.Idle -> Unit
            }

            Spacer(Modifier.height(30.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(UpdateLine))
            Spacer(Modifier.height(18.dp))
            when (state) {
                is UpdateState.Available -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (!mandatory) {
                        Text("忽略此版本", Modifier.clickable { onIgnore(state.manifest) }, color = UpdateMuted, fontSize = 13.sp)
                        Text("稍后提醒", Modifier.clickable { onLater(state.manifest) }, color = UpdateWhite.copy(alpha = 0.72f), fontSize = 13.sp)
                    }
                    Text(
                        "下载并升级",
                        Modifier.clickable { onDownload(state.manifest) },
                        color = UpdateBlue,
                        fontWeight = FontWeight.Bold,
                    )
                }
                is UpdateState.Downloading -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("取消下载", Modifier.clickable(onClick = onCancelDownload), color = UpdateRed, fontWeight = FontWeight.SemiBold)
                }
                is UpdateState.Ready -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (!mandatory) Text("稍后安装", Modifier.clickable(onClick = onDismiss), color = UpdateMuted)
                    Text(
                        "立即安装",
                        modifier = Modifier.clickable {
                            val activity = context.findActivity()
                            installNotice = if (activity == null) {
                                "无法打开系统安装器。"
                            } else {
                                when (UpdateInstaller.launch(activity, state.apkFile)) {
                                    InstallLaunchResult.STARTED -> null
                                    InstallLaunchResult.NEEDS_UNKNOWN_SOURCES_PERMISSION -> "请允许 School 安装未知应用，返回后再次点击“立即安装”。"
                                    InstallLaunchResult.FILE_MISSING -> "更新文件不存在，请重新下载。"
                                }
                            }
                        },
                        color = UpdateBlue,
                        fontWeight = FontWeight.Bold,
                    )
                }
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("关闭", Modifier.clickable(onClick = onDismiss), color = UpdateBlue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun UpdateMetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = UpdateMuted, fontSize = 12.sp)
        Text(value, color = UpdateWhite.copy(alpha = 0.82f), fontSize = 13.sp)
    }
}

@Composable
private fun UpdateNotes(title: String, entries: List<String>, color: Color) {
    if (entries.isEmpty()) return
    Spacer(Modifier.height(22.dp))
    Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(9.dp))
    entries.forEach { entry ->
        Text("• $entry", color = UpdateWhite.copy(alpha = 0.78f), fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(4.dp))
    }
}

private fun UpdateState.manifestOrNull(): UpdateManifest? = when (this) {
    is UpdateState.Available -> manifest
    is UpdateState.Downloading -> manifest
    is UpdateState.Ready -> manifest
    else -> null
}

private fun UpdateState.title(): String = when (this) {
    UpdateState.Checking -> "正在检查更新"
    is UpdateState.UpToDate -> "已是最新版本"
    is UpdateState.Available -> "发现新版本"
    is UpdateState.Downloading -> "正在下载更新"
    is UpdateState.Ready -> "更新包已准备完成"
    is UpdateState.Error -> "更新失败"
    UpdateState.Idle -> "应用更新"
}

private fun UpdateState.subtitle(): String = when (this) {
    UpdateState.Checking -> "从 GitHub Release 获取签名更新清单。"
    is UpdateState.UpToDate -> "没有比当前版本更新的开发包。"
    is UpdateState.Available -> "确认变更后，可下载完整 APK 并由系统安装器覆盖升级。"
    is UpdateState.Downloading -> "下载完成后会自动校验文件完整性和 APK 签名。"
    is UpdateState.Ready -> "Android 会显示系统安装确认页面，普通应用不能静默安装。"
    is UpdateState.Error -> "本次操作没有修改当前已安装版本。"
    UpdateState.Idle -> ""
}

private fun UpdateState.accentColor(): Color = when (this) {
    is UpdateState.Error -> UpdateRed
    is UpdateState.Ready -> Color(0xFF34C759)
    is UpdateState.Available,
    is UpdateState.Downloading,
    UpdateState.Checking,
    -> UpdateBlue
    else -> UpdateYellow
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
