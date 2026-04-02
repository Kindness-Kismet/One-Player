package one.next.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPreferencesTest {

    @Test
    fun defaultPlayerControlsLayout_usesDefaultLayout() {
        val preferences = PlayerPreferences()

        assertEquals(
            PlayerControlsLayout(),
            preferences.playerControlsLayout,
        )
    }
}
