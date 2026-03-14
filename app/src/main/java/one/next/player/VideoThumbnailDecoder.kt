package one.next.player

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.get
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.ContentMetadata
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toAndroidUri
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import kotlin.math.abs
import okio.FileSystem
import one.next.player.core.common.Logger

class VideoThumbnailDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val strategy: ThumbnailStrategy,
    private val diskCache: Lazy<DiskCache?>,
) : Decoder {

    companion object {
        private const val TAG = "VideoThumbnailDecoder"

        // 缩略图最大尺寸，避免 4K 全分辨率 Bitmap 占用过多内存
        private const val MAX_THUMBNAIL_SIZE = 512
    }

    // 优先使用系统缩略图服务，质量优于 FFmpeg 提取帧
    private fun tryLoadSystemThumbnail(): Bitmap? {
        val uri = when (val metadata = source.metadata) {
            is ContentMetadata -> metadata.uri.toAndroidUri()
            else -> {
                if (source.fileSystem !== FileSystem.SYSTEM) return null
                findContentUriForPath(source.file().toFile().path) ?: return null
            }
        }
        val start = System.currentTimeMillis()
        return try {
            options.context.contentResolver.loadThumbnail(
                uri,
                Size(MAX_THUMBNAIL_SIZE, MAX_THUMBNAIL_SIZE),
                null,
            ).also {
                Logger.info(TAG, "systemThumbnail ok ${System.currentTimeMillis() - start}ms uri=$uri")
            }
        } catch (e: Exception) {
            Logger.info(TAG, "systemThumbnail fail ${System.currentTimeMillis() - start}ms uri=$uri err=${e.message}")
            null
        }
    }

    // 通过文件路径查询 MediaStore 获取 content:// URI
    private fun findContentUriForPath(path: String): android.net.Uri? {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.Video.Media._ID)
        return try {
            options.context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Video.Media.DATA} = ?",
                arrayOf(path),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    ContentUris.withAppendedId(collection, id)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private val diskCacheKey: String
        get() = options.diskCacheKey ?: run {
            val metadata = source.metadata
            when {
                metadata is ContentMetadata -> metadata.uri.toAndroidUri().toString()
                source.fileSystem === FileSystem.SYSTEM -> source.file().toFile().path
                else -> error("Not supported")
            }
        }

    @OptIn(ExperimentalCoilApi::class)
    override suspend fun decode(): DecodeResult {
        Logger.info(TAG, "decode start key=$diskCacheKey")
        readFromDiskCache()?.use { snapshot ->
            val file = snapshot.data.toFile()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)

            val cachedBitmap = BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
                },
            )

            if (cachedBitmap != null) {
                return DecodeResult(
                    image = cachedBitmap.toDrawable(options.context.resources).asImage(),
                    isSampled = true,
                )
            }
        }

        // 系统缩略图快速路径（content:// URI，由系统优化且自带缓存）
        tryLoadSystemThumbnail()?.let { systemBitmap ->
            val bitmap = writeToDiskCache(systemBitmap)
            return DecodeResult(
                image = bitmap.toDrawable(options.context.resources).asImage(),
                isSampled = true,
            )
        }

        // FFmpeg 提取帧（全格式通用，不阻塞）
        val ffmpegStart = System.currentTimeMillis()
        getThumbnailFromMediaInfo()?.scaleToFit()?.let { rawBitmap ->
            Logger.info(TAG, "mediaInfo ok ${System.currentTimeMillis() - ffmpegStart}ms key=$diskCacheKey")
            val bitmap = writeToDiskCache(rawBitmap)
            return DecodeResult(
                image = bitmap.toDrawable(options.context.resources).asImage(),
                isSampled = true,
            )
        }

        throw IllegalStateException("Failed to get video thumbnail for key=$diskCacheKey")
    }

    private fun getThumbnailFromMediaInfo(): Bitmap? {
        val metadata = source.metadata
        val mediaInfo = try {
            when {
                metadata is ContentMetadata -> {
                    MediaInfoBuilder().from(
                        context = options.context,
                        uri = metadata.uri.toAndroidUri(),
                    ).build()
                }
                source.fileSystem === FileSystem.SYSTEM -> {
                    MediaInfoBuilder().from(
                        filePath = source.file().toFile().path,
                    ).build()
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        } ?: return null

        return try {
            when (strategy) {
                is ThumbnailStrategy.FirstFrame -> mediaInfo.getFrame()
                is ThumbnailStrategy.FrameAtPercentage -> {
                    val timeMs = (mediaInfo.duration * strategy.percentage).toLong()
                    mediaInfo.getFrameAt(timeMs)
                }
                is ThumbnailStrategy.Hybrid -> {
                    val firstFrame = mediaInfo.getFrame()
                    if (firstFrame != null && isSolidColor(firstFrame)) {
                        val timeMs = (mediaInfo.duration * strategy.percentage).toLong()
                        mediaInfo.getFrameAt(timeMs) ?: firstFrame
                    } else {
                        firstFrame
                    }
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            mediaInfo.release()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        if (width <= MAX_THUMBNAIL_SIZE && height <= MAX_THUMBNAIL_SIZE) return 1
        var inSampleSize = 1
        val maxDimension = maxOf(width, height)
        while (maxDimension / (inSampleSize * 2) >= MAX_THUMBNAIL_SIZE) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun Bitmap.scaleToFit(): Bitmap {
        if (width <= MAX_THUMBNAIL_SIZE && height <= MAX_THUMBNAIL_SIZE) return this
        val scale = MAX_THUMBNAIL_SIZE.toFloat() / maxOf(width, height)
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
        if (scaled !== this) recycle()
        return scaled
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? = if (options.diskCachePolicy.readEnabled) {
        diskCache.value?.openSnapshot(diskCacheKey)
    } else {
        null
    }

    private fun writeToDiskCache(inBitmap: Bitmap): Bitmap {
        if (!options.diskCachePolicy.writeEnabled) return inBitmap
        val editor = diskCache.value?.openEditor(diskCacheKey) ?: return inBitmap
        try {
            editor.data.toFile().outputStream().use { output ->
                inBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
            }
            editor.commitAndOpenSnapshot()?.use { snapshot ->
                val outBitmap = snapshot.data.toFile().inputStream().use { input ->
                    BitmapFactory.decodeStream(input)
                }
                inBitmap.recycle()
                return outBitmap
            }
        } catch (_: Exception) {
            try {
                editor.abort()
            } catch (_: Exception) {
            }
        }
        return inBitmap
    }

    class Factory(
        private val thumbnailStrategy: () -> ThumbnailStrategy,
    ) : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            Logger.info(TAG, "Factory.create mimeType=${result.mimeType}")
            if (!isApplicable(result.mimeType)) return null
            return VideoThumbnailDecoder(
                source = result.source,
                options = options,
                strategy = thumbnailStrategy(),
                diskCache = lazy { imageLoader.diskCache },
            )
        }

        private fun isApplicable(mimeType: String?): Boolean = mimeType != null && mimeType.startsWith("video/")
    }
}

sealed class ThumbnailStrategy {
    data object FirstFrame : ThumbnailStrategy()
    data class FrameAtPercentage(val percentage: Float = 0.5f) : ThumbnailStrategy()
    data class Hybrid(val percentage: Float = 0.5f) : ThumbnailStrategy()
}

private fun isSolidColor(bitmap: Bitmap, threshold: Float = 0.7f): Boolean {
    val width = bitmap.width
    val height = bitmap.height

    // 采样中心区域网格，避开黑边干扰
    val marginX = width / 10
    val marginY = height / 10
    val sampleAreaRight = width - marginX
    val sampleAreaBottom = height - marginY

    // 构建采样点网格
    val gridSize = 10
    val stepX = (sampleAreaRight - marginX) / gridSize
    val stepY = (sampleAreaBottom - marginY) / gridSize

    if (stepX <= 0 || stepY <= 0) return false

    val sampledColors = mutableListOf<Int>()

    for (x in 0 until gridSize) {
        for (y in 0 until gridSize) {
            val pixelX = marginX + x * stepX
            val pixelY = marginY + y * stepY
            if (pixelX < width && pixelY < height) {
                sampledColors.add(bitmap[pixelX, pixelY])
            }
        }
    }

    if (sampledColors.isEmpty()) return false

    // 以首个颜色作为参考值
    val referenceColor = sampledColors[0]
    val referenceR = (referenceColor shr 16) and 0xFF
    val referenceG = (referenceColor shr 8) and 0xFF
    val referenceB = referenceColor and 0xFF

    // 统计容差内的相似颜色数量
    val tolerance = 30 // RGB 容差
    val similarCount = sampledColors.count { color ->
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        abs(r - referenceR) <= tolerance &&
            abs(g - referenceG) <= tolerance &&
            abs(b - referenceB) <= tolerance
    }

    val similarityRatio = similarCount.toFloat() / sampledColors.size
    return similarityRatio >= threshold
}
