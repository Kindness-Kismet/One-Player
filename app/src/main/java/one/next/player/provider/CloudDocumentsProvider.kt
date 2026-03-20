package one.next.player.provider

import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.share.DiskShare
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.FileNotFoundException
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import one.next.player.core.data.remote.SmbClient
import one.next.player.core.data.remote.SmbClient.Companion.extractRelativePath
import one.next.player.core.data.remote.SmbClient.Companion.extractShareName
import one.next.player.core.data.remote.SmbClient.Companion.toSmbAuthContext
import one.next.player.core.data.remote.SmbShareEnumerator
import one.next.player.core.data.remote.WebDavClient
import one.next.player.core.data.repository.RemoteServerRepository
import one.next.player.core.model.RemoteFile
import one.next.player.core.model.RemoteServer
import one.next.player.core.model.ServerProtocol

class CloudDocumentsProvider : DocumentsProvider() {

    private val remoteServerRepository: RemoteServerRepository by lazy {
        entryPoint.remoteServerRepository()
    }
    private val webDavClient: WebDavClient by lazy {
        entryPoint.webDavClient()
    }

    private val entryPoint: CloudDocumentsProviderEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context!!.applicationContext,
            CloudDocumentsProviderEntryPoint::class.java,
        )
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): MatrixCursor {
        val result = MatrixCursor(resolveRootProjection(projection))
        val servers = getServers()
        if (servers.isEmpty()) return result

        result.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
            add(DocumentsContract.Root.COLUMN_TITLE, ROOT_TITLE)
            add(DocumentsContract.Root.COLUMN_SUMMARY, buildRootSummary(servers.size))
            add(DocumentsContract.Root.COLUMN_FLAGS, ROOT_FLAGS)
            add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_upload)
            add(DocumentsContract.Root.COLUMN_MIME_TYPES, ROOT_MIME_TYPES)
            add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, -1)
        }

        return result
    }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): MatrixCursor {
        val result = MatrixCursor(resolveDocumentProjection(projection))
        appendDocumentRow(result, documentId)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): MatrixCursor {
        val result = MatrixCursor(resolveDocumentProjection(projection))

        if (parentDocumentId == ROOT_DOCUMENT_ID) {
            getServers().forEach { server ->
                appendServerRow(result, server)
            }
            return result
        }

        val parsed = parseDocumentId(parentDocumentId)
        val server = getServer(parsed.serverId) ?: return result

        listFiles(server, parsed.path).forEach { file ->
            appendFileRow(
                result = result,
                server = server,
                file = file,
            )
        }
        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        if (!mode.startsWith("r")) {
            throw FileNotFoundException("Only read mode is supported")
        }
        if (documentId == ROOT_DOCUMENT_ID) {
            throw FileNotFoundException("Root document cannot be opened")
        }

        val parsed = parseDocumentId(documentId)
        val server = getServer(parsed.serverId) ?: throw FileNotFoundException("Server not found")
        if (parsed.path.isBlank() || parsed.path == "/") {
            throw FileNotFoundException("Document path is empty")
        }

        return when (server.protocol) {
            ServerProtocol.WEBDAV -> openWebDavDocument(server, parsed.path, signal)
            ServerProtocol.SMB -> openSmbDocument(server, parsed.path, signal)
        }
    }

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ): Boolean {
        if (parentDocumentId == ROOT_DOCUMENT_ID) {
            return documentId != ROOT_DOCUMENT_ID
        }

        val parent = parseDocumentId(parentDocumentId)
        val child = parseDocumentId(documentId)
        if (parent.serverId != child.serverId) return false
        if (parent.path == "/") return child.path != "/"
        return child.path.startsWith(parent.path)
    }

    override fun getDocumentType(documentId: String): String {
        if (documentId == ROOT_DOCUMENT_ID) return DocumentsContract.Document.MIME_TYPE_DIR

        val parsed = parseDocumentId(documentId)
        if (parsed.path == "/") return DocumentsContract.Document.MIME_TYPE_DIR

        val server = getServer(parsed.serverId) ?: return FALLBACK_VIDEO_MIME
        val fileName = parsed.path.substringAfterLast('/')
        val files = listFiles(server, parentPathOf(parsed.path))
        val file = files.firstOrNull { it.path.removeSuffix("/") == parsed.path.removeSuffix("/") }
        if (file?.isDirectory == true) return DocumentsContract.Document.MIME_TYPE_DIR
        return resolveMimeType(fileName, file?.contentType)
    }

    override fun findDocumentPath(
        parentDocumentId: String?,
        childDocumentId: String,
    ): DocumentsContract.Path? {
        if (childDocumentId == ROOT_DOCUMENT_ID) {
            return DocumentsContract.Path(ROOT_ID, listOf(ROOT_DOCUMENT_ID))
        }

        val parsed = parseDocumentId(childDocumentId)
        val server = getServer(parsed.serverId) ?: return null
        val path = mutableListOf(ROOT_DOCUMENT_ID, buildServerDocumentId(server.id))

        if (parsed.path != "/") {
            path += buildDocumentPathSegments(server, parsed.path)
        }

        if (parentDocumentId != null && parentDocumentId !in path) {
            return null
        }

        return DocumentsContract.Path(ROOT_ID, path)
    }

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?,
    ): MatrixCursor {
        val result = MatrixCursor(resolveDocumentProjection(projection))

        if (rootId != ROOT_ID) return result

        getServers().forEach { server ->
            listFiles(server, server.path.ensureDirectoryPath())
                .filter { !it.isDirectory }
                .filter { it.name.contains(query, ignoreCase = true) }
                .forEach { file ->
                    appendFileRow(
                        result = result,
                        server = server,
                        file = file,
                    )
                }
        }
        return result
    }

    private fun appendDocumentRow(result: MatrixCursor, documentId: String) {
        if (documentId == ROOT_DOCUMENT_ID) {
            result.newRow().apply {
                add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
                add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, ROOT_TITLE)
                add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                add(DocumentsContract.Document.COLUMN_FLAGS, DIRECTORY_FLAGS)
                add(DocumentsContract.Document.COLUMN_ICON, android.R.drawable.ic_menu_upload)
                add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
                add(DocumentsContract.Document.COLUMN_SIZE, null)
            }
            return
        }

        val parsed = parseDocumentId(documentId)
        val server = getServer(parsed.serverId) ?: return

        if (parsed.path == "/") {
            appendServerRow(result, server)
            return
        }

        val files = listFiles(server, parentPathOf(parsed.path))
        val file = files.firstOrNull { it.path.removeSuffix("/") == parsed.path.removeSuffix("/") } ?: return
        appendFileRow(result, server, file)
    }

    private fun appendServerRow(
        result: MatrixCursor,
        server: RemoteServer,
    ) {
        result.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, buildServerDocumentId(server.id))
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, server.name.ifBlank { server.host })
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            add(DocumentsContract.Document.COLUMN_FLAGS, DIRECTORY_FLAGS)
            add(DocumentsContract.Document.COLUMN_ICON, android.R.drawable.ic_menu_upload)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
            add(DocumentsContract.Document.COLUMN_SIZE, null)
        }
    }

    private fun appendFileRow(
        result: MatrixCursor,
        server: RemoteServer,
        file: RemoteFile,
    ) {
        val documentId = buildDocumentId(server.id, file.path)
        val isDirectory = file.isDirectory
        result.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
            add(
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                if (isDirectory) {
                    DocumentsContract.Document.MIME_TYPE_DIR
                } else {
                    resolveMimeType(file.name, file.contentType)
                },
            )
            add(
                DocumentsContract.Document.COLUMN_FLAGS,
                if (isDirectory) DIRECTORY_FLAGS else FILE_FLAGS,
            )
            add(
                DocumentsContract.Document.COLUMN_ICON,
                if (isDirectory) android.R.drawable.ic_menu_agenda else android.R.drawable.ic_media_play,
            )
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
            add(DocumentsContract.Document.COLUMN_SIZE, file.size.takeIf { !isDirectory })
        }
    }

    private fun listFiles(server: RemoteServer, path: String): List<RemoteFile> = when (server.protocol) {
        ServerProtocol.WEBDAV -> kotlinx.coroutines.runBlocking {
            webDavClient.listDirectory(server, path).getOrElse { emptyList() }
        }
        ServerProtocol.SMB -> listSmbDirectory(server, path)
    }

    private fun listSmbDirectory(server: RemoteServer, path: String): List<RemoteFile> = runCatching {
        val config = SmbConfig.builder()
            .withTimeout(SmbClient.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withSoTimeout(SmbClient.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val isServerPathRoot = server.path.removePrefix("/").removeSuffix("/").isBlank()
        val isCurrentPathRoot = path.removePrefix("/").removeSuffix("/").isBlank()

        if (isServerPathRoot && isCurrentPathRoot) {
            val shares = SmbShareEnumerator.listShares(
                host = server.host,
                port = server.port ?: SmbClient.DEFAULT_PORT,
                auth = server.toSmbAuthContext(),
                config = config,
            )
            return@runCatching shares
                .filter { it.isDisk && !it.isHidden }
                .map { share ->
                    RemoteFile(
                        name = share.name,
                        path = "/${share.name}/",
                        isDirectory = true,
                        size = 0,
                    )
                }
                .sortedBy { it.name }
        }

        val shareName: String
        val relativePath: String

        if (isServerPathRoot) {
            val trimmed = path.removePrefix("/").removeSuffix("/")
            shareName = trimmed.substringBefore("/")
            relativePath = trimmed.substringAfter("/", missingDelimiterValue = "")
                .replace("/", "\\")
        } else {
            shareName = extractShareName(server.path)
            relativePath = extractRelativePath(server.path, path)
        }

        val client = SMBClient(config)
        val connection = client.connect(server.host, server.port ?: SmbClient.DEFAULT_PORT)
        val session = connection.authenticate(server.toSmbAuthContext())
        val share = session.connectShare(shareName) as DiskShare

        val files = mutableListOf<RemoteFile>()
        share.list(relativePath).forEach { info ->
            val name = info.fileName
            if (name == "." || name == "..") return@forEach

            val isDirectory = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            val fullPath = if (path.endsWith('/')) "$path$name" else "$path/$name"
            files.add(
                RemoteFile(
                    name = name,
                    path = if (isDirectory) "$fullPath/" else fullPath,
                    isDirectory = isDirectory,
                    size = info.endOfFile,
                ),
            )
        }

        share.close()
        session.close()
        connection.close()
        client.close()

        files.sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name })
    }.getOrDefault(emptyList())

    private fun openWebDavDocument(
        server: RemoteServer,
        path: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val targetUrl = webDavClient.buildFileUrl(server, path)
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        val requestBuilder = Request.Builder().url(targetUrl)
        webDavClient.buildAuthHeaders(server).forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        val response = httpClient.newCall(requestBuilder.build()).execute()
        val body = response.body ?: run {
            response.close()
            throw FileNotFoundException("Empty response body")
        }
        if (!response.isSuccessful) {
            body.close()
            response.close()
            throw FileNotFoundException("HTTP ${response.code}")
        }

        val pipe = ParcelFileDescriptor.createReliablePipe()
        signal?.setOnCancelListener {
            runCatching { pipe[0].close() }
            runCatching { pipe[1].close() }
            runCatching { body.close() }
            response.close()
        }

        Thread {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            response.close()
        }.start()
        return pipe[0]
    }

    private fun openSmbDocument(
        server: RemoteServer,
        path: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val isServerPathRoot = server.path.removePrefix("/").removeSuffix("/").isBlank()

        val shareName: String
        val relativePath: String

        if (isServerPathRoot) {
            val trimmed = path.removePrefix("/").removeSuffix("/")
            shareName = trimmed.substringBefore("/")
            relativePath = trimmed.substringAfter("/", missingDelimiterValue = "")
                .replace("/", "\\")
        } else {
            shareName = extractShareName(server.path)
            relativePath = extractRelativePath(server.path, path)
        }

        val config = SmbConfig.builder()
            .withTimeout(SmbClient.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withSoTimeout(SmbClient.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        val client = SMBClient(config)
        val connection = client.connect(server.host, server.port ?: SmbClient.DEFAULT_PORT)
        val session = connection.authenticate(server.toSmbAuthContext())
        val share = session.connectShare(shareName) as DiskShare
        val file = share.openFile(
            relativePath,
            EnumSet.of(AccessMask.GENERIC_READ),
            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.noneOf(com.hierynomus.mssmb2.SMB2CreateOptions::class.java),
        )
        val input = file.inputStream
        val pipe = ParcelFileDescriptor.createReliablePipe()

        signal?.setOnCancelListener {
            runCatching { pipe[0].close() }
            runCatching { pipe[1].close() }
            runCatching { input.close() }
            runCatching { file.close() }
            runCatching { share.close() }
            runCatching { session.close() }
            runCatching { connection.close() }
            runCatching { client.close() }
        }

        Thread {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                input.use { source ->
                    source.copyTo(output)
                }
            }
            file.close()
            share.close()
            session.close()
            connection.close()
            client.close()
        }.start()
        return pipe[0]
    }

    private fun getServers(): List<RemoteServer> = runCatching {
        kotlinx.coroutines.runBlocking {
            remoteServerRepository.getAll().first()
        }
    }.getOrDefault(emptyList())

    private fun getServer(serverId: Long): RemoteServer? = runCatching {
        kotlinx.coroutines.runBlocking {
            remoteServerRepository.getById(serverId)
        }
    }.getOrNull()

    private fun resolveRootProjection(projection: Array<out String>?): Array<String> {
        @Suppress("UNCHECKED_CAST")
        return projection as? Array<String> ?: DEFAULT_ROOT_PROJECTION
    }

    private fun resolveDocumentProjection(projection: Array<out String>?): Array<String> {
        @Suppress("UNCHECKED_CAST")
        return projection as? Array<String> ?: DEFAULT_DOCUMENT_PROJECTION
    }

    private fun buildRootSummary(serverCount: Int): String = "$serverCount 个云端位置"

    private fun buildServerDocumentId(serverId: Long): String = buildDocumentId(serverId, "/")

    private fun buildDocumentId(serverId: Long, path: String): String {
        val normalizedPath = if (path.isBlank()) "/" else path
        return "$serverId|${Uri.encode(normalizedPath)}"
    }

    private fun parseDocumentId(documentId: String): ParsedDocumentId {
        val separatorIndex = documentId.indexOf('|')
        if (separatorIndex <= 0) throw FileNotFoundException("Invalid documentId")
        val serverId = documentId.substring(0, separatorIndex).toLongOrNull()
            ?: throw FileNotFoundException("Invalid serverId")
        val encodedPath = documentId.substring(separatorIndex + 1)
        val decodedPath = Uri.decode(encodedPath).ifBlank { "/" }
        return ParsedDocumentId(serverId = serverId, path = decodedPath)
    }

    private fun parentPathOf(path: String): String {
        val normalized = path.removeSuffix("/")
        val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parent.isBlank()) "/" else "$parent/"
    }

    private fun buildDocumentPathSegments(
        server: RemoteServer,
        path: String,
    ): List<String> {
        val normalized = path.removePrefix("/").removeSuffix("/")
        if (normalized.isBlank()) return emptyList()

        val segments = normalized.split('/').filter { it.isNotBlank() }
        val documentIds = mutableListOf<String>()
        var currentPath = "/"

        if (server.protocol == ServerProtocol.SMB && server.path.removePrefix("/").removeSuffix("/").isBlank()) {
            for (segment in segments) {
                currentPath = if (currentPath == "/") "/$segment/" else "$currentPath$segment/"
                documentIds += buildDocumentId(server.id, currentPath)
            }
            return documentIds
        }

        val serverBasePath = server.path.ensureDirectoryPath()
        val serverBaseSegments = serverBasePath.removePrefix("/").removeSuffix("/")
            .split('/')
            .filter { it.isNotBlank() }
        var currentSegments = serverBaseSegments.toMutableList()
        val relativeSegments = segments.drop(serverBaseSegments.size)

        for (segment in relativeSegments) {
            currentSegments += segment
            currentPath = "/${currentSegments.joinToString("/")}/"
            documentIds += buildDocumentId(server.id, currentPath)
        }

        return documentIds
    }

    private fun resolveMimeType(
        name: String,
        declaredMimeType: String?,
    ): String {
        val cleanMimeType = declaredMimeType.orEmpty().takeIf {
            it.isNotBlank() && it != "application/octet-stream" && it != "audio/aac"
        }
        if (cleanMimeType != null) return cleanMimeType

        val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when {
                VIDEO_EXTENSIONS.contains(extension) -> FALLBACK_VIDEO_MIME
                SUBTITLE_EXTENSIONS.contains(extension) -> FALLBACK_SUBTITLE_MIME
                else -> FALLBACK_BINARY_MIME
            }
    }

    private fun String.ensureDirectoryPath(): String = if (endsWith('/')) this else "$this/"

    private data class ParsedDocumentId(
        val serverId: Long,
        val path: String,
    )

    companion object {
        private const val ROOT_ID = "cloud"
        private const val ROOT_DOCUMENT_ID = "root"
        private const val ROOT_TITLE = "One Player"
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val FALLBACK_VIDEO_MIME = "video/*"
        private const val FALLBACK_BINARY_MIME = "application/octet-stream"
        private const val FALLBACK_SUBTITLE_MIME = "application/x-subrip"
        private const val ROOT_MIME_TYPES = "video/*\napplication/x-matroska\nvideo/mp4\napplication/x-subrip\ntext/vtt\ntext/x-ssa\ntext/x-ass\napplication/ass\napplication/ssa\ntext/plain"

        private const val ROOT_FLAGS =
            DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD or
                DocumentsContract.Root.FLAG_SUPPORTS_SEARCH

        private const val DIRECTORY_FLAGS =
            DocumentsContract.Document.FLAG_DIR_PREFERS_GRID or
                DocumentsContract.Document.FLAG_DIR_PREFERS_LAST_MODIFIED

        private const val FILE_FLAGS = 0

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_ICON,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        private val VIDEO_EXTENSIONS = setOf(
            "3gp",
            "avi",
            "flv",
            "m2ts",
            "m4v",
            "mkv",
            "mov",
            "mp4",
            "mts",
            "ts",
            "webm",
            "wmv",
        )

        private val SUBTITLE_EXTENSIONS = setOf(
            "ass",
            "srt",
            "ssa",
            "sub",
            "vtt",
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudDocumentsProviderEntryPoint {
    fun remoteServerRepository(): RemoteServerRepository
    fun webDavClient(): WebDavClient
}
