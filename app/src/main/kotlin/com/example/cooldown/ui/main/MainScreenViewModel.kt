package com.example.cooldown.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cooldown.data.AppLanguage
import com.example.cooldown.data.BoundingBoxCoords
import com.example.cooldown.data.CoolSpot
import com.example.cooldown.data.CoolSpotCacheManager
import com.example.cooldown.data.NominatimResult
import com.example.cooldown.data.OverpassService
import com.example.cooldown.data.RouteInfo
import com.example.cooldown.data.SpotCategory
import com.example.cooldown.data.TravelMode
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
  val showLegendDialog: Boolean = false,
  val selectedMapStyle: FreeMapStyle = FreeMapStyle.OSM_DE,
  val showMapStyleDialog: Boolean = false,
  val language: AppLanguage = AppLanguage.DE,
) {
  val isAllCategoriesSelected: Boolean
    get() = activeCategories.size == SpotCategory.entries.size

  val isNoCategorySelected: Boolean
    get() = activeCategories.isEmpty()

  val filteredSpots: List<CoolSpot>
    get() {
      if (activeCategories.isEmpty()) return emptyList()
      val refLat = userLat ?: mapCenterLat
      val refLon = userLon ?: mapCenterLon
      return allSpots
        .filter { it.category in activeCategories }
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
      _uiState.update { it.copy(language = savedLang) }
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
    _uiState.update {
      it.copy(
        selectedSpot = spot,
        activeRoute = if (spot == null) it.activeRoute else it.activeRoute,
      )
    }
    if (spot != null && autoCalculateRoute) {
      calculateRouteToSpot(spot, _uiState.value.selectedTravelMode)
    }
  }

  fun closeDetailCardKeepRoute() {
    _uiState.update { it.copy(selectedSpot = null) }
  }

  fun calculateRouteToSpot(
    spot: CoolSpot? = _uiState.value.selectedSpot,
    travelMode: TravelMode = _uiState.value.selectedTravelMode,
  ) {
    val targetSpot = spot ?: return
    routeJob?.cancel()
    routeJob = viewModelScope.launch {
      val state = _uiState.value
      val originLat = state.userLat ?: state.mapCenterLat
      val originLon = state.userLon ?: state.mapCenterLon
      _uiState.update {
        it.copy(
          selectedSpot = targetSpot,
          selectedTravelMode = travelMode,
          isCalculatingRoute = true,
        )
      }
      val result = overpassService.calculateRoute(
        originLat = originLat,
        originLon = originLon,
        destination = targetSpot,
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

  fun clearRoute() {
    routeJob?.cancel()
    _uiState.update { it.copy(activeRoute = null, isCalculatingRoute = false) }
  }

  /**
   * Toggles category selection:
   * - If all are selected -> selects ONLY this category
   * - If this category is already exclusively selected -> deselects all (empty category set)
   * - If none or another is selected -> selects ONLY this category
   */
  fun selectExclusiveCategory(category: SpotCategory) {
    _uiState.update { state ->
      val nextCategories = if (state.activeCategories.size == 1 && category in state.activeCategories) {
        // Toggle off -> None selected!
        emptySet()
      } else {
        // Select ONLY this category
        setOf(category)
      }
      val keepSelected = state.selectedSpot?.takeIf { it.category in nextCategories }
      state.copy(
        activeCategories = nextCategories,
        selectedSpot = keepSelected,
      )
    }
  }

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

  // Alias kept for unit tests
  fun toggleCategory(category: SpotCategory) = selectExclusiveCategory(category)

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
    if (query.trim().length < 2) {
      _uiState.update { it.copy(searchResults = emptyList(), isSearchingLocation = false) }
      return
    }
    searchJob = viewModelScope.launch {
      delay(400)
      _uiState.update { it.copy(isSearchingLocation = true) }
      overpassService.searchCityOrAddress(query).fold(
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
