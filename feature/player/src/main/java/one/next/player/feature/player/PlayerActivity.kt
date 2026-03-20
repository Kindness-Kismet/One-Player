package one.next.player.feature.player

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.util.Consumer
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import one.next.player.core.common.Logger
import one.next.player.core.common.extensions.applyPrivacyProtection
import one.next.player.core.common.extensions.getMediaContentUri
import one.next.player.core.common.extensions.resolvePrivacyPreviewScrim
import one.next.player.core.common.extensions.scanFileForContentUri
import one.next.player.core.common.storagePermission
import one.next.player.core.media.sync.MediaSynchronizer
import one.next.player.core.model.ScreenOrientation
import one.next.player.core.ui.theme.OnePlayerTheme
import one.next.player.feature.player.extensions.OpenDocumentWithInitialUri
import one.next.player.feature.player.extensions.registerForSuspendActivityResult
import one.next.player.feature.player.extensions.setExtras
import one.next.player.feature.player.extensions.uriToSubtitleConfiguration
import one.next.player.feature.player.service.PlayerService
import one.next.player.feature.player.service.addSubtitleTrack
import one.next.player.feature.player.service.stopPlayerSession
import one.next.player.feature.player.utils.PlayerApi

val LocalUseMaterialYouControls = compositionLocalOf { false }

