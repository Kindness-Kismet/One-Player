package one.next.player.core.datastore.serializer

import androidx.datastore.core.CorruptionException
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationPreferencesSerializerTest {

    @Test(expected = CorruptionException::class)
    fun readFrom_throwsForLegacyApplicationPreferencesJson() {
        runBlocking {
            val legacyJson = """
                {
                    "useDynamicColors": false,
                    "markLastPlayedMedia": false,
                    "showThumbnailField": false
                }
            """.trimIndent()

            ApplicationPreferencesSerializer.readFrom(
                ByteArrayInputStream(legacyJson.encodeToByteArray()),
            )
        }
    }

    @Test
    fun readFrom_readsCurrentApplicationPreferencesJson() = runBlocking {
        val currentJson = """
            {
                "appLanguage": "zh-CN",
                "shouldUseDynamicColors": false,
                "shouldShowThumbnailField": false
            }
        """.trimIndent()

        val result = ApplicationPreferencesSerializer.readFrom(
            ByteArrayInputStream(currentJson.encodeToByteArray()),
        )

        assertEquals("zh-CN", result.appLanguage)
        assertEquals(false, result.shouldUseDynamicColors)
        assertEquals(false, result.shouldShowThumbnailField)
    }
}
