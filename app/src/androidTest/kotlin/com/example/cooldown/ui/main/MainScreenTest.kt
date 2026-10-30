package com.example.cooldown.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.cooldown.data.SpotCategory
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.cooldown.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      CoolDownMapScreen(
        uiState = CoolDownUiState(),
        viewModel = MainScreenViewModel(),
      )
    }
  }

  @Test
  fun drinkingWaterFilterChip_exists() {
    composeTestRule.onNodeWithText(SpotCategory.DRINKING_WATER.titleDe).assertExists()
  }
}
