package one.next.player.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_server")
data class RemoteServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int?,
    val path: String,
    val username: String,
    val password: String,
    @ColumnInfo(name = "is_proxy_enabled")
    val isProxyEnabled: Boolean,
    @ColumnInfo(name = "proxy_host")
    val proxyHost: String,
    @ColumnInfo(name = "proxy_port")
    val proxyPort: Int?,
)
