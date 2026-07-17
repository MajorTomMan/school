package com.majortomman.school.update

import org.json.JSONArray
import org.json.JSONObject

internal object UpdateManifestCodec {
    fun decode(json: String): UpdateManifest {
        val root = JSONObject(json)
        val apk = root.getJSONObject("apk")
        return UpdateManifest(
            schemaVersion = root.getInt("schemaVersion"),
            channel = root.getString("channel"),
            versionCode = root.getLong("versionCode"),
            versionName = root.getString("versionName"),
            minimumSupportedVersionCode = root.optLong("minimumSupportedVersionCode", 0L),
            mandatory = root.optBoolean("mandatory", false),
            publishedAt = root.optString("publishedAt"),
            changes = root.optJSONArray("changes").toStringList(),
            fixes = root.optJSONArray("fixes").toStringList(),
            apk = UpdateApk(
                fileName = apk.getString("fileName"),
                downloadUrl = apk.getString("downloadUrl"),
                size = apk.getLong("size"),
                sha256 = apk.getString("sha256").normalizedSha256(),
                certificateSha256 = apk.getString("certificateSha256").normalizedSha256(),
            ),
        ).also(::validate)
    }

    fun encode(manifest: UpdateManifest): String = JSONObject()
        .put("schemaVersion", manifest.schemaVersion)
        .put("channel", manifest.channel)
        .put("versionCode", manifest.versionCode)
        .put("versionName", manifest.versionName)
        .put("minimumSupportedVersionCode", manifest.minimumSupportedVersionCode)
        .put("mandatory", manifest.mandatory)
        .put("publishedAt", manifest.publishedAt)
        .put("changes", JSONArray(manifest.changes))
        .put("fixes", JSONArray(manifest.fixes))
        .put(
            "apk",
            JSONObject()
                .put("fileName", manifest.apk.fileName)
                .put("downloadUrl", manifest.apk.downloadUrl)
                .put("size", manifest.apk.size)
                .put("sha256", manifest.apk.sha256)
                .put("certificateSha256", manifest.apk.certificateSha256),
        )
        .toString()

    private fun validate(manifest: UpdateManifest) {
        require(manifest.schemaVersion == 1) { "不支持的更新清单版本。" }
        require(manifest.channel == UPDATE_CHANNEL) { "更新通道不匹配。" }
        require(manifest.versionCode > 0L) { "更新版本号无效。" }
        require(manifest.versionName.isNotBlank()) { "更新版本名称为空。" }
        require(manifest.apk.fileName.endsWith(".apk")) { "更新文件不是 APK。" }
        require(manifest.apk.size > 0L) { "更新文件大小无效。" }
        require(SHA256_REGEX.matches(manifest.apk.sha256)) { "APK SHA-256 无效。" }
        require(SHA256_REGEX.matches(manifest.apk.certificateSha256)) { "APK 证书 SHA-256 无效。" }
        val url = java.net.URI(manifest.apk.downloadUrl)
        require(url.scheme == "https") { "更新地址必须使用 HTTPS。" }
        require(url.host == "github.com") { "更新地址不是允许的 GitHub Release 地址。" }
        require(url.path.contains("/MajorTomMan/school/releases/download/dev-latest/")) {
            "更新地址不是 School 的 dev-latest Release。"
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }

    private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
}

internal fun String.normalizedSha256(): String = lowercase().filter { it in '0'..'9' || it in 'a'..'f' }
