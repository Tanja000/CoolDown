package com.findcoolplaceswithcooldown.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findcoolplaceswithcooldown.data.AppLanguage
import com.findcoolplaceswithcooldown.data.BoundingBoxCoords
import com.findcoolplaceswithcooldown.data.CoolSpot
import com.findcoolplaceswithcooldown.data.CoolSpotCacheManager
import com.findcoolplaceswithcooldown.data.NominatimResult
import com.findcoolplaceswithcooldown.data.OverpassService
import com.findcoolplaceswithcooldown.data.RouteInfo
import com.findcoolplaceswithcooldown.data.SpotCategory
import com.findcoolplaceswithcooldown.data.TravelMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoolDownUiState(
  val allSpots: List<CoolSpot> = emptyList(),
  val activeCategories: Set<SpotCategory> = SpotCategory.entries.toSet(),
  val selectedSpot: CoolSpot? = null,
  val activeRoute: RouteInfo? = null,
  val isCalculatingRoute: Boolean = false,
  val selectedTravelMode: TravelMode = TravelMode.WALKING,
  val isLoading: Boolean = false,
  val isShowingCachedResults: Boolean = false,
  val errorMessage: String? = null,
  val userLat: Double? = null,
  val userLon: Double? = null,
  val mapCenterLat: Double = 52.5200, // Default: Berlin Mitte
  val mapCenterLon: Double = 13.4050,
  val mapZoom: Double = 15.2,
  val hasMovedMapSinceFetch: Boolean = false,
  val searchQuery: String = "",
  val searchResults: List<NominatimResult> = emptyList(),
  val isSearchingLocation: Boolean = false,
  val showSpotListSheet: Boolean = false,
  val showRouteOverviewSheet: Boolean = false,
  val showLegendDialog: Boolean = false,
  val selectedMapStyle: FreeMapStyle = FreeMapStyle.OSM_DE,
  val showMapStyleDialog: Boolean = false,
  val language: AppLanguage = AppLanguage.DE,
  val isAdFree: Boolean = false,
  val personalizedAdsConsent: Boolean = true,
  val hasSeenConsentBanner: Boolean = true,
  val showIceCreamDialog: Boolean = false,
  val showLegalPrivacyDialog: Boolean = false,
) {
  val isAllCategoriesSelected: Boolean
    get() = activeCategories.size == SpotCategory.entries.size

  val isNoCategorySelected: Boolean
    get() = activeCategories.isEmpty()

  val filteredSpots: List<CoolSpot>
    get() {
      val refLat = userLat ?: mapCenterLat
      val refLon = userLon ?: mapCenterLon
      val routeSpots = activeRoute?.targetSpots.orEmpty()

      val categorySpots = if (activeCategories.isEmpty()) {
        emptyList()
      } else {
        allSpots.filter { it.category in activeCategories }
      }

      // Ziel und Zwischenziele einer ausgewählten Route bleiben immer sichtbar, auch bei Kategoriewechsel!
      val missingRouteSpots = routeSpots.filter { routeSpot ->
        categorySpots.none { it.id == routeSpot.id }
      }

      return (categorySpots + missingRouteSpots)
        .sortedBy { it.distanceMetersTo(refLat, refLon) }
    }

  val categoryCounts: Map<SpotCategory, Int>
    get() = allSpots.groupingBy { it.category }.eachCount()
}

