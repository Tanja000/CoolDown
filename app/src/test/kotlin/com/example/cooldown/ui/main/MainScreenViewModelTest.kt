package com.example.cooldown.ui.main

import com.example.cooldown.data.SpotCategory
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_initiallyAllCategoriesActive() = runTest {
    val viewModel = MainScreenViewModel()
    val state = viewModel.uiState.first()
    assertEquals(SpotCategory.entries.toSet(), state.activeCategories)
  }

  @Test
  fun selectExclusiveCategory_filtersOnlyThatCategoryAndCanDeselectAll() = runTest {
    val viewModel = MainScreenViewModel()
    viewModel.selectExclusiveCategory(SpotCategory.MUSEUM)
    assertEquals(setOf(SpotCategory.MUSEUM), viewModel.uiState.first().activeCategories)

    // Clicking again deselects all (category selection becomes empty)
    viewModel.selectExclusiveCategory(SpotCategory.MUSEUM)
    assertTrue(viewModel.uiState.first().activeCategories.isEmpty())

    // "Alle" button restores all categories
    viewModel.selectAllCategories()
    assertEquals(SpotCategory.entries.toSet(), viewModel.uiState.first().activeCategories)
  }

  @Test
  fun clearAllCategories_clearsAll() = runTest {
    val viewModel = MainScreenViewModel()
    viewModel.clearAllCategories()
    assertTrue(viewModel.uiState.first().activeCategories.isEmpty())
  }
}
