package one.next.player.core.data.remote

import android.util.Base64
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLDecoder
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import one.next.player.core.model.RemoteFile
import one.next.player.core.model.RemoteServer
import one.next.player.core.model.ServerProtocol
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class WebDavClient @Inject constructor() {

    // 列出指定路径的目录内容
    suspend fun listDirectory(
        server: RemoteServer,
        directoryPath: String,
    ): Result<List<RemoteFile>> = runCatching {
        if (server.protocol != ServerProtocol.WEBDAV) {
            error("WebDavClient only supports WEBDAV protocol")
        }

        val baseUrl = buildBaseUrl(server)
        val normalizedPath = directoryPath.ensureTrailingSlash()
        val url = "$baseUrl$normalizedPath"

        val client = buildClient(server)
        val requestBody = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder()
            .url(url)
            .method("PROPFIND", requestBody)
            .header("Depth", "1")
        applyAuth(requestBuilder, server)

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful && response.code != 207) {
            error("WebDAV PROPFIND failed: HTTP ${response.code}")
        }

        val body = response.body
        val files = parsePropfindResponse(body.byteStream(), normalizedPath)
        response.close()
        files
    }

    // 构建文件的完整可播放 URL
    fun buildFileUrl(server: RemoteServer, filePath: String): String {
        val baseUrl = buildBaseUrl(server)
        return "$baseUrl$filePath"
    }

    // 构建带认证的 header（供播放器使用）
    fun buildAuthHeaders(server: RemoteServer): Map<String, String> {
        if (server.username.isBlank()) return emptyMap()

        val credentials = "${server.username}:${server.password}"
        val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        return mapOf("Authorization" to "Basic $encoded")
    }

    private fun buildBaseUrl(server: RemoteServer): String {
        val host = server.host.trim()
        if (host.startsWith("http://") || host.startsWith("https://")) {
            val base = host.removeSuffix("/")
            val port = server.port ?: return base
            if (base.substringAfter("://").contains(':')) return base
            return "$base:$port"
        }
        val port = server.port?.let { ":$it" } ?: ""
        return "http://$host$port"
    }

    private fun buildClient(server: RemoteServer): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)

        if (server.isProxyEnabled && server.proxyHost.isNotBlank()) {
            builder.proxy(
                Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress(server.proxyHost, server.proxyPort ?: 8080),
                ),
            )
        }

        return builder.build()
    }

    private fun applyAuth(builder: Request.Builder, server: RemoteServer) {
        if (server.username.isBlank()) return

        val credentials = "${server.username}:${server.password}"
        val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        builder.header("Authorization", "Basic $encoded")
    }

    @Suppress("NestedBlockDepth")
    private fun parsePropfindResponse(
        inputStream: InputStream,
        requestPath: String,
    ): List<RemoteFile> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        val files = mutableListOf<RemoteFile>()
        var currentHref: String? = null
        var currentDisplayName: String? = null
        var currentContentLength: Long = 0
        var currentContentType = ""
        var isCollection = false
        var inResponse = false
        var currentTag = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "response") {
                        inResponse = true
                        currentHref = null
                        currentDisplayName = null
                        currentContentLength = 0
                        currentContentType = ""
                        isCollection = false
                    }
                    if (currentTag == "collection" && inResponse) {
                        isCollection = true
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inResponse) {
                        val text = parser.text?.trim().orEmpty()
                        when (currentTag) {
                            "href" -> currentHref = text
                            "displayname" -> currentDisplayName = text
                            "getcontentlength" -> currentContentLength = text.toLongOrNull() ?: 0
                            "getcontenttype" -> currentContentType = text
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "response" && inResponse) {
                        inResponse = false
                        val href = currentHref
                        if (href != null) {
                            val decodedHref = URLDecoder.decode(href, "UTF-8")
                            val decodedRequest = URLDecoder.decode(requestPath, "UTF-8")
                            val isSelf = decodedHref.removeSuffix("/") == decodedRequest.removeSuffix("/")
                            if (!isSelf) {
                                val name = currentDisplayName?.takeIf { it.isNotBlank() }
                                    ?: decodedHref.removeSuffix("/").substringAfterLast('/')
                                files.add(
                                    RemoteFile(
                                        name = name,
                                        path = href,
                                        isDirectory = isCollection,
                                        size = currentContentLength,
                                        contentType = currentContentType,
                                    ),
                                )
                            }
                        }
                    }
                    currentTag = ""
                }
            }
            parser.next()
        }

        return files.sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name })
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

    companion object {
        private val CONNECT_TIMEOUT = java.time.Duration.ofSeconds(15)
        private val READ_TIMEOUT = java.time.Duration.ofSeconds(30)

        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8" ?><D:propfind xmlns:D="DAV:"><D:allprop/></D:propfind>"""
    }
}
