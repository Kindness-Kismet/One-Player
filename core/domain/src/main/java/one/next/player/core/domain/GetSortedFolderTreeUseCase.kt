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
import one.next.player.core.model.ApplicationPreferences
import one.next.player.core.model.Folder
import one.next.player.core.model.Sort

class GetSortedFolderTreeUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(folderPath: String? = null): Flow<Folder?> = combine(
        mediaRepository.getFoldersFlow(),
        preferencesRepository.applicationPreferences,
    ) { folders, preferences ->
        val currentFolder = folderPath?.let {
            folders.find { it.path == folderPath } ?: return@combine null
        } ?: Folder.rootFolder

        val sort = Sort(by = preferences.sortBy, order = preferences.sortOrder)
        val visibleMedia = currentFolder.mediaList.filterNot { video ->
            preferences.recycleBinEnabled && video.isInRecycleBin
        }

        currentFolder.copy(
            mediaList = visibleMedia,
            folderList = folders.getFoldersFor(path = currentFolder.path, preferences = preferences),
        ).let { folder ->
            if (folderPath == null) folder.getInitialFolderWithContent() else folder
        }.let { folder ->
            folder.copy(
                mediaList = folder.mediaList.sortedWith(sort.videoComparator()),
                folderList = folder.folderList.sortedWith(sort.folderComparator()),
            )
        }
    }.flowOn(defaultDispatcher)

    private fun Folder.getInitialFolderWithContent(): Folder = when {
        mediaList.isEmpty() && folderList.size == 1 -> folderList.first().getInitialFolderWithContent()
        else -> this
    }

    private fun List<Folder>.getFoldersFor(
        path: String,
        preferences: ApplicationPreferences,
    ): List<Folder> = mapNotNull { directory ->
        if (directory.parentPath != path || preferences.isPathExcluded(directory.path)) {
            return@mapNotNull null
        }

        val childFolders = getFoldersFor(path = directory.path, preferences = preferences)
        val visibleMedia = directory.mediaList.filterNot { video ->
            preferences.recycleBinEnabled && video.isInRecycleBin
        }
        if (visibleMedia.isEmpty() && childFolders.isEmpty()) {
            return@mapNotNull null
        }

        Folder(
            name = directory.name,
            path = directory.path,
            dateModified = directory.dateModified,
            mediaList = visibleMedia,
            folderList = childFolders,
        )
    }
}
