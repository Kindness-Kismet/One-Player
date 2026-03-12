package one.next.player.settings.utils

import java.util.Locale
import one.next.player.core.common.Logger

object LocalesHelper {

    val appSupportedLocales: List<Pair<String, String>> = listOf(
        Pair("English", "en"),
        Pair("简体中文", "zh-CN"),
        Pair("繁體中文", "zh-TW"),
    )

    fun getAvailableLocales(): List<Pair<String, String>> = try {
        Locale.getAvailableLocales().map {
            val key = it.isO3Language
            val language = it.displayLanguage
            Pair(language, key)
        }.distinctBy { it.second }.sortedBy { it.first }
    } catch (e: Exception) {
        Logger.error(TAG, "Failed to load available locales", e)
        listOf()
    }

    fun getLocaleDisplayLanguage(key: String): String = try {
        if (key.isBlank()) return ""

        Locale.getAvailableLocales().firstOrNull { locale ->
            locale.isO3Language == key || locale.language == key
        }?.displayLanguage.orEmpty()
    } catch (e: Exception) {
        Logger.error(TAG, "Failed to resolve locale display language: $key", e)
        ""
    }

    fun getAppLocaleDisplayName(languageTag: String): String = appSupportedLocales.firstOrNull {
        it.second == languageTag
    }?.first.orEmpty()

    private const val TAG = "LocalesHelper"
}
