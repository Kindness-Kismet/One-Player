package one.next.player

import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.next.player.core.common.extensions.applyPrivacyProtection
import one.next.player.core.common.extensions.resolvePrivacyPreviewScrim
import one.next.player.core.common.storagePermission
import one.next.player.core.media.services.MediaService
import one.next.player.core.media.sync.MediaSynchronizer
import one.next.player.core.model.ThemeConfig
import one.next.player.core.ui.composables.rememberRuntimePermissionState
import one.next.player.core.ui.theme.OnePlayerTheme
import one.next.player.navigation.MediaRootRoute
import one.next.player.navigation.cloudNavGraph
import one.next.player.navigation.mediaNavGraph
import one.next.player.navigation.settingsNavGraph

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val AUTO_REFRESH_INTERVAL_MILLIS = 30_000L

        // 进程级时间戳，Activity 重建后不会重置，进程死亡后归零触发全量刷新
        @Volatile
        private var lastAutoRefreshAt = 0L
    }

    @Inject
    lateinit var synchronizer: MediaSynchronizer

    @Inject
    lateinit var mediaService: MediaService

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val persistedThemeConfig = readPersistedThemeConfig(dataDir = applicationInfo.dataDir)
        val bootstrapTheme = resolveBootstrapTheme(
            themeConfig = persistedThemeConfig,
            isSystemDarkTheme = isSystemDarkTheme(resources.configuration),
        )
        setTheme(resolveBootstrapSplashThemeStyle(shouldUseDarkTheme = bootstrapTheme.shouldUseDarkTheme))
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        val bootstrapShouldHideInRecents = readPersistedHideInRecents(dataDir = applicationInfo.dataDir)
        applyPrivacyProtection(
            shouldPreventScreenshots = viewModel.currentPreferences.shouldPreventScreenshots,
            shouldHideInRecents = viewModel.currentPreferences.shouldHideInRecents,
        )
        mediaService.initialize(this@MainActivity)
        applySystemBars(
            shouldHideInRecents = bootstrapShouldHideInRecents,
            shouldUseDarkTheme = bootstrapTheme.shouldUseDarkTheme,
        )

        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    uiState = state
                }
            }
        }

        splashScreen.setKeepOnScreenCondition {
            when (uiState) {
                MainActivityUiState.Loading -> true
                is MainActivityUiState.Success -> false
            }
        }
        setContent {
            val shouldUseDarkTheme = shouldUseDarkTheme(uiState = uiState)

            val preferences = (uiState as? MainActivityUiState.Success)?.preferences
            val shouldPreventScreenshots = preferences?.shouldPreventScreenshots == true
            val shouldHideInRecents = preferences?.shouldHideInRecents == true

            LaunchedEffect(shouldPreventScreenshots, shouldHideInRecents) {
                if (preferences == null) return@LaunchedEffect
                this@MainActivity.applyPrivacyProtection(
                    shouldPreventScreenshots = shouldPreventScreenshots,
                    shouldHideInRecents = shouldHideInRecents,
                )
            }

            LaunchedEffect(shouldHideInRecents, shouldUseDarkTheme) {
                applySystemBars(
                    shouldHideInRecents = shouldHideInRecents,
                    shouldUseDarkTheme = shouldUseDarkTheme,
                )
            }

            OnePlayerTheme(
                shouldUseDarkTheme = shouldUseDarkTheme,
                shouldUseDynamicColor = shouldUseDynamicTheming(uiState = uiState),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    val storagePermissionState = rememberRuntimePermissionState(permission = storagePermission)

                    LifecycleEventEffect(event = Lifecycle.Event.ON_START) {
                        storagePermissionState.launchPermissionRequest()
                    }

                    LaunchedEffect(storagePermissionState.isGranted) {
                        if (!storagePermissionState.isGranted) return@LaunchedEffect

                        synchronizer.startSync()
                        if (lastAutoRefreshAt != 0L) return@LaunchedEffect

                        // 延迟 refresh，让 UI 先用 DB 缓存数据渲染
                        delay(2000)
                        synchronizer.refresh()
                        lastAutoRefreshAt = SystemClock.elapsedRealtime()
                    }

                    LifecycleEventEffect(event = Lifecycle.Event.ON_RESUME) {
                        if (!storagePermissionState.isGranted) return@LifecycleEventEffect

                        val now = SystemClock.elapsedRealtime()
                        if (now - lastAutoRefreshAt < AUTO_REFRESH_INTERVAL_MILLIS) return@LifecycleEventEffect

                        lifecycleScope.launch {
                            synchronizer.refresh()
                            lastAutoRefreshAt = SystemClock.elapsedRealtime()
                        }
                    }

                    val mainNavController = rememberNavController()

                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                testTagsAsResourceId = true
                            },
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        NavHost(
                            navController = mainNavController,
                            startDestination = MediaRootRoute,
                            enterTransition = {
                                slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = LinearEasing,
                                    ),
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = LinearEasing,
                                    ),
                                    targetOffset = { fullOffset -> (fullOffset * 0.3f).toInt() },
                                )
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = LinearEasing,
                                    ),
                                    initialOffset = { fullOffset -> (fullOffset * 0.3f).toInt() },
                                )
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = LinearEasing,
                                    ),
                                )
                            },
                        ) {
                            mediaNavGraph(
                                context = this@MainActivity,
                                navController = mainNavController,
                            )
                            cloudNavGraph(
                                context = this@MainActivity,
                                navController = mainNavController,
                            )
                            settingsNavGraph(navController = mainNavController)
                        }
                    }
                }
            }
        }
    }

    private fun applySystemBars(
        shouldHideInRecents: Boolean,
        shouldUseDarkTheme: Boolean,
    ) {
        val systemBarScrim = resolvePrivacyPreviewScrim(shouldHideInRecents)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = systemBarScrim,
                darkScrim = systemBarScrim,
                detectDarkMode = { shouldUseDarkTheme },
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = systemBarScrim,
                darkScrim = systemBarScrim,
                detectDarkMode = { shouldUseDarkTheme },
            ),
        )
    }
}

