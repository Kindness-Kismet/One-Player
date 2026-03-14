package one.next.player.core.domain

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import one.next.player.core.common.Dispatcher
import one.next.player.core.common.NextDispatchers
import one.next.player.core.model.Folder
import one.next.player.core.model.Video

data class SearchResults(
    val folders: List<Folder> = emptyList(),
    val videos: List<Video> = emptyList(),
) {
    val isEmpty: Boolean
        get() = folders.isEmpty() && videos.isEmpty()

    val totalCount: Int
        get() = folders.size + videos.size
}

fun SearchResults.asRootFolder(): Folder = Folder.rootFolder.copy(
    mediaList = videos,
    folderList = folders,
)

class SearchMediaUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val getSortedFoldersUseCase: GetSortedFoldersUseCase,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(query: String): Flow<SearchResults> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return flowOf(SearchResults())
        }

        return combine(
            getSortedVideosUseCase(),
            getSortedFoldersUseCase(),
        ) { videos, folders ->
            val searchMatcher = SearchMatcher(normalizedQuery)

            val scoredFolders = folders.mapNotNull { folder ->
                val score = searchMatcher.calculateScore(folder.name, folder.path)
                if (score > 0) folder to score else null
            }.sortedByDescending { it.second }.map { it.first }

            val scoredVideos = videos.mapNotNull { video ->
                val score = searchMatcher.calculateScore(video.nameWithExtension, video.path)
                if (score > 0) video to score else null
            }.sortedByDescending { it.second }.map { it.first }

            SearchResults(
                folders = scoredFolders,
                videos = scoredVideos,
            )
        }.flowOn(defaultDispatcher)
    }
}

private class SearchMatcher(query: String) {
    private val queryLower = query.lowercase()
    private val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

    private val wordsInOrderPattern: Regex? = if (queryWords.size > 1) {
        Regex(queryWords.joinToString(".*") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    } else {
        null
    }

    fun calculateScore(vararg texts: String): Int {
        var bestScore = 0

        for (text in texts) {
            val textLower = text.lowercase()
            val score = calculateTextScore(textLower)
            if (score > bestScore) {
                bestScore = score
            }
        }

        return bestScore
    }

    private fun calculateTextScore(textLower: String): Int {
        if (textLower.contains(queryLower)) {
            val wordBoundaryBonus = if (isWordBoundaryMatch(textLower, queryLower)) 50 else 0
            val startBonus = if (textLower.startsWith(queryLower)) 30 else 0
            return 1000 + wordBoundaryBonus + startBonus
        }

        if (queryWords.size <= 1) {
            return 0
        }

        wordsInOrderPattern?.let { pattern ->
            if (pattern.containsMatchIn(textLower)) {
                return 500
            }
        }

        val allWordsPresent = queryWords.all { word -> textLower.contains(word) }
        if (allWordsPresent) {
            val boundaryMatches = queryWords.count { word -> isWordBoundaryMatch(textLower, word) }
            return 200 + (boundaryMatches * 20)
        }

        return 0
    }

    private fun isWordBoundaryMatch(text: String, query: String): Boolean {
        val index = text.indexOf(query)
        if (index == -1) return false

        val isAtStart = index == 0
        val isPrecededByBoundary = index > 0 && text[index - 1].isWordBoundary()

        return isAtStart || isPrecededByBoundary
    }

    private fun Char.isWordBoundary(): Boolean = this in listOf(' ', '_', '-', '.', '/', '\\', '[', ']', '(', ')')
}
