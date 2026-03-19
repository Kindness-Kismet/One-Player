package one.next.player.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import one.next.player.core.data.repository.LocalMediaRepository
import one.next.player.core.data.repository.LocalPreferencesRepository
import one.next.player.core.data.repository.LocalRemoteServerRepository
import one.next.player.core.data.repository.LocalSearchHistoryRepository
import one.next.player.core.data.repository.MediaRepository
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.data.repository.RemoteServerRepository
import one.next.player.core.data.repository.SearchHistoryRepository

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    fun bindsMediaRepository(
        videoRepository: LocalMediaRepository,
    ): MediaRepository

    @Binds
    @Singleton
    fun bindsPreferencesRepository(
        preferencesRepository: LocalPreferencesRepository,
    ): PreferencesRepository

    @Binds
    @Singleton
    fun bindsSearchHistoryRepository(
        searchHistoryRepository: LocalSearchHistoryRepository,
    ): SearchHistoryRepository

    @Binds
    @Singleton
    fun bindsRemoteServerRepository(
        remoteServerRepository: LocalRemoteServerRepository,
    ): RemoteServerRepository
}