@SuppressLint("UnsafeOptInUsageError")
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PlayerActivity"
    }

    @Inject
    lateinit var mediaSynchronizer: MediaSynchronizer

    private val viewModel: PlayerViewModel by viewModels()
    val playerPreferences get() = viewModel.uiState.value.playerPreferences

    private val onWindowAttributesChangedListener = CopyOnWriteArrayList<Consumer<WindowManager.LayoutParams?>>()

    private var isPlaybackFinished = false
    private var shouldPlayInBackground: Boolean = false
    private var isIntentNew: Boolean = true

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private lateinit var playerApi: PlayerApi

    private val playbackStateListener: Player.Listener = playbackStateListener()

    private val subtitleFileSuspendLauncher = registerForSuspendActivityResult(OpenDocumentWithInitialUri())
    private val mediaPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted ->
        if (!isGranted) return@registerForActivityResult

        lifecycleScope.launch {
            maybeInitControllerFuture()
            mediaController = controllerFuture?.await()
            mediaController?.run {
                updateKeepScreenOnFlag()
                addListener(playbackStateListener)
                startPlayback()
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPrivacyProtection(
            shouldPreventScreenshots = viewModel.uiState.value.shouldPreventScreenshots,
            shouldHideInRecents = viewModel.uiState.value.shouldHideInRecents,
        )
        presetVideoOrientation()
        val systemBarScrim = resolvePrivacyPreviewScrim(
            shouldHideInRecents = viewModel.uiState.value.shouldHideInRecents,
        )
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(systemBarScrim),
            navigationBarStyle = SystemBarStyle.dark(systemBarScrim),
        )

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var player by remember { mutableStateOf<MediaController?>(null) }

            LifecycleStartEffect(
                uiState.shouldPreventScreenshots,
                uiState.shouldHideInRecents,
            ) {
                this@PlayerActivity.applyPrivacyProtection(
                    shouldPreventScreenshots = uiState.shouldPreventScreenshots,
                    shouldHideInRecents = uiState.shouldHideInRecents,
                )
                onStopOrDispose {}
            }

            LifecycleStartEffect(Unit) {
                maybeInitControllerFuture()
                lifecycleScope.launch {
                    player = controllerFuture?.await()
                }

                onStopOrDispose {
                    player = null
                }
            }

            CompositionLocalProvider(LocalUseMaterialYouControls provides (uiState.playerPreferences?.shouldUseMaterialYouControls == true)) {
                OnePlayerTheme(shouldUseDarkTheme = true) {
                    MediaPlayerScreen(
                        modifier = Modifier.semantics {
                            testTagsAsResourceId = true
                        },
                        player = player,
                        viewModel = viewModel,
                        playerPreferences = uiState.playerPreferences ?: return@OnePlayerTheme,
                        onSelectSubtitleClick = {
                            lifecycleScope.launch {
                                val uri = subtitleFileSuspendLauncher.launch(
                                    OpenDocumentWithInitialUri.Input(
                                        mimeTypes = arrayOf(
                                            MimeTypes.APPLICATION_SUBRIP,
                                            MimeTypes.APPLICATION_TTML,
                                            MimeTypes.TEXT_VTT,
                                            MimeTypes.TEXT_SSA,
                                            MimeTypes.BASE_TYPE_APPLICATION + "/octet-stream",
                                            MimeTypes.BASE_TYPE_TEXT + "/*",
                                            MimeTypes.BASE_TYPE_AUDIO + "/aac",
                                        ),
                                        initialUri = intent.getParcelableExtra("initial_subtitle_directory_uri"),
                                    ),
                                ) ?: return@launch
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                maybeInitControllerFuture()
                                controllerFuture?.await()?.addSubtitleTrack(uri)
                            }
                        },
                        onBackClick = { finishAndStopPlayerSession() },
                        onPlayInBackgroundClick = {
                            shouldPlayInBackground = true
                            finish()
                        },
                        onScreenshotClick = {
                            lifecycleScope.launch {
                                val messageResId = runCatching {
                                    if (saveCurrentFrameScreenshot()) {
                                        one.next.player.core.ui.R.string.screenshot_saved
                                    } else {
                                        one.next.player.core.ui.R.string.screenshot_failed
                                    }
                                }.getOrElse {
                                    Logger.error(TAG, "Failed to take screenshot", it)
                                    one.next.player.core.ui.R.string.screenshot_failed
                                }
                                Toast.makeText(this@PlayerActivity, messageResId, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }

        playerApi = PlayerApi(this)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            if (!ensureMediaPermission()) return@launch

            maybeInitControllerFuture()
            mediaController = controllerFuture?.await()

            mediaController?.run {
                updateKeepScreenOnFlag()
                addListener(playbackStateListener)
                startPlayback()
            }
        }
    }

    override fun onStop() {
        mediaController?.run {
            viewModel.shouldPlayWhenReady = playWhenReady
            removeListener(playbackStateListener)
        }
        val shouldPlayInBackground = shouldPlayInBackground || playerPreferences?.shouldAutoPlayInBackground == true
        if (subtitleFileSuspendLauncher.isAwaitingResult || !shouldPlayInBackground) {
            mediaController?.pause()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            finish()
            if (!shouldPlayInBackground) {
                mediaController?.stopPlayerSession()
            }
        }

        controllerFuture?.run {
            MediaController.releaseFuture(this)
            controllerFuture = null
        }
        super.onStop()
    }

    private fun maybeInitControllerFuture() {
        if (controllerFuture == null) {
            val sessionToken = SessionToken(applicationContext, ComponentName(applicationContext, PlayerService::class.java))
            controllerFuture = MediaController.Builder(applicationContext, sessionToken).buildAsync()
        }
    }

    private fun startPlayback() {
        val uri = intent.data ?: return

        val isReturningFromBackground = !isIntentNew && mediaController?.currentMediaItem != null
        val isNewUriTheCurrentMediaItem = mediaController?.currentMediaItem?.localConfiguration?.uri.toString() == uri.toString()

        if (isReturningFromBackground || isNewUriTheCurrentMediaItem) {
            Logger.info(
                TAG,
                "startPlayback reused current item returning=$isReturningFromBackground same=$isNewUriTheCurrentMediaItem uri=$uri",
            )
            mediaController?.prepare()
            mediaController?.playWhenReady = viewModel.shouldPlayWhenReady
            return
        }

        isIntentNew = false

        lifecycleScope.launch {
            playVideo(uri)
        }
    }

    private suspend fun playVideo(uri: Uri) = withContext(Dispatchers.Default) {
        val t0 = System.currentTimeMillis()
        Logger.info(TAG, "playVideo start uri=$uri")

        val playbackUri = resolvePlaybackUri(uri)
        val requestHeaders = buildRequestHeadersFromIntent()
        if (uri.scheme == "file") {
            uri.path?.let { path ->
                mediaSynchronizer.registerManualVideoPath(path)
                Logger.info(TAG, "playVideo registeredManualPath=$path")
            }
        }
        val t1 = System.currentTimeMillis()
        Logger.info(TAG, "playVideo resolveUri=${t1 - t0}ms resolved=$playbackUri")

        val shouldBuildPlaylist = uri.scheme != "file" && playbackUri.scheme != "file"
        val playlist = playerApi.getPlaylist().takeIf { it.isNotEmpty() }
            ?: if (shouldBuildPlaylist) {
                viewModel.getPlaylistFromUri(playbackUri)
                    .map { it.uriString }
                    .toMutableList()
                    .apply {
                        if (!contains(playbackUri.toString())) {
                            add(index = 0, element = playbackUri.toString())
                        }
                    }
            } else {
                mutableListOf(playbackUri.toString())
            }
        val t2 = System.currentTimeMillis()
        Logger.info(TAG, "playVideo playlist=${t2 - t1}ms size=${playlist.size}")

        val mediaItemIndexToPlay = playlist.indexOfFirst {
            it == playbackUri.toString()
        }.takeIf { it >= 0 } ?: 0

        val mediaItems = playlist.mapIndexed { index, uri ->
            MediaItem.Builder().apply {
                setUri(uri)
                setMediaId(uri)
                if (index == mediaItemIndexToPlay) {
                    setMediaMetadata(
                        MediaMetadata.Builder().apply {
                            setTitle(playerApi.title)
                            setExtras(
                                positionMs = playerApi.position?.toLong(),
                                requestHeaders = requestHeaders,
                            )
                        }.build(),
                    )
                    val apiSubs = playerApi.getSubs().map { subtitle ->
                        uriToSubtitleConfiguration(
                            uri = subtitle.uri,
                            subtitleEncoding = playerPreferences?.subtitleTextEncoding ?: "",
                            isSelected = subtitle.isSelected,
                        )
                    }
                    setSubtitleConfigurations(apiSubs)
                }
            }.build()
        }

        withContext(Dispatchers.Main) {
            mediaController?.run {
                setMediaItems(mediaItems, mediaItemIndexToPlay, playerApi.position?.toLong() ?: C.TIME_UNSET)
                playWhenReady = viewModel.shouldPlayWhenReady
                prepare()
                Logger.info(TAG, "playVideo prepare total=${System.currentTimeMillis() - t0}ms")
            }
        }
    }

    private fun ensureMediaPermission(): Boolean {
        if (hasMediaReadPermission()) return true

        mediaPermissionLauncher.launch(storagePermission)
        return false
    }

    // file:// URI 在 scoped storage 下无法直接读取，需要逐级回退解析
    private suspend fun resolvePlaybackUri(uri: Uri): Uri {
        val t0 = System.currentTimeMillis()

        // file:// 已有路径，跳过 MediaStore 查询避免 ContentResolver 阻塞
        if (uri.scheme == "file") {
            val rawPath = uri.path ?: return uri
            val canonicalPath = runCatching { File(rawPath).canonicalPath }.getOrDefault(rawPath)
            Logger.info(TAG, "resolveUri canonical=${System.currentTimeMillis() - t0}ms path=$canonicalPath")

            if (File(canonicalPath).exists()) {
                Logger.info(TAG, "resolveUri fileFallback=${System.currentTimeMillis() - t0}ms")
                return Uri.fromFile(File(canonicalPath))
            }

            if (hasMediaReadPermission()) {
                scanFileForContentUri(path = canonicalPath, timeoutMs = 800L)?.let {
                    Logger.info(TAG, "resolveUri scanFile=${System.currentTimeMillis() - t0}ms result=$it")
                    return it
                }
                Logger.info(TAG, "resolveUri scanFileMiss=${System.currentTimeMillis() - t0}ms")
            } else {
                Logger.info(TAG, "resolveUri skipMediaStore noReadPermission=true")
            }
            return uri
        }

        if (hasMediaReadPermission()) {
            getMediaContentUri(uri)?.let {
                Logger.info(TAG, "resolveUri contentUri=${System.currentTimeMillis() - t0}ms")
                return it
            }
        }

        return uri
    }

    private fun buildRequestHeadersFromIntent(): Map<String, String> {
        val headerBundle = intent.getBundleExtra("headers") ?: return emptyMap()
        return buildMap {
            for (key in headerBundle.keySet()) {
                val value = headerBundle.getString(key).orEmpty()
                if (value.isNotEmpty()) put(key, value)
            }
        }
    }

    private fun hasMediaReadPermission(): Boolean = ContextCompat.checkSelfPermission(this, storagePermission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun playbackStateListener() = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            intent.data = mediaItem?.localConfiguration?.uri
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            updateKeepScreenOnFlag()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            when (playbackState) {
                Player.STATE_ENDED -> {
                    isPlaybackFinished = mediaController?.playbackState == Player.STATE_ENDED
                    finishAndStopPlayerSession()
                }

                else -> {}
            }
        }

        override fun onPlayWhenReadyChanged(shouldPlayWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(shouldPlayWhenReady, reason)

            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                if (mediaController?.repeatMode != Player.REPEAT_MODE_OFF) return
                isPlaybackFinished = true
                finishAndStopPlayerSession()
            }
        }
    }

    override fun finish() {
        if (playerApi.shouldReturnResult) {
            val result = playerApi.getResult(
                isPlaybackFinished = isPlaybackFinished,
                duration = mediaController?.duration ?: C.TIME_UNSET,
                position = mediaController?.currentPosition ?: C.TIME_UNSET,
            )
            setResult(RESULT_OK, result)
        }
        super.finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null) {
            setIntent(intent)
            isIntentNew = true
            if (mediaController != null) {
                startPlayback()
            }
        }
    }

    private fun updateKeepScreenOnFlag() {
        if (mediaController?.isPlaying == true) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private suspend fun saveCurrentFrameScreenshot(): Boolean = withContext(Dispatchers.Main) {
        val surfaceView = findPlayerSurfaceView() ?: return@withContext false
        if (surfaceView.width <= 0 || surfaceView.height <= 0) return@withContext false

        val bitmap = createBitmap(surfaceView.width, surfaceView.height)
        val copyResult = suspendCancellableCoroutine<Int> { continuation ->
            PixelCopy.request(surfaceView, bitmap, { result ->
                continuation.resume(result)
            }, Handler(mainLooper))
        }
        if (copyResult != PixelCopy.SUCCESS) return@withContext false

        withContext(Dispatchers.IO) {
            saveScreenshotBitmap(bitmap)
        }
    }

    private fun findPlayerSurfaceView(): SurfaceView? {
        val rootView = window.decorView.rootView ?: return null
        return findSurfaceView(rootView)
    }

    private fun findSurfaceView(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        val group = view as? android.view.ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            findSurfaceView(group.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun saveScreenshotBitmap(bitmap: android.graphics.Bitmap): Boolean {
        val fileName = buildScreenshotFileName()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Screenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(collection, contentValues) ?: return false
        return try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IOException("Failed to compress screenshot")
                }
            } ?: throw IOException("Failed to open screenshot output stream")

            contentResolver.update(
                uri,
                android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                },
                null,
                null,
            ) > 0
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            Logger.error(TAG, "Failed to save screenshot", e)
            false
        }
    }

    private fun buildScreenshotFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "Screenshot-$timestamp.png"
    }

    private fun finishAndStopPlayerSession() {
        finish()
        mediaController?.stopPlayerSession()
    }

    override fun onWindowAttributesChanged(params: WindowManager.LayoutParams?) {
        super.onWindowAttributesChanged(params)
        for (listener in onWindowAttributesChangedListener) {
            listener.accept(params)
        }
    }

    fun addOnWindowAttributesChangedListener(listener: Consumer<WindowManager.LayoutParams?>) {
        onWindowAttributesChangedListener.add(listener)
    }

    fun removeOnWindowAttributesChangedListener(listener: Consumer<WindowManager.LayoutParams?>) {
        onWindowAttributesChangedListener.remove(listener)
    }

    // 在 controller 连接前根据数据库预设方向，避免竖屏闪烁
    private fun presetVideoOrientation() {
        val prefs = playerPreferences ?: return
        if (prefs.playerScreenOrientation != ScreenOrientation.VIDEO_ORIENTATION) return
        val uri = intent.data ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val video = viewModel.getVideoByUri(uri.toString()) ?: return@launch
            if (video.width <= 0 || video.height <= 0) return@launch
            val orientation = if (video.height >= video.width) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            withContext(Dispatchers.Main) {
                if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    requestedOrientation = orientation
                }
            }
        }
    }
}
