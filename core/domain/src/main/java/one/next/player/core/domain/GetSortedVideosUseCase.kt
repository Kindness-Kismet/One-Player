package one.next.player.core.domain

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import one.next.player.core.common.Dispatcher
import one.next.player.core.common.NextDispatchers
import one.next.player.core.data.repository.MediaRepository
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.model.Sort
import one.next.player.core.model.Video

class GetSortedVideosUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    operator fun invoke(
        folderPath: String? = null,
        recycleBinOnly: Boolean = false,
    ): Flow<List<Video>> {
        val videosFlow = if (recycleBinOnly) {
            mediaRepository.getRecycleBinVideosFlow()
        } else if (folderPath != null) {
            mediaRepository.getVideosFlowFromFolderPath(folderPath)
        } else {
            mediaRepository.getVideosFlow()
        }

        return combine(
            videosFlow,
            preferencesRepository.applicationPreferences,
        ) { videoItems, preferences ->
            val visibleVideos = videoItems.filterNot { video ->
                (!recycleBinOnly && preferences.isPathExcluded(video.parentPath)) ||
                    (!recycleBinOnly && preferences.recycleBinEnabled && video.isInRecycleBin)
            }

            val sort = Sort(by = preferences.sortBy, order = preferences.sortOrder)
            visibleVideos.sortedWith(sort.videoComparator())
        }.flowOn(defaultDispatcher)
    }
}
