package one.next.player.core.data.remote

import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import one.next.player.core.model.RemoteFile
import one.next.player.core.model.RemoteServer
import one.next.player.core.model.ServerProtocol

class SmbClient @Inject constructor() {

    // 列出 SMB 共享上的目录内容
    suspend fun listDirectory(
        server: RemoteServer,
        directoryPath: String,
    ): Result<List<RemoteFile>> = runCatching {
        if (server.protocol != ServerProtocol.SMB) {
            error("SmbClient only supports SMB protocol")
        }

        val shareName = extractShareName(server.path)
        val relativePath = extractRelativePath(server.path, directoryPath)

        val config = SmbConfig.builder()
            .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withSoTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val client = SMBClient(config)
        val connection = client.connect(server.host, server.port ?: DEFAULT_PORT)
        val authContext = server.toSmbAuthContext()
        val session = connection.authenticate(authContext)
        val share = session.connectShare(shareName) as DiskShare

        val files = mutableListOf<RemoteFile>()
        val listing = share.list(relativePath)

        for (info in listing) {
            val name = info.fileName
            if (name == "." || name == "..") continue

            val isDirectory = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            val size = info.endOfFile

            val fullPath = if (directoryPath.endsWith("/")) {
                "$directoryPath$name"
            } else {
                "$directoryPath/$name"
            }

            files.add(
                RemoteFile(
                    name = name,
                    path = if (isDirectory) "$fullPath/" else fullPath,
                    isDirectory = isDirectory,
                    size = size,
                ),
            )
        }

        share.close()
        session.close()
        connection.close()
        client.close()

        files.sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name })
    }

    companion object {
        const val DEFAULT_PORT = 445
        const val TIMEOUT_SECONDS = 15L

        // 从 SMB 路径提取共享名
        fun extractShareName(serverPath: String): String {
            val trimmed = serverPath.removePrefix("/").removeSuffix("/")
            return trimmed.substringBefore("/").ifBlank { trimmed }
        }

        // 从完整路径中提取共享内的相对路径，返回 Windows 风格反斜杠路径
        fun extractRelativePath(serverPath: String, directoryPath: String): String {
            val shareName = extractShareName(serverPath)
            val normalizedServerPath = serverPath.removePrefix("/").removeSuffix("/")
            val serverRelative = normalizedServerPath.removePrefix(shareName).removePrefix("/")
            val normalizedDirectoryPath = directoryPath.removePrefix("/").removeSuffix("/")
            val relativeToShare = normalizedDirectoryPath.removePrefix("$shareName/")
                .removePrefix(shareName)
                .removePrefix("/")

            val combined = when {
                relativeToShare.isBlank() -> serverRelative
                serverRelative.isBlank() -> relativeToShare
                relativeToShare == serverRelative -> serverRelative
                relativeToShare.startsWith("$serverRelative/") -> relativeToShare
                else -> "$serverRelative/$relativeToShare"
            }

            return combined.replace("/", "\\")
        }

        fun RemoteServer.toSmbAuthContext(): AuthenticationContext {
            if (username.isBlank()) return AuthenticationContext.anonymous()

            val domain = username.substringBefore('\\', missingDelimiterValue = "")
                .substringBefore('/', missingDelimiterValue = "")
            val account = username.substringAfterLast('\\').substringAfterLast('/')

            return AuthenticationContext(account, password.toCharArray(), domain)
        }
    }
}
