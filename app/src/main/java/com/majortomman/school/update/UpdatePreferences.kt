package com.majortomman.school.update

import android.content.Context

internal class UpdatePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("school_updates", Context.MODE_PRIVATE)

    fun settings(): UpdateSettings = UpdateSettings(
        autoCheck = preferences.getBoolean(KEY_AUTO_CHECK, true),
        wifiOnly = preferences.getBoolean(KEY_WIFI_ONLY, true),
        lastCheckedAt = preferences.getLong(KEY_LAST_CHECKED, 0L),
    )

    fun setAutoCheck(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
    }

    fun setWifiOnly(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun setLastChecked(timestamp: Long) {
        preferences.edit().putLong(KEY_LAST_CHECKED, timestamp).apply()
    }

    fun cacheManifest(rawJson: String) {
        preferences.edit().putString(KEY_MANIFEST, rawJson).apply()
    }

    fun cachedManifest(): UpdateManifest? = preferences.getString(KEY_MANIFEST, null)
        ?.let { runCatching { UpdateManifestCodec.decode(it) }.getOrNull() }

    fun setSnoozeUntil(timestamp: Long) {
        preferences.edit().putLong(KEY_SNOOZE_UNTIL, timestamp).apply()
    }

    fun snoozeUntil(): Long = preferences.getLong(KEY_SNOOZE_UNTIL, 0L)

    fun ignoreVersion(versionCode: Long) {
        preferences.edit().putLong(KEY_IGNORED_VERSION, versionCode).apply()
    }

    fun ignoredVersion(): Long = preferences.getLong(KEY_IGNORED_VERSION, 0L)

    fun saveDownloadedApk(versionCode: Long, path: String) {
        preferences.edit()
            .putLong(KEY_DOWNLOADED_VERSION, versionCode)
            .putString(KEY_DOWNLOADED_PATH, path)
            .apply()
    }

    fun downloadedApk(versionCode: Long): String? {
        if (preferences.getLong(KEY_DOWNLOADED_VERSION, 0L) != versionCode) return null
        return preferences.getString(KEY_DOWNLOADED_PATH, null)
    }

    fun clearDownloadedApk() {
        preferences.edit().remove(KEY_DOWNLOADED_VERSION).remove(KEY_DOWNLOADED_PATH).apply()
    }

    private companion object {
        const val KEY_AUTO_CHECK = "auto_check"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_LAST_CHECKED = "last_checked"
        const val KEY_MANIFEST = "manifest"
        const val KEY_SNOOZE_UNTIL = "snooze_until"
        const val KEY_IGNORED_VERSION = "ignored_version"
        const val KEY_DOWNLOADED_VERSION = "downloaded_version"
        const val KEY_DOWNLOADED_PATH = "downloaded_path"
    }
}
