package one.next.player.core.data.remote

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ImpersonationLevel
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.PipeShare
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.EnumSet

object SmbShareEnumerator {

    data class ShareInfo(
        val name: String,
        val type: Int,
        val remark: String,
    ) {
        val isDisk: Boolean get() = (type and 0x0FFFFFFF) == STYPE_DISKTREE
        val isHidden: Boolean get() = name.endsWith("$")
    }

    fun listShares(
        host: String,
        port: Int,
        auth: AuthenticationContext,
        config: SmbConfig,
    ): List<ShareInfo> {
        val client = SMBClient(config)
        val connection = client.connect(host, port)
        val session = connection.authenticate(auth)
        val pipeShare = session.connectShare("IPC$") as PipeShare

        val pipe = pipeShare.open(
            "srvsvc",
            SMB2ImpersonationLevel.Impersonation,
            EnumSet.of(AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE),
            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.noneOf(SMB2CreateOptions::class.java),
        )

        try {
            val bindAck = pipe.transact(buildBindPdu())
            verifyBindAck(bindAck)

            val response = pipe.transact(buildNetShareEnumRequest(host))
            return parseNetShareEnumResponse(response)
        } finally {
            pipe.close()
            pipeShare.close()
            session.close()
            connection.close()
            client.close()
        }
    }

    private fun buildBindPdu(): ByteArray {
        val buf = ByteBuffer.allocate(72).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(5).put(0)
        buf.put(11)
        buf.put(3)
        buf.putInt(0x00000010)
        buf.putShort(72)
        buf.putShort(0)
        buf.putInt(1)
        buf.putShort(4280)
        buf.putShort(4280)
        buf.putInt(0)
        buf.put(1)
        buf.put(0).put(0).put(0)

        buf.putShort(0)
        buf.putShort(1)

        putUuid(buf, 0x4B324FC8, 0x1670, 0x01D3, byteArrayOf(0x12, 0x78, 0x5A, 0x47, 0xBF.toByte(), 0x6E, 0xE1.toByte(), 0x88.toByte()))
        buf.putInt(3)

        putUuid(buf, 0x8A885D04.toInt(), 0x1CEB, 0x11C9, byteArrayOf(0x9F.toByte(), 0xE8.toByte(), 0x08, 0x00, 0x2B, 0x10, 0x48, 0x60))
        buf.putInt(2)

        return buf.array()
    }

    private fun buildNetShareEnumRequest(hostname: String): ByteArray {
        val serverName = "\\\\$hostname"
        val serverNameChars = serverName.length + 1
        val serverNameBytes = serverNameChars * 2
        val serverNamePadding = (4 - (serverNameBytes % 4)) % 4

        val stubSize = 4 + 4 + 4 + 4 + serverNameBytes + serverNamePadding +
            4 + 4 + 4 + 4 + 4 + 4 + 4 + 4
        val headerSize = 24
        val fragLength = headerSize + stubSize

        val buf = ByteBuffer.allocate(fragLength).order(ByteOrder.LITTLE_ENDIAN)

        buf.put(5).put(0)
        buf.put(0)
        buf.put(3)
        buf.putInt(0x00000010)
        buf.putShort(fragLength.toShort())
        buf.putShort(0)
        buf.putInt(2)
        buf.putInt(stubSize)
        buf.putShort(0)
        buf.putShort(15)

        buf.putInt(0x00020000)
        buf.putInt(serverNameChars)
        buf.putInt(0)
        buf.putInt(serverNameChars)
        for (ch in serverName) {
            buf.putShort(ch.code.toShort())
        }
        buf.putShort(0)
        repeat(serverNamePadding) { buf.put(0) }

        buf.putInt(1)
        buf.putInt(1)

        buf.putInt(0x00020004)
        buf.putInt(0)
        buf.putInt(0)

        buf.putInt(-1)

        buf.putInt(0x00020008)
        buf.putInt(0)

        return buf.array()
    }

    private fun verifyBindAck(data: ByteArray) {
        if (data.size < 24) error("Bind ack too short")
        if (data[2].toInt() != 12) error("Expected Bind Ack (type 12), got ${data[2]}")
    }

    private fun parseNetShareEnumResponse(data: ByteArray): List<ShareInfo> {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(24)

        val level = buf.getInt()
        val switch = buf.getInt()
        if (level != 1 || switch != 1) return emptyList()

        val containerPtr = buf.getInt()
        if (containerPtr == 0) return emptyList()

        val entriesRead = buf.getInt()
        val bufferPtr = buf.getInt()
        if (entriesRead == 0 || bufferPtr == 0) return emptyList()

        buf.getInt()

        data class RawEntry(val namePtr: Int, val type: Int, val remarkPtr: Int)
        val entries = (0 until entriesRead).map {
            RawEntry(
                namePtr = buf.getInt(),
                type = buf.getInt(),
                remarkPtr = buf.getInt(),
            )
        }

        val shares = mutableListOf<ShareInfo>()
        for (entry in entries) {
            val name = if (entry.namePtr != 0) readNdrString(buf) else ""
            val remark = if (entry.remarkPtr != 0) readNdrString(buf) else ""
            shares.add(ShareInfo(name = name, type = entry.type, remark = remark))
        }

        return shares
    }

    private fun readNdrString(buf: ByteBuffer): String {
        val maxCount = buf.getInt()
        val offset = buf.getInt()
        val actualCount = buf.getInt()
        if (actualCount <= 0) return ""

        val byteLen = actualCount * 2
        val bytes = ByteArray(byteLen)
        buf.get(bytes)

        val padding = (4 - (byteLen % 4)) % 4
        repeat(padding) { buf.get() }

        val str = String(bytes, StandardCharsets.UTF_16LE)
        return str.trimEnd('\u0000')
    }

    private fun putUuid(buf: ByteBuffer, timeLow: Int, timeMid: Short, timeHi: Short, node: ByteArray) {
        buf.putInt(timeLow)
        buf.putShort(timeMid)
        buf.putShort(timeHi)
        buf.put(node)
    }

    private const val STYPE_DISKTREE = 0
}
