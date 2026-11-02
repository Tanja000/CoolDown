package com.findcoolplaceswithcooldown.ui.main

import com.findcoolplaceswithcooldown.data.CoolSpot
import com.findcoolplaceswithcooldown.data.SpotCategory
import com.findcoolplaceswithcooldown.data.TravelMode
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
  fun toggleCategory_supportsMultiSelection() = runTest {
    val viewModel = MainScreenViewModel()
    viewModel.clearAllCategories()
    assertTrue(viewModel.uiState.first().activeCategories.isEmpty())

    // Add first category
    viewModel.toggleCategory(SpotCategory.MUSEUM)
    assertEquals(setOf(SpotCategory.MUSEUM), viewModel.uiState.first().activeCategories)

    // Add second category (multi-selection)
    viewModel.toggleCategory(SpotCategory.PARK)
    assertEquals(
      setOf(SpotCategory.MUSEUM, SpotCategory.PARK),
      viewModel.uiState.first().activeCategories,
    )

    // Toggle first category off
    viewModel.toggleCategory(SpotCategory.MUSEUM)
    assertEquals(setOf(SpotCategory.PARK), viewModel.uiState.first().activeCategories)

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

  @Test
  fun travelMode_includesDriving() {
    val driving = TravelMode.DRIVING
    assertEquals("Auto", driving.titleDe)
    assertEquals("Car", driving.titleEn)
    assertEquals("driving", driving.googleMapsMode)
  }

  @Test
  fun multiSpotRoute_addsPinsCorrectly() = runTest {
    val viewModel = MainScreenViewModel()
    val spot1 = CoolSpot(
      id = 1L,
      lat = 52.52,
      lon = 13.40,
      category = SpotCategory.DRINKING_WATER,
      name = "Brunnen 1",
    )
    val spot2 = CoolSpot(
      id = 2L,
      lat = 52.53,
      lon = 13.41,
      category = SpotCategory.PARK,
      name = "Park 1",
    )

    viewModel.selectSpot(spot1, autoCalculateRoute = false)
    assertEquals(spot1, viewModel.uiState.first().selectedSpot)

    viewModel.addSpotToRoute(spot1)
    viewModel.addSpotToRoute(spot2)
    assertEquals(spot2, viewModel.uiState.first().selectedSpot)

    // Test changing travel mode
    viewModel.changeTravelMode(TravelMode.DRIVING)
    assertEquals(TravelMode.DRIVING, viewModel.uiState.first().selectedTravelMode)

    // Test route overview sheet toggle
    viewModel.setShowRouteOverviewSheet(true)
    assertTrue(viewModel.uiState.first().showRouteOverviewSheet)

    // Test removing a spot from route updates remaining pins
    viewModel.removeSpotFromRoute(spot1.id)
    val stateAfterRemoval = viewModel.uiState.first()
    assertEquals(1, stateAfterRemoval.activeRoute?.stopCount)
    assertEquals(spot2.id, stateAfterRemoval.activeRoute?.targetSpots?.first()?.id)

    // Test removing last spot clears route
    viewModel.removeSpotFromRoute(spot2.id)
    val stateAfterEmpty = viewModel.uiState.first()
    assertEquals(null, stateAfterEmpty.activeRoute)
  }

  @Test
  fun iceCreamSupport_togglesDialogAndKeepsAdsActive() = runTest {
    val viewModel = MainScreenViewModel()
    assertEquals(false, viewModel.uiState.first().isAdFree)
    assertEquals(false, viewModel.uiState.first().showIceCreamDialog)

    viewModel.setShowIceCreamDialog(true)
    assertTrue(viewModel.uiState.first().showIceCreamDialog)

    viewModel.supportWithIceCream("ice_cream_medium")
    // Ads remain active after donation (no ad-free promise) and dialog closes
    assertEquals(false, viewModel.uiState.first().isAdFree)
    assertEquals(false, viewModel.uiState.first().showIceCreamDialog)
  }

  @Test
  fun gdprConsent_togglesPersonalizedAdsAndDialog() = runTest {
    val viewModel = MainScreenViewModel()
    assertEquals(true, viewModel.uiState.first().personalizedAdsConsent)

    viewModel.setPersonalizedAdsConsent(false)
    assertEquals(false, viewModel.uiState.first().personalizedAdsConsent)
    assertTrue(viewModel.uiState.first().hasSeenConsentBanner)

    viewModel.setShowLegalPrivacyDialog(true)
    assertTrue(viewModel.uiState.first().showLegalPrivacyDialog)
    viewModel.setShowLegalPrivacyDialog(false)
    assertEquals(false, viewModel.uiState.first().showLegalPrivacyDialog)
  }

  @Test
  fun activeRoute_spotsRemainVisibleWhenCategoryDeselected() = runTest {
    val viewModel = MainScreenViewModel()
    val spot = CoolSpot(
      id = 42L,
      lat = 52.52,
      lon = 13.40,
      category = SpotCategory.ICE_CREAM,
      name = "Gelateria",
    )
    viewModel.addSpotToRoute(spot)

    // Deselect all categories
    viewModel.clearAllCategories()
    val state = viewModel.uiState.first()
    assertTrue(state.activeCategories.isEmpty())

    // Even though no category is selected, the route destination remains visible in filteredSpots!
    assertTrue(state.filteredSpots.any { it.id == 42L })
  }
}
