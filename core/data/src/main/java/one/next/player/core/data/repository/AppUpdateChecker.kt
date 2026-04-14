package one.next.player.core.data.repository

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.next.player.core.common.Logger
import org.json.JSONObject

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
)

@Singleton
class AppUpdateChecker @Inject constructor() {

    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val RELEASES_URL =
            "https://api.github.com/repos/Kindness-Kismet/One-Player/releases/latest"
    }

    suspend fun checkForUpdate(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val release = JSONObject(json)
            val tagName = release.optString("tag_name", "").removePrefix("v")
            val htmlUrl = release.optString("html_url", "")
            if (tagName.isEmpty()) return@withContext null

            if (compareVersions(tagName, currentVersion) > 0) {
                AppUpdateInfo(latestVersion = tagName, releaseUrl = htmlUrl)
            } else {
                null
            }
        }.getOrElse { throwable ->
            Logger.error(TAG, "Failed to check for updates", throwable)
            null
        }
    }
}

// 正数表示 v1 更新，负数表示 v2 更新
internal fun compareVersions(v1: String, v2: String): Int {
    val parts1 = v1.split('.').map { it.toIntOrNull() ?: 0 }
    val parts2 = v2.split('.').map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(parts1.size, parts2.size)
    for (i in 0 until maxLen) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) return p1 - p2
    }
    return 0
}
