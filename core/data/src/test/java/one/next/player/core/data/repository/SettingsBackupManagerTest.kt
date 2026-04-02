package one.next.player.core.data.repository

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import one.next.player.core.model.ApplicationPreferences
import one.next.player.core.model.PlayerPreferences
import one.next.player.core.model.SettingsBackup
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackupManagerTest {

    private val backupManager = SettingsBackupManager()

    @Test
    fun writeAndRead_roundTripsCurrentBackupFormat() {
        val settingsBackup = SettingsBackup(
            applicationPreferences = ApplicationPreferences(
                appLanguage = "zh-CN",
                shouldHideInRecents = true,
            ),
            playerPreferences = PlayerPreferences(
                shouldAutoPlay = false,
                subtitleTextSize = 18,
            ),
        )
        val outputStream = ByteArrayOutputStream()

        backupManager.write(outputStream, settingsBackup)

        val restoredBackup = backupManager.read(
            ByteArrayInputStream(outputStream.toByteArray()),
        )

        assertEquals(settingsBackup, restoredBackup)
    }
}
