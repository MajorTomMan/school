package com.majortomman.school.data.material

import org.json.JSONArray
import org.json.JSONObject

object MaterialPackManifestParser {
    private val packIdPattern = Regex("[a-z0-9][a-z0-9._-]{2,63}")
    private val sha256Pattern = Regex("[0-9a-fA-F]{64}")

    fun parse(json: String): MaterialPackManifest {
        val root = JSONObject(json)
        val schemaVersion = root.requireInt("schemaVersion")
        require(schemaVersion == MATERIAL_PACK_SCHEMA_VERSION) {
            "不支持的教材包格式版本：$schemaVersion"
        }

        val packId = root.requireString("packId")
        require(packIdPattern.matches(packId)) {
            "packId 只能包含小写字母、数字、点、下划线和短横线，长度为 3—64"
        }

        val version = root.requireString("version")
        require(version.length <= 64) { "教材包版本号过长" }
        val title = root.requireString("title")
        require(title.length <= 120) { "教材标题过长" }
        val subject = root.requireString("subject")
        require(subject.length <= 40) { "科目名称过长" }
        val catalogPath = safeRelativePath(root.optString("catalog", "catalog.json"), "catalog")

        val pdfObject = root.optJSONObject("pdf")
            ?: throw IllegalArgumentException("manifest.json 缺少 pdf 对象")
        val pdfPath = safeRelativePath(pdfObject.requireString("path"), "pdf.path")
        require(pdfPath.endsWith(".pdf", ignoreCase = true)) { "pdf.path 必须指向 PDF 文件" }
        val sha256 = pdfObject.requireString("sha256").lowercase()
        require(sha256Pattern.matches(sha256)) { "pdf.sha256 必须是 64 位十六进制摘要" }
        val pageIndexOffset = pdfObject.optInt("pageIndexOffset", 0)
        require(pageIndexOffset in -10_000..10_000) { "pdf.pageIndexOffset 超出允许范围" }

        return MaterialPackManifest(
            schemaVersion = schemaVersion,
            packId = packId,
            version = version,
            title = title,
            subject = subject,
            catalogPath = catalogPath,
            pdf = MaterialPdfAsset(
                path = pdfPath,
                sha256 = sha256,
                pageIndexOffset = pageIndexOffset,
            ),
        )
    }

    fun toJson(manifest: MaterialPackManifest): JSONObject = JSONObject()
        .put("schemaVersion", manifest.schemaVersion)
        .put("packId", manifest.packId)
        .put("version", manifest.version)
        .put("title", manifest.title)
        .put("subject", manifest.subject)
        .put("catalog", manifest.catalogPath)
        .put(
            "pdf",
            JSONObject()
                .put("path", manifest.pdf.path)
                .put("sha256", manifest.pdf.sha256)
                .put("pageIndexOffset", manifest.pdf.pageIndexOffset),
        )

    fun safeRelativePath(raw: String, field: String): String {
        val normalized = raw.trim().replace('\\', '/')
        require(normalized.isNotEmpty()) { "$field 不能为空" }
        require(!normalized.startsWith('/')) { "$field 不能是绝对路径" }
        require(!Regex("^[A-Za-z]:").containsMatchIn(normalized)) { "$field 不能是绝对路径" }
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        require(parts.isNotEmpty() && parts.none { it == ".." }) { "$field 包含不安全路径" }
        return parts.joinToString("/")
    }

    private fun JSONObject.requireString(name: String): String {
        val value = optString(name).trim()
        require(value.isNotEmpty()) { "manifest.json 缺少 $name" }
        return value
    }

    private fun JSONObject.requireInt(name: String): Int {
        require(has(name)) { "manifest.json 缺少 $name" }
        return getInt(name)
    }
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) add(getString(index))
}
