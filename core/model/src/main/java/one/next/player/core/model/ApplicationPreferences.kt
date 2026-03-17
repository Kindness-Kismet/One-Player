package one.next.player.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ApplicationPreferences(
    val appLanguage: String = "",
    val sortBy: Sort.By = Sort.By.TITLE,
    val sortOrder: Sort.Order = Sort.Order.ASCENDING,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val shouldUseHighContrastDarkTheme: Boolean = false,
    val shouldUseDynamicColors: Boolean = true,
    val shouldPreventScreenshots: Boolean = false,
    val shouldHideInRecents: Boolean = false,
    val shouldMarkLastPlayedMedia: Boolean = true,
    val shouldIgnoreNoMediaFiles: Boolean = false,
    val isRecycleBinEnabled: Boolean = false,
    val excludeFolders: List<String> = emptyList(),
    val mediaViewMode: MediaViewMode = MediaViewMode.FOLDERS,
    val mediaLayoutMode: MediaLayoutMode = MediaLayoutMode.LIST,

    // 字段显示
    val shouldShowDurationField: Boolean = true,
    val shouldShowExtensionField: Boolean = false,
    val shouldShowPathField: Boolean = true,
    val shouldShowResolutionField: Boolean = false,
    val shouldShowSizeField: Boolean = false,
    val shouldShowThumbnailField: Boolean = true,
    val shouldShowPlayedProgress: Boolean = true,

    // 缩略图生成
    val thumbnailGenerationStrategy: ThumbnailGenerationStrategy = ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE,
    val thumbnailFramePosition: Float = DEFAULT_THUMBNAIL_FRAME_POSITION,
    val shouldCheckForUpdatesOnStartup: Boolean = false,
    val manualVideoPaths: List<String> = emptyList(),
    val pendingExternalVideoPaths: List<String> = emptyList(),
) {

    fun isPathExcluded(path: String): Boolean {
        if (path.isBlank()) return false

        return excludeFolders.any { excludedPath ->
            path == excludedPath || path.startsWith("$excludedPath/")
        }
    }

    companion object {
        const val DEFAULT_THUMBNAIL_FRAME_POSITION = 0.5f
    }
}
