package one.next.player.feature.player

import android.content.Intent
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerControlsDragDropAppAndroidTest {

    @get:Rule
    val timeout: Timeout = Timeout(20, TimeUnit.SECONDS)

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun playerControlDragSource_hostStartsWithDisabledState() {
        launchHost(enabled = false)

        composeRule.onNodeWithTag("enabled_state").assertTextEquals("false")
        composeRule.onNodeWithTag("drop_count").assertTextEquals("0")
        composeRule.onNodeWithTag("dropped_control").assertTextEquals("null")
        composeRule.onNodeWithTag("dropped_offset").assertTextEquals("null")
    }

    @Test
    fun playerControlDragSource_hostStartsWithEnabledState() {
        launchHost(enabled = true)

        composeRule.onNodeWithTag("enabled_state").assertTextEquals("true")
        composeRule.onNodeWithTag("drop_count").assertTextEquals("0")
        composeRule.onNodeWithTag("dropped_control").assertTextEquals("null")
        composeRule.onNodeWithTag("dropped_offset").assertTextEquals("null")
    }

    private fun launchHost(enabled: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, PlayerControlsDragDropHostActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(PlayerControlsDragDropHostActivity.EXTRA_ENABLED, enabled)

        instrumentation.uiAutomation.adoptShellPermissionIdentity()
        try {
            instrumentation.targetContext.startActivity(intent)
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }

        composeRule.onNodeWithTag("drag_source")
        composeRule.waitForIdle()
    }
}
