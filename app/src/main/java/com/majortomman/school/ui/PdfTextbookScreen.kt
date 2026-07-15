package com.majortomman.school.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.data.material.InstalledMaterialPack
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ReaderBlack = Color(0xFF050608)
private val ReaderWhite = Color(0xFFF5F7FA)
private val ReaderBlue = Color(0xFF2D7BFF)
private val ReaderYellow = Color(0xFFFFCC00)
private val ReaderMuted = ReaderWhite.copy(alpha = 0.46f)

@Composable
fun PdfTextbookScreen(
    pack: InstalledMaterialPack,
    initialPrintedPage: Int,
    onBack: () -> Unit,
) {
    if (!pack.pdfFile.isFile) {
        ReaderError(
            message = "预制课程已经可以学习，但尚未绑定教材 PDF。请从教材中心绑定对应版本后再查看原页。",
            onBack = onBack,
        )
        return
    }

    val sessionResult = remember(pack.rootPath, pack.manifest.version) {
        runCatching { PdfRenderSession(pack.pdfFile) }
    }
    val session = sessionResult.getOrNull()
    DisposableEffect(session) { onDispose { session?.close() } }

    if (session == null) {
        ReaderError(
            message = sessionResult.exceptionOrNull()?.message ?: "无法打开教材 PDF",
            onBack = onBack,
        )
        return
    }

    val lessonWindow = remember(pack.rootPath, initialPrintedPage) {
        TextbookReadingWindow.resolve(pack, initialPrintedPage)
    }
    var fullBook by rememberSaveable(pack.manifest.packId, initialPrintedPage) { mutableStateOf(false) }
    val activeWindow = lessonWindow.takeUnless { fullBook }
    val initialPage = activeWindow?.clamp(initialPrintedPage) ?: initialPrintedPage
    var pageIndex by rememberSaveable(pack.manifest.packId, initialPrintedPage) {
        mutableStateOf(pack.printedPageToPdfIndex(initialPage).coerceIn(0, session.pageCount - 1))
    }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderError by remember { mutableStateOf<String?>(null) }

    val printedPage = pack.pdfIndexToPrintedPage(pageIndex)
    val previousPrintedPage = pack.pdfIndexToPrintedPage((pageIndex - 1).coerceAtLeast(0))
    val nextPrintedPage = pack.pdfIndexToPrintedPage((pageIndex + 1).coerceAtMost(session.pageCount - 1))
    val canPrevious = pageIndex > 0 && (activeWindow == null || activeWindow.contains(previousPrintedPage))
    val canNext = pageIndex < session.pageCount - 1 && (activeWindow == null || activeWindow.contains(nextPrintedPage))

    Column(
        modifier = Modifier.fillMaxSize().background(ReaderBlack).systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (activeWindow == null) "教材" else "本节教材",
                    color = ReaderYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(pack.manifest.title, color = ReaderWhite, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                activeWindow?.let { range ->
                    Text(
                        "只查看第 ${range.startPrintedPage}—${range.endPrintedPage} 页",
                        color = ReaderBlue,
                        fontSize = 12.sp,
                    )
                }
            }
            Text("$printedPage 页", color = ReaderMuted, style = MaterialTheme.typography.labelLarge)
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val density = LocalDensity.current
            val targetWidthPx = with(density) { maxWidth.toPx().toInt() }.coerceIn(720, 2400)
            LaunchedEffect(pageIndex, targetWidthPx) {
                bitmap = null
                renderError = null
                runCatching { withContext(Dispatchers.IO) { session.render(pageIndex, targetWidthPx) } }
                    .onSuccess { bitmap = it }
                    .onFailure { renderError = it.message ?: "页面渲染失败" }
            }

            when {
                renderError != null -> Text(
                    renderError.orEmpty(),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = ReaderWhite,
                    textAlign = TextAlign.Center,
                )
                bitmap == null -> Text("正在打开…", modifier = Modifier.align(Alignment.Center), color = ReaderMuted)
                else -> AnimatedContent(
                    targetState = bitmap,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                    label = "pdfPage",
                ) { rendered ->
                    if (rendered != null) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                bitmap = rendered.asImageBitmap(),
                                contentDescription = "教材第 $printedPage 页",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    }
                }
            }
        }

        if (lessonWindow != null) {
            Text(
                text = if (fullBook) "返回本节范围" else "查看完整教材",
                modifier = Modifier.fillMaxWidth().clickable {
                    fullBook = !fullBook
                    if (!fullBook) {
                        val clamped = lessonWindow.clamp(pack.pdfIndexToPrintedPage(pageIndex))
                        pageIndex = pack.printedPageToPdfIndex(clamped).coerceIn(0, session.pageCount - 1)
                    }
                }.padding(vertical = 9.dp),
                color = ReaderMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(ReaderBlack).navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReaderAction("上一页", ReaderWhite.copy(alpha = 0.78f), Modifier.weight(1f), canPrevious) { pageIndex -= 1 }
            ReaderAction("返回", ReaderYellow, Modifier.weight(1f), onClick = onBack)
            ReaderAction("下一页", ReaderBlue, Modifier.weight(1f), canNext) { pageIndex += 1 }
        }
    }
}

@Composable
private fun ReaderAction(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val effective = if (enabled) color else color.copy(alpha = 0.22f)
    Text(
        text = label,
        modifier = modifier.height(45.dp).border(1.dp, effective, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        color = effective,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ReaderError(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(ReaderBlack).systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("尚未绑定教材", color = ReaderWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(message, color = ReaderMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Text(
            "返回教材中心",
            modifier = Modifier.clickable(onClick = onBack).padding(14.dp),
            color = ReaderBlue,
            fontWeight = FontWeight.Bold,
        )
    }
}

private class PdfRenderSession(file: java.io.File) : Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    val pageCount: Int get() = renderer.pageCount

    fun render(index: Int, width: Int): Bitmap {
        require(index in 0 until pageCount) { "页码超出范围" }
        renderer.openPage(index).use { page ->
            val height = (width * (page.height.toFloat() / page.width.toFloat())).toInt().coerceIn(1, 4200)
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }
}
