package com.majortomman.school.data.material

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * 在应用进程启动时安装全部内置预制课程。PDF 不随 APK 分发；课程树、知识点、
 * 页码范围、讲解与练习可以离线使用，之后绑定匹配 PDF 即可查看教材原页。
 */
class PrebuiltTextbookBootstrapProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.applicationContext?.let { appContext ->
            runCatching { PrebuiltTextbookBootstrap.installMissing(appContext) }
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

internal object PrebuiltTextbookBootstrap {
    fun installMissing(context: android.content.Context) {
        val existing = MaterialLibraryStore.read(context).associateBy { it.slot.key }
        BundledTextbookKnowledgePack.books(context).forEach { book ->
            val current = existing[book.slot.key]
            if (current?.pack?.pdfFile?.isFile == true) return@forEach
            if (
                current?.pack?.manifest?.version == BundledTextbookKnowledgePack.PACK_VERSION &&
                current.pack.manifest.packId == "prebuilt-${book.sha256.take(16)}"
            ) return@forEach
            MaterialLibraryStore.upsert(context, book.installUnbound(context))
        }
    }
}