class MainScreenViewModel(
  private val overpassService: OverpassService = OverpassService(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(CoolDownUiState())
  val uiState: StateFlow<CoolDownUiState> = _uiState.asStateFlow()

  private var cacheManager: CoolSpotCacheManager? = null
  private var hasInitializedCache = false
  private var fetchJob: Job? = null
  private var searchJob: Job? = null
  private var routeJob: Job? = null
  private var lastFetchedBbox: BoundingBoxCoords? = null

  fun initializeWithCacheOrFetch(
    context: Context,
    currentGpsLocation: Pair<Double, Double>?,
  ): Pair<Double, Double> {
    if (cacheManager == null) {
      cacheManager = CoolSpotCacheManager(context.applicationContext)
      val savedLang = cacheManager?.getSavedLanguage() ?: AppLanguage.DE
      val personalizedConsent = cacheManager?.getPersonalizedAdsConsent() ?: true
      val seenConsent = cacheManager?.hasSeenConsentBanner() ?: false
      _uiState.update {
        it.copy(
          language = savedLang,
          isAdFree = false,
          personalizedAdsConsent = personalizedConsent,
          hasSeenConsentBanner = seenConsent,
        )
      }
    }
    if (hasInitializedCache) {
      return _uiState.value.mapCenterLat to _uiState.value.mapCenterLon
    }
    hasInitializedCache = true

    val cached = cacheManager?.loadCache()
    if (cached != null && cached.spots.isNotEmpty()) {
      val positionUnchanged = if (currentGpsLocation != null) {
        cacheManager?.isSamePosition(
          cached = cached,
          currentLat = currentGpsLocation.first,
          currentLon = currentGpsLocation.second,
        ) == true
      } else {
        true
      }

      if (positionUnchanged) {
        val targetLat = currentGpsLocation?.first ?: cached.centerLat
        val targetLon = currentGpsLocation?.second ?: cached.centerLon
        lastFetchedBbox = BoundingBoxCoords(
          south = targetLat - 0.012,
          west = targetLon - 0.018,
          north = targetLat + 0.012,
          east = targetLon + 0.018,
        )
        _uiState.update {
          it.copy(
            allSpots = cached.spots,
            mapCenterLat = targetLat,
            mapCenterLon = targetLon,
            mapZoom = cached.zoom,
            userLat = currentGpsLocation?.first ?: it.userLat,
            userLon = currentGpsLocation?.second ?: it.userLon,
            isShowingCachedResults = true,
            isLoading = false,
            hasMovedMapSinceFetch = false,
          )
        }
        return targetLat to targetLon
      }
    }

    val startLat = currentGpsLocation?.first ?: cached?.centerLat ?: _uiState.value.mapCenterLat
    val startLon = currentGpsLocation?.second ?: cached?.centerLon ?: _uiState.value.mapCenterLon
    val bbox = BoundingBoxCoords(
      south = startLat - 0.012,
      west = startLon - 0.018,
      north = startLat + 0.012,
      east = startLon + 0.018,
    )
    onMapViewportChanged(
      centerLat = startLat,
      centerLon = startLon,
      zoom = _uiState.value.mapZoom,
      bbox = bbox,
      triggerAutoFetch = true,
    )
    return startLat to startLon
  }

  fun toggleLanguage() {
    val newLang = if (_uiState.value.language == AppLanguage.DE) AppLanguage.EN else AppLanguage.DE
    cacheManager?.saveLanguage(newLang)
    _uiState.update { it.copy(language = newLang) }
  }

  fun setLanguage(lang: AppLanguage) {
    cacheManager?.saveLanguage(lang)
    _uiState.update { it.copy(language = lang) }
  }

  fun setShowIceCreamDialog(show: Boolean) {
    _uiState.update { it.copy(showIceCreamDialog = show) }
  }

  fun setShowLegalPrivacyDialog(show: Boolean) {
    _uiState.update { it.copy(showLegalPrivacyDialog = show) }
  }

  fun setPersonalizedAdsConsent(consent: Boolean) {
    cacheManager?.setPersonalizedAdsConsent(consent)
    _uiState.update {
      it.copy(
        personalizedAdsConsent = consent,
        hasSeenConsentBanner = true,
      )
    }
  }

  fun dismissConsentBanner(acceptPersonalized: Boolean) {
    setPersonalizedAdsConsent(acceptPersonalized)
  }

  /**
   * Verarbeitet eine Eis-Spende (Werbung bleibt weiterhin aktiv, wie gewünscht).
   */
  fun supportWithIceCream(tier: String) {
    _uiState.update { it.copy(showIceCreamDialog = false) }
  }

  fun restorePurchases() {
    // One-time consumable donations do not disable ads
  }

  fun onMapViewportChanged(
    centerLat: Double,
    centerLon: Double,
    zoom: Double,
    bbox: BoundingBoxCoords,
    triggerAutoFetch: Boolean = false,
  ) {
    val movedSignificantly = lastFetchedBbox?.let { prev ->
      val prevCenterLat = (prev.south + prev.north) / 2.0
      val prevCenterLon = (prev.west + prev.east) / 2.0
      kotlin.math.abs(centerLat - prevCenterLat) > 0.0025 ||
        kotlin.math.abs(centerLon - prevCenterLon) > 0.0035
    } ?: false

    _uiState.update {
      it.copy(
        mapCenterLat = centerLat,
        mapCenterLon = centerLon,
        mapZoom = zoom,
        hasMovedMapSinceFetch = if (triggerAutoFetch) false else (it.hasMovedMapSinceFetch || movedSignificantly),
      )
    }

    if (triggerAutoFetch || lastFetchedBbox == null) {
      fetchSpotsInBounds(bbox, zoom)
    }
  }

  fun fetchSpotsInBounds(bbox: BoundingBoxCoords, zoom: Double = _uiState.value.mapZoom) {
    fetchJob?.cancel()
    fetchJob = viewModelScope.launch {
      _uiState.update {
        it.copy(
          isLoading = true,
          errorMessage = null,
          hasMovedMapSinceFetch = false,
        )
      }
      val includeTrees = zoom >= 15.0
      val result = overpassService.fetchCoolSpots(bbox, includeIndividualTrees = includeTrees)
      result.fold(
        onSuccess = { spots ->
          lastFetchedBbox = bbox
          val centerLat = (bbox.south + bbox.north) / 2.0
          val centerLon = (bbox.west + bbox.east) / 2.0
          cacheManager?.saveCache(
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = zoom,
            spots = spots,
          )
          _uiState.update { state ->
            val updatedSelected = state.selectedSpot?.let { sel ->
              spots.find { it.id == sel.id && it.category == sel.category } ?: sel
            }
            state.copy(
              allSpots = spots,
              selectedSpot = updatedSelected,
              isLoading = false,
              isShowingCachedResults = false,
              errorMessage = null,
            )
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = if (it.language == AppLanguage.EN) {
                "Loading failed: ${err.localizedMessage ?: "Network error"}"
              } else {
                "Laden fehlgeschlagen: ${err.localizedMessage ?: "Netzwerkfehler"}"
              },
            )
          }
        },
      )
    }
  }

  fun selectSpot(spot: CoolSpot?, autoCalculateRoute: Boolean = true) {
    if (spot == null) {
      _uiState.update { it.copy(selectedSpot = null) }
      return
    }

    val currentRoute = _uiState.value.activeRoute
    if (currentRoute != null && autoCalculateRoute) {
      if (currentRoute.containsSpot(spot.id)) {
        // Spot is already part of the route -> just open its details so user can inspect or delete it
        _uiState.update { it.copy(selectedSpot = spot) }
      } else {
        // New spot -> add to existing multi-stop route
        addSpotToRoute(spot, _uiState.value.selectedTravelMode)
      }
    } else {
      _uiState.update {
        it.copy(selectedSpot = spot)
      }
      if (autoCalculateRoute) {
        calculateRouteToSpot(spot, _uiState.value.selectedTravelMode)
      }
    }
  }

  fun addSpotToRoute(
    spot: CoolSpot,
    travelMode: TravelMode = _uiState.value.selectedTravelMode,
  ) {
    val existingRoute = _uiState.value.activeRoute
    val currentSpots = existingRoute?.targetSpots.orEmpty()
    val updatedSpots = if (currentSpots.any { it.id == spot.id }) {
      currentSpots
    } else {
      currentSpots + spot
    }

    val state = _uiState.value
    val originLat = state.userLat ?: state.mapCenterLat
    val originLon = state.userLon ?: state.mapCenterLon
    val reordered = overpassService.optimizeWaypointOrder(originLat, originLon, updatedSpots)

    var totalDist = 0
    var prevLat = originLat
    var prevLon = originLon
    val polyline = mutableListOf(originLat to originLon)
    for (s in reordered) {
      totalDist += s.distanceMetersTo(prevLat, prevLon)
      polyline.add(s.lat to s.lon)
      prevLat = s.lat
      prevLon = s.lon
    }
    val fallbackSpeed = when (travelMode) {
      TravelMode.WALKING -> 1.3
      TravelMode.BICYCLING -> 4.0
      TravelMode.DRIVING -> 8.0
    }

    val immediateRoute = RouteInfo(
      targetSpots = reordered,
      originLat = originLat,
      originLon = originLon,
      travelMode = travelMode,
      distanceMeters = totalDist.coerceAtLeast(1),
      durationSeconds = (totalDist / fallbackSpeed).toInt().coerceAtLeast(60),
      polylinePoints = polyline,
    )

    _uiState.update {
      it.copy(
        activeRoute = immediateRoute,
        selectedSpot = spot,
        selectedTravelMode = travelMode,
        isCalculatingRoute = true,
      )
    }

    calculateMultiStopRoute(reordered, travelMode)
  }

  fun closeDetailCardKeepRoute() {
    _uiState.update { it.copy(selectedSpot = null) }
  }

  fun calculateMultiStopRoute(
    spots: List<CoolSpot>,
    travelMode: TravelMode = _uiState.value.selectedTravelMode,
  ) {
    if (spots.isEmpty()) {
      clearRoute()
      return
    }
    routeJob?.cancel()
    _uiState.update {
      it.copy(
        selectedTravelMode = travelMode,
        isCalculatingRoute = true,
      )
    }
    routeJob = viewModelScope.launch {
      val state = _uiState.value
      val originLat = state.userLat ?: state.mapCenterLat
      val originLon = state.userLon ?: state.mapCenterLon
      val result = overpassService.calculateMultiRoute(
        originLat = originLat,
        originLon = originLon,
        destinations = spots,
        travelMode = travelMode,
      )
      result.onSuccess { route ->
        _uiState.update {
          it.copy(
            activeRoute = route,
            isCalculatingRoute = false,
          )
        }
      }.onFailure {
        _uiState.update { it.copy(isCalculatingRoute = false) }
      }
    }
  }

  fun calculateRouteToSpot(
    spot: CoolSpot? = _uiState.value.selectedSpot,
    travelMode: TravelMode = _uiState.value.selectedTravelMode,
  ) {
    val targetSpot = spot ?: return
    val existingRoute = _uiState.value.activeRoute
    val spots = if (existingRoute != null && existingRoute.targetSpots.isNotEmpty()) {
      if (existingRoute.targetSpots.any { it.id == targetSpot.id }) {
        existingRoute.targetSpots
      } else {
        existingRoute.targetSpots + targetSpot
      }
    } else {
      listOf(targetSpot)
    }
    calculateMultiStopRoute(spots, travelMode)
  }

  fun changeTravelMode(mode: TravelMode) {
    _uiState.update { state ->
      val updatedRoute = state.activeRoute?.copy(travelMode = mode)
      state.copy(
        selectedTravelMode = mode,
        activeRoute = updatedRoute,
      )
    }
    val route = _uiState.value.activeRoute
    if (route != null && route.targetSpots.isNotEmpty()) {
      calculateMultiStopRoute(route.targetSpots, mode)
    }
  }

  fun setShowRouteOverviewSheet(show: Boolean) {
    _uiState.update { it.copy(showRouteOverviewSheet = show) }
  }

  fun removeSpotFromRoute(spotId: Long) {
    val currentRoute = _uiState.value.activeRoute ?: return
    val remaining = currentRoute.targetSpots.filterNot { it.id == spotId }
    if (remaining.isEmpty()) {
      clearRoute()
      _uiState.update { it.copy(showRouteOverviewSheet = false, selectedSpot = null) }
      return
    }

    val state = _uiState.value
    val originLat = state.userLat ?: state.mapCenterLat
    val originLon = state.userLon ?: state.mapCenterLon

    // Immediately re-optimize the remaining pins in TSP order
    val reorderedRemaining = overpassService.optimizeWaypointOrder(originLat, originLon, remaining)
    val keepSelected = state.selectedSpot?.takeIf { it.id != spotId }

    // Synchronously update UI state and map polyline with the reordered remaining pins
    var totalDist = 0
    var prevLat = originLat
    var prevLon = originLon
    val polyline = mutableListOf(originLat to originLon)
    for (spot in reorderedRemaining) {
      totalDist += spot.distanceMetersTo(prevLat, prevLon)
      polyline.add(spot.lat to spot.lon)
      prevLat = spot.lat
      prevLon = spot.lon
    }
    val fallbackSpeed = when (currentRoute.travelMode) {
      TravelMode.WALKING -> 1.3
      TravelMode.BICYCLING -> 4.0
      TravelMode.DRIVING -> 8.0
    }

    val immediateRoute = RouteInfo(
      targetSpots = reorderedRemaining,
      originLat = originLat,
      originLon = originLon,
      travelMode = currentRoute.travelMode,
      distanceMeters = totalDist.coerceAtLeast(1),
      durationSeconds = (totalDist / fallbackSpeed).toInt().coerceAtLeast(60),
      polylinePoints = polyline,
    )

    _uiState.update {
      it.copy(
        activeRoute = immediateRoute,
        selectedSpot = keepSelected,
        isCalculatingRoute = true,
      )
    }

    // Launch detailed turn-by-turn routing calculation in background
    calculateMultiStopRoute(reorderedRemaining, currentRoute.travelMode)
  }

  fun clearRoute() {
    routeJob?.cancel()
    _uiState.update {
      it.copy(
        activeRoute = null,
        isCalculatingRoute = false,
        showRouteOverviewSheet = false,
      )
    }
  }

  /**
   * Multi-selection of categories:
   * Clicking a category toggles its presence in activeCategories (multi-selection).
   */
  fun toggleCategory(category: SpotCategory) {
    _uiState.update { state ->
      val nextCategories = if (category in state.activeCategories) {
        state.activeCategories - category
      } else {
        state.activeCategories + category
      }
      val keepSelected = state.selectedSpot?.takeIf { it.category in nextCategories }
      state.copy(
        activeCategories = nextCategories,
        selectedSpot = keepSelected,
      )
    }
  }

  fun selectExclusiveCategory(category: SpotCategory) = toggleCategory(category)

  fun toggleAllCategories() {
    _uiState.update { state ->
      val next = if (state.isAllCategoriesSelected) emptySet() else SpotCategory.entries.toSet()
      val keepSelected = state.selectedSpot?.takeIf { it.category in next }
      state.copy(
        activeCategories = next,
        selectedSpot = keepSelected,
      )
    }
  }

  fun selectAllCategories() {
    _uiState.update { it.copy(activeCategories = SpotCategory.entries.toSet()) }
  }

  fun clearAllCategories() {
    _uiState.update { it.copy(activeCategories = emptySet(), selectedSpot = null) }
  }

  fun updateUserLocation(lat: Double, lon: Double) {
    _uiState.update { it.copy(userLat = lat, userLon = lon) }
  }

  fun onSearchQueryChanged(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
    searchJob?.cancel()
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
      _uiState.update { it.copy(searchResults = emptyList(), isSearchingLocation = false) }
      return
    }
    searchJob = viewModelScope.launch {
      delay(200)
      _uiState.update { it.copy(isSearchingLocation = true) }
      overpassService.searchCityOrAddress(trimmed).fold(
        onSuccess = { results ->
          _uiState.update { it.copy(searchResults = results, isSearchingLocation = false) }
        },
        onFailure = {
          _uiState.update { it.copy(searchResults = emptyList(), isSearchingLocation = false) }
        },
      )
    }
  }

  fun clearSearch() {
    _uiState.update {
      it.copy(searchQuery = "", searchResults = emptyList(), isSearchingLocation = false)
    }
  }

  fun setShowSpotListSheet(show: Boolean) {
    _uiState.update { it.copy(showSpotListSheet = show) }
  }

  fun setShowLegendDialog(show: Boolean) {
    _uiState.update { it.copy(showLegendDialog = show) }
  }

  fun setMapStyle(style: FreeMapStyle) {
    _uiState.update { it.copy(selectedMapStyle = style, showMapStyleDialog = false) }
  }

  fun setShowMapStyleDialog(show: Boolean) {
    _uiState.update { it.copy(showMapStyleDialog = show) }
  }

  fun dismissError() {
    _uiState.update { it.copy(errorMessage = null) }
  }
}
