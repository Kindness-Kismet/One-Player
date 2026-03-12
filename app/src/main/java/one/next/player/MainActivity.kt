package one.next.player

import android.graphics.Color
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
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.next.player.core.common.storagePermission
import one.next.player.core.media.services.MediaService
import one.next.player.core.media.sync.MediaSynchronizer
import one.next.player.core.model.ThemeConfig
import one.next.player.core.ui.composables.rememberRuntimePermissionState
import one.next.player.core.ui.theme.NextPlayerTheme
import one.next.player.navigation.MediaRootRoute
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaService.initialize(this@MainActivity)

        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    uiState = state
                }
            }
        }

        installSplashScreen().setKeepOnScreenCondition {
            when (uiState) {
                MainActivityUiState.Loading -> true
                is MainActivityUiState.Success -> false
            }
        }

        setContent {
            val shouldUseDarkTheme = shouldUseDarkTheme(uiState = uiState)

            LaunchedEffect(shouldUseDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                )
            }

            NextPlayerTheme(
                darkTheme = shouldUseDarkTheme,
                highContrastDarkTheme = shouldUseHighContrastDarkTheme(uiState = uiState),
                dynamicColor = shouldUseDynamicTheming(uiState = uiState),
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
                        settingsNavGraph(navController = mainNavController)
                    }
                }
            }
        }
    }
}

/**
 * Returns `true` if dark theme should be used, as a function of the [uiState] and the
 * current system context.
 */
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
fun shouldUseHighContrastDarkTheme(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> uiState.preferences.useHighContrastDarkTheme
}

/**
 * Returns `true` if the dynamic color is disabled, as a function of the [uiState].
 */
@Composable
fun shouldUseDynamicTheming(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> uiState.preferences.useDynamicColors
}