internal data class BootstrapThemeResolution(
    val shouldUseDarkTheme: Boolean,
)

internal fun resolveBootstrapTheme(
    themeConfig: ThemeConfig,
    isSystemDarkTheme: Boolean,
): BootstrapThemeResolution = when (themeConfig) {
    ThemeConfig.SYSTEM -> BootstrapThemeResolution(shouldUseDarkTheme = isSystemDarkTheme)
    ThemeConfig.OFF -> BootstrapThemeResolution(shouldUseDarkTheme = false)
    ThemeConfig.ON -> BootstrapThemeResolution(shouldUseDarkTheme = true)
}

internal fun readPersistedThemeConfig(dataDir: String): ThemeConfig {
    val preferencesFile = File(dataDir, "files/datastore/app_preferences.json")
    if (!preferencesFile.exists()) return ThemeConfig.SYSTEM

    val rawConfig = runCatching { preferencesFile.readText() }
        .getOrNull()
        ?.let(THEME_CONFIG_PATTERN::find)
        ?.groupValues
        ?.getOrNull(1)
        ?: return ThemeConfig.SYSTEM

    return ThemeConfig.entries.firstOrNull { it.name == rawConfig } ?: ThemeConfig.SYSTEM
}

internal fun readPersistedHideInRecents(dataDir: String): Boolean {
    val preferencesFile = File(dataDir, "files/datastore/app_preferences.json")
    if (!preferencesFile.exists()) return false

    return runCatching { preferencesFile.readText() }
        .getOrNull()
        ?.let(HIDE_IN_RECENTS_PATTERN::find)
        ?.groupValues
        ?.getOrNull(1)
        ?.toBooleanStrictOrNull()
        ?: false
}

private fun resolveBootstrapSplashThemeStyle(shouldUseDarkTheme: Boolean): Int = if (shouldUseDarkTheme) {
    one.next.player.R.style.Theme_OnePlayer_Splash_Dark
} else {
    one.next.player.R.style.Theme_OnePlayer_Splash_Light
}

private fun isSystemDarkTheme(configuration: Configuration): Boolean = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

private val THEME_CONFIG_PATTERN = "\"themeConfig\"\\s*:\\s*\"([A-Z_]+)\"".toRegex()
private val HIDE_IN_RECENTS_PATTERN = "\"shouldHideInRecents\"\\s*:\\s*(true|false)".toRegex()

@Composable
fun shouldUseDarkTheme(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> isSystemInDarkTheme()
    is MainActivityUiState.Success -> when (uiState.preferences.themeConfig) {
        ThemeConfig.SYSTEM -> isSystemInDarkTheme()
        ThemeConfig.OFF -> false
        ThemeConfig.ON -> true
    }
}

@Composable
fun shouldUseDynamicTheming(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> uiState.preferences.shouldUseDynamicColors
}
