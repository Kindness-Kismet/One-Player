package one.next.player.core.datastore.serializer

import androidx.datastore.core.CorruptionException
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPreferencesSerializerTest {

    @Test(expected = CorruptionException::class)
    fun readFrom_throwsForLegacyPlayerPreferencesJson() {
        runBlocking {
            val legacyJson = """
                {
                    "rememberPlayerBrightness": true,
                    "autoplay": false,
                    "useSwipeControls": false,
                    "enableVolumeSwipeGesture": false,
                    "pauseOnHeadsetDisconnect": false,
                    "useSystemCaptionStyle": true,
                    "subtitleTextSize": 16.0,
                    "shouldUseLibass": true
                }
            """.trimIndent()

            PlayerPreferencesSerializer.readFrom(
                ByteArrayInputStream(legacyJson.encodeToByteArray()),
            )
        }
    }

    @Test
    fun readFrom_readsCurrentPlayerPreferencesJson() = runBlocking {
        val currentJson = """
            {
                "shouldRememberPlayerBrightness": true,
                "shouldAutoPlay": false,
                "subtitleTextSize": 16
            }
        """.trimIndent()

        val result = PlayerPreferencesSerializer.readFrom(
            ByteArrayInputStream(currentJson.encodeToByteArray()),
        )

        assertEquals(true, result.shouldRememberPlayerBrightness)
        assertEquals(false, result.shouldAutoPlay)
        assertEquals(16, result.subtitleTextSize)
    }
}
