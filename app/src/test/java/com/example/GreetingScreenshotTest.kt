package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.bot.BotRuntimeSnapshot
import com.example.bot.BotState
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun control_panel_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        ControlPanel(
          runtime = BotRuntimeSnapshot(
            isRunning = true,
            state = BotState.WAITING_BITE,
            lastConfidence = 0.87,
            lastVisionScore = 0.021,
            lastAction = "wait 120ms no_bite_signal",
            fishCaught = 12,
          ),
          accessibilityReady = true,
          onToggleBot = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/control_panel.png")
  }
}
