package one.next.player.core.domain

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import one.next.player.core.common.Dispatcher
import one.next.player.core.common.NextDispatchers
import one.next.player.core.data.repository.MediaRepository
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.model.Folder
import one.next.player.core.model.Sort

class GetSortedFoldersUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(): Flow<List<Folder>> = combine(
        mediaRepository.getFoldersFlow(),
        preferencesRepository.applicationPreferences,
    ) { folders, preferences ->
        val visibleDirectories = folders.mapNotNull { folder ->
            if (preferences.isPathExcluded(folder.path)) {
                return@mapNotNull null
            }

            val visibleMedia = folder.mediaList.filterNot { video ->
                preferences.recycleBinEnabled && video.isInRecycleBin
            }
            if (visibleMedia.isEmpty()) {
                return@mapNotNull null
            }

            folder.copy(mediaList = visibleMedia)
        }

        val sort = Sort(by = preferences.sortBy, order = preferences.sortOrder)
        visibleDirectories.sortedWith(sort.folderComparator())
    }.flowOn(defaultDispatcher)
}
