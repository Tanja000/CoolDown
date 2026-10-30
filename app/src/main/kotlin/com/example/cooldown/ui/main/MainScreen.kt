package com.example.cooldown.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.cooldown.data.AppLanguage
import com.example.cooldown.data.BoundingBoxCoords
import com.example.cooldown.data.CoolSpot
import com.example.cooldown.data.RouteInfo
import com.example.cooldown.data.SpotCategory
import com.example.cooldown.data.TravelMode
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel() },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  CoolDownMapScreen(
    uiState = uiState,
    viewModel = viewModel,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CoolDownMapScreen(
  uiState: CoolDownUiState,
  viewModel: MainScreenViewModel,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val lang = uiState.language

  remember {
    val osmConf = Configuration.getInstance()
    osmConf.load(context, context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
    osmConf.userAgentValue =
      "Mozilla/5.0 (Linux; Android 14; CoolDownApp/1.5; +https://openstreetmap.org)"
    val baseDir = File(context.cacheDir, "osmdroid_v2").apply { mkdirs() }
    val tileCacheDir = File(baseDir, "tiles").apply { mkdirs() }
    osmConf.osmdroidBasePath = baseDir
    osmConf.osmdroidTileCache = tileCacheDir
    true
  }

  var mapViewRef by remember { mutableStateOf<MapView?>(null) }
  var hasLocationPermission by remember {
    mutableStateOf(checkHasLocationPermission(context))
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    hasLocationPermission = granted
    if (granted) {
      getLastKnownLocation(context)?.let { loc ->
        viewModel.updateUserLocation(loc.latitude, loc.longitude)
        val prevLat = uiState.mapCenterLat
        val prevLon = uiState.mapCenterLon
        val distMoved = kotlin.math.hypot(loc.latitude - prevLat, loc.longitude - prevLon)
        if (distMoved > 0.005 || uiState.allSpots.isEmpty()) {
          mapViewRef?.let { map ->
            map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude), 15.5, 900L)
            map.postDelayed({ triggerSearchFromMap(map, viewModel, auto = true) }, 950L)
          }
        }
      }
    }
  }

  LaunchedEffect(Unit) {
    val gpsLoc = getLastKnownLocation(context)?.let { loc ->
      viewModel.updateUserLocation(loc.latitude, loc.longitude)
      loc.latitude to loc.longitude
    }
    val initialCenter = viewModel.initializeWithCacheOrFetch(context, gpsLoc)
    mapViewRef?.controller?.setCenter(GeoPoint(initialCenter.first, initialCenter.second))

    if (!hasLocationPermission) {
      permissionLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
        )
      )
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    // 1. Interactive OpenStreetMap View
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { ctx ->
        MapView(ctx).apply {
          setTileSource(uiState.selectedMapStyle.tileSource)
          setUseDataConnection(true)
          setMultiTouchControls(true)
          zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
          minZoomLevel = 4.0
          maxZoomLevel = 19.0

          val gpsLoc = getLastKnownLocation(ctx)?.let { it.latitude to it.longitude }
          val startCenter = viewModel.initializeWithCacheOrFetch(ctx, gpsLoc)
          controller.setZoom(uiState.mapZoom)
          controller.setCenter(GeoPoint(startCenter.first, startCenter.second))

          val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
              // If route is active, keep it, but close details on empty map tap
              viewModel.closeDetailCardKeepRoute()
              focusManager.clearFocus()
              return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
          })
          overlays.add(eventsOverlay)

          addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
              if (width > 0 && height > 0) {
                val center = mapCenter
                val bb = boundingBox
                if (bb.latNorth != bb.latSouth && bb.lonEast != bb.lonWest) {
                  viewModel.onMapViewportChanged(
                    centerLat = center.latitude,
                    centerLon = center.longitude,
                    zoom = zoomLevelDouble,
                    bbox = BoundingBoxCoords(
                      south = bb.latSouth,
                      west = bb.lonWest,
                      north = bb.latNorth,
                      east = bb.lonEast,
                    ),
                    triggerAutoFetch = false,
                  )
                }
              }
              return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
              if (width > 0 && height > 0) {
                val center = mapCenter
                val bb = boundingBox
                if (bb.latNorth != bb.latSouth && bb.lonEast != bb.lonWest) {
                  viewModel.onMapViewportChanged(
                    centerLat = center.latitude,
                    centerLon = center.longitude,
                    zoom = zoomLevelDouble,
                    bbox = BoundingBoxCoords(
                      south = bb.latSouth,
                      west = bb.lonWest,
                      north = bb.latNorth,
                      east = bb.lonEast,
                    ),
                    triggerAutoFetch = false,
                  )
                }
              }
              return false
            }
          })

          mapViewRef = this
        }
      },
      update = { mapView ->
        if (mapView.tileProvider.tileSource.name() != uiState.selectedMapStyle.tileSource.name()) {
          mapView.setTileSource(uiState.selectedMapStyle.tileSource)
        }

        val hasMyLocOverlay = mapView.overlays.any { it is MyLocationNewOverlay }
        if (hasLocationPermission && !hasMyLocOverlay) {
          val locOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
          }
          mapView.overlays.add(locOverlay)
        }

        // Remove previous Marker and Polyline overlays (preserve MapEventsOverlay and MyLocationNewOverlay)
        mapView.overlays.removeAll { it is Marker || it is Polyline }

        // Draw active route Polyline on the map
        uiState.activeRoute?.let { route ->
          if (route.polylinePoints.size >= 2) {
            val geoPoints = route.polylinePoints.map { (lat, lon) -> GeoPoint(lat, lon) }
            val borderLine = Polyline(mapView).apply {
              setPoints(geoPoints)
              outlinePaint.color = android.graphics.Color.WHITE
              outlinePaint.strokeWidth = 24f
              outlinePaint.strokeCap = Paint.Cap.ROUND
              outlinePaint.strokeJoin = Paint.Join.ROUND
              outlinePaint.isAntiAlias = true
            }
            val innerLine = Polyline(mapView).apply {
              setPoints(geoPoints)
              outlinePaint.color = android.graphics.Color.rgb(0, 110, 240)
              outlinePaint.strokeWidth = 15f
              outlinePaint.strokeCap = Paint.Cap.ROUND
              outlinePaint.strokeJoin = Paint.Join.ROUND
              outlinePaint.isAntiAlias = true
              // Click on polyline opens the detail sheet again!
              setOnClickListener { _, _, _ ->
                viewModel.selectSpot(route.targetSpot, autoCalculateRoute = false)
                true
              }
            }
            mapView.overlays.add(borderLine)
            mapView.overlays.add(innerLine)
          }
        }

        // Add markers for all filtered CoolSpots
        val selectedId = uiState.selectedSpot?.id
        val selectedCat = uiState.selectedSpot?.category
        for (spot in uiState.filteredSpots) {
          val isSelected = (spot.id == selectedId && spot.category == selectedCat)
          val marker = Marker(mapView).apply {
            position = GeoPoint(spot.lat, spot.lon)
            title = spot.name
            snippet = spot.category.title(lang)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = MarkerIconFactory.getMarkerDrawable(context, spot.category, isSelected)
            setOnMarkerClickListener { _, _ ->
              viewModel.selectSpot(spot, autoCalculateRoute = true)
              mapView.controller.animateTo(GeoPoint(spot.lat, spot.lon))
              true
            }
          }
          mapView.overlays.add(marker)
        }
        mapView.invalidate()
      },
    )

    DisposableEffect(mapViewRef) {
      val map = mapViewRef
      map?.onResume()
      onDispose {
        map?.onPause()
      }
    }

    // 2. Top Search Bar & Category Filter Chips
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(top = 8.dp),
    ) {
      // Search Bar Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.AcUnit,
              contentDescription = null,
              tint = Color(0xFF0077E6),
              modifier = Modifier
                .padding(start = 8.dp)
                .size(24.dp),
            )
            TextField(
              value = uiState.searchQuery,
              onValueChange = viewModel::onSearchQueryChanged,
              modifier = Modifier.weight(1f),
              placeholder = {
                Text(
                  text = if (lang == AppLanguage.EN) "Search place or city…" else "Ort oder Stadt suchen…",
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
              keyboardActions = KeyboardActions(
                onSearch = { focusManager.clearFocus() }
              ),
              colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
              ),
            )
            if (uiState.searchQuery.isNotEmpty()) {
              IconButton(onClick = viewModel::clearSearch) {
                Icon(Icons.Default.Clear, contentDescription = if (lang == AppLanguage.EN) "Clear" else "Löschen")
              }
            }

            // Language Switcher Button (DE / EN)
            Surface(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { viewModel.toggleLanguage() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
              shape = RoundedCornerShape(12.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Language,
                  contentDescription = "Language",
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (lang == AppLanguage.DE) "DE" else "EN",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }

            IconButton(onClick = { viewModel.setShowMapStyleDialog(true) }) {
              Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = if (lang == AppLanguage.EN) "Change map" else "Kartenstil wechseln",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
            IconButton(onClick = { viewModel.setShowLegendDialog(true) }) {
              Icon(
                imageVector = Icons.Default.Info,
                contentDescription = if (lang == AppLanguage.EN) "Categories & Info" else "Kategorien & Info",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }

          if (uiState.isLoading || uiState.isSearchingLocation || uiState.isCalculatingRoute) {
            LinearProgressIndicator(
              modifier = Modifier.fillMaxWidth(),
              color = Color(0xFF0077E6),
            )
          }

          // Search Suggestions Dropdown
          if (uiState.searchResults.isNotEmpty()) {
            HorizontalDivider()
            Column(modifier = Modifier.fillMaxWidth()) {
              uiState.searchResults.forEach { place ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      val lat = place.lat.toDoubleOrNull()
                      val lon = place.lon.toDoubleOrNull()
                      viewModel.clearSearch()
                      focusManager.clearFocus()
                      if (lat != null && lon != null) {
                        mapViewRef?.let { map ->
                          map.controller.animateTo(GeoPoint(lat, lon), 15.2, 800L)
                          map.postDelayed(
                            { triggerSearchFromMap(map, viewModel, auto = true) },
                            850L,
                          )
                        }
                      }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = place.display_name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Category Filter Chips
      val allSelected = uiState.isAllCategoriesSelected
      val noneSelected = uiState.isNoCategorySelected

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // High-contrast, easily recognizable "Alle" button (can toggle between ALL and NONE)
        Surface(
          modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable { viewModel.toggleAllCategories() },
          color = if (allSelected) Color(0xFF0D47A1) else Color(0xFFF0F4F8),
          shape = RoundedCornerShape(22.dp),
          shadowElevation = 4.dp,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = if (allSelected) Icons.Default.Check else Icons.Default.FilterList,
              contentDescription = null,
              tint = if (allSelected) Color.White else Color(0xFF0D47A1),
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (lang == AppLanguage.EN) {
                if (uiState.allSpots.isNotEmpty()) "All (${uiState.allSpots.size})" else "All"
              } else {
                if (uiState.allSpots.isNotEmpty()) "Alle (${uiState.allSpots.size})" else "Alle"
              },
              fontWeight = FontWeight.Bold,
              color = if (allSelected) Color.White else Color(0xFF0D47A1),
            )
          }
        }

        // Separate Button: "Keine" / "None"
        Surface(
          modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable { viewModel.clearAllCategories() },
          color = if (noneSelected) Color(0xFF37474F) else Color.White,
          shape = RoundedCornerShape(22.dp),
          shadowElevation = 3.dp,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.Clear,
              contentDescription = null,
              tint = if (noneSelected) Color.White else Color(0xFF455A64),
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (lang == AppLanguage.EN) "None" else "Keine",
              fontWeight = if (noneSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (noneSelected) Color.White else Color(0xFF455A64),
            )
          }
        }

        // Each category button: Clicking it selects ONLY that category. Clicking again deselects all.
        SpotCategory.entries.forEach { category ->
          val isExclusivelySelected = !allSelected && category in uiState.activeCategories
          val count = uiState.categoryCounts[category] ?: 0
          val catColor = Color(category.colorHex)

          FilterChip(
            selected = isExclusivelySelected,
            onClick = { viewModel.selectExclusiveCategory(category) },
            label = {
              Text(
                text = if (count > 0) "${category.title(lang)} ($count)" else category.title(lang),
                fontWeight = if (isExclusivelySelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isExclusivelySelected) Color.White else Color(0xFF1A1C1E),
              )
            },
            leadingIcon = {
              Icon(
                imageVector = category.iconVector(),
                contentDescription = null,
                tint = if (isExclusivelySelected) Color.White else catColor,
                modifier = Modifier.size(18.dp),
              )
            },
            shape = RoundedCornerShape(20.dp),
            colors = FilterChipDefaults.filterChipColors(
              containerColor = Color.White,
              labelColor = Color(0xFF1A1C1E),
              selectedContainerColor = catColor,
              selectedLabelColor = Color.White,
            ),
            border = FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = isExclusivelySelected,
              borderColor = catColor.copy(alpha = 0.65f),
              selectedBorderColor = Color.White,
              borderWidth = 1.2.dp,
              selectedBorderWidth = 1.8.dp,
            ),
            elevation = FilterChipDefaults.filterChipElevation(elevation = 4.dp),
          )
        }
      }

      // "Search this map section" button
      AnimatedVisibility(
        visible = uiState.hasMovedMapSinceFetch && !uiState.isLoading,
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .padding(top = 8.dp),
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
      ) {
        ElevatedButton(
          onClick = {
            mapViewRef?.let { triggerSearchFromMap(it, viewModel, auto = false) }
          },
          colors = ButtonDefaults.elevatedButtonColors(
            containerColor = Color(0xFF006699),
            contentColor = Color.White,
          ),
          elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 6.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            if (lang == AppLanguage.EN) "Search this area" else "In diesem Kartenausschnitt suchen",
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      // Error banner
      uiState.errorMessage?.let { errorMsg ->
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = errorMsg,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.weight(1f),
            )
            TextButton(
              onClick = {
                mapViewRef?.let { triggerSearchFromMap(it, viewModel, auto = false) }
              }
            ) {
              Text(if (lang == AppLanguage.EN) "Retry" else "Erneut")
            }
          }
        }
      }
    }

    // Active Route Bar when Detail Card is closed (Allows opening details again or removing route)
    if (uiState.activeRoute != null && uiState.selectedSpot == null) {
      val route = uiState.activeRoute
      Surface(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .statusBarsPadding()
          .padding(top = 118.dp, start = 16.dp, end = 16.dp)
          .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              // Open details again
              viewModel.selectSpot(route.targetSpot, autoCalculateRoute = false)
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Default.Route,
            contentDescription = null,
            tint = Color(0xFF0077E6),
            modifier = Modifier.size(24.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${route.targetSpot.name} (${route.formattedDuration(lang)} • ${route.formattedDistance})",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = if (lang == AppLanguage.EN) "Tap for details or to navigate" else "Tippen für Details & Navigation",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = { viewModel.clearRoute() },
            modifier = Modifier.size(32.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = if (lang == AppLanguage.EN) "Remove route" else "Route entfernen",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }

    // 3. Bottom Controls & Selected CoolSpot Detail Card
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
      ) {
        // Left: Open list of spots
        ExtendedFloatingActionButton(
          onClick = { viewModel.setShowSpotListSheet(true) },
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.primary,
          icon = {
            Icon(Icons.Default.Explore, contentDescription = null)
          },
          text = {
            Text(
              text = if (uiState.isLoading) {
                if (lang == AppLanguage.EN) "Searching…" else "Suche läuft…"
              } else {
                if (lang == AppLanguage.EN) "${uiState.filteredSpots.size} places nearby"
                else "${uiState.filteredSpots.size} Orte in der Nähe"
              },
              fontWeight = FontWeight.SemiBold,
            )
          },
        )

        // Right: Refresh & My Location
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          SmallFloatingActionButton(
            onClick = {
              mapViewRef?.let { triggerSearchFromMap(it, viewModel, auto = false) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
          ) {
            if (uiState.isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
              )
            } else {
              Icon(
                Icons.Default.Refresh,
                contentDescription = if (lang == AppLanguage.EN) "Refresh" else "Neu laden",
              )
            }
          }

          FloatingActionButton(
            onClick = {
              if (!hasLocationPermission) {
                permissionLauncher.launch(
                  arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                  )
                )
              } else {
                val loc = getLastKnownLocation(context)
                if (loc != null) {
                  viewModel.updateUserLocation(loc.latitude, loc.longitude)
                  mapViewRef?.let { map ->
                    map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude), 15.8, 800L)
                    map.postDelayed({ triggerSearchFromMap(map, viewModel, auto = true) }, 850L)
                  }
                } else {
                  mapViewRef?.let { triggerSearchFromMap(it, viewModel, auto = false) }
                }
              }
            },
            containerColor = Color(0xFF0077E6),
            contentColor = Color.White,
          ) {
            Icon(
              Icons.Default.MyLocation,
              contentDescription = if (lang == AppLanguage.EN) "My location" else "Mein Standort",
            )
          }
        }
      }

      // Detail Card
      AnimatedVisibility(
        visible = uiState.selectedSpot != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
      ) {
        uiState.selectedSpot?.let { spot ->
          val originLat = uiState.userLat ?: uiState.mapCenterLat
          val originLon = uiState.userLon ?: uiState.mapCenterLon
          Spacer(modifier = Modifier.height(10.dp))
          CoolSpotDetailCard(
            spot = spot,
            lang = lang,
            activeRoute = uiState.activeRoute,
            isCalculatingRoute = uiState.isCalculatingRoute,
            selectedTravelMode = uiState.selectedTravelMode,
            referenceLat = originLat,
            referenceLon = originLon,
            onClose = { viewModel.selectSpot(null) },
            onShowRouteAndCloseDetails = { mode ->
              viewModel.calculateRouteToSpot(spot, mode)
              zoomMapToFitRoute(mapViewRef, originLat, originLon, spot.lat, spot.lon)
              // Close detail card so route stays clearly visible on map!
              viewModel.closeDetailCardKeepRoute()
            },
            onRemoveRoute = {
              viewModel.clearRoute()
            },
            onOpenInGoogleMaps = {
              openRouteInGoogleMaps(
                context = context,
                originLat = uiState.userLat,
                originLon = uiState.userLon,
                spot = spot,
                travelMode = uiState.selectedTravelMode,
              )
            },
          )
        }
      }
    }
  }

  // 4. Nearby CoolSpots Bottom Sheet List
  if (uiState.showSpotListSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
      onDismissRequest = { viewModel.setShowSpotListSheet(false) },
      sheetState = sheetState,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .padding(bottom = 24.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = if (lang == AppLanguage.EN) "Cool Spots & Water" else "Kühle Orte & Trinkwasser",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = if (lang == AppLanguage.EN) {
                "${uiState.filteredSpots.size} places found (sorted by distance)"
              } else {
                "${uiState.filteredSpots.size} Treffer im aktuellen Kartenausschnitt (sortiert nach Entfernung)"
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.filteredSpots.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = if (uiState.isLoading) {
                if (lang == AppLanguage.EN) "Loading spots from OpenStreetMap…" else "Lade Orte aus OpenStreetMap…"
              } else {
                if (lang == AppLanguage.EN) "No places for selected category in this area."
                else "Keine Orte für diese Kategorie im aktuellen Kartenausschnitt gefunden."
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          val refLat = uiState.userLat ?: uiState.mapCenterLat
          val refLon = uiState.userLon ?: uiState.mapCenterLon
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
          ) {
            items(uiState.filteredSpots, key = { "${it.category}_${it.id}" }) { spot ->
              SpotListItemCard(
                spot = spot,
                lang = lang,
                distanceText = spot.formattedDistanceTo(refLat, refLon),
                onSelectAndRoute = {
                  viewModel.setShowSpotListSheet(false)
                  viewModel.selectSpot(spot, autoCalculateRoute = true)
                  zoomMapToFitRoute(mapViewRef, refLat, refLon, spot.lat, spot.lon)
                },
                onOpenGoogleMaps = {
                  openRouteInGoogleMaps(
                    context = context,
                    originLat = uiState.userLat,
                    originLon = uiState.userLon,
                    spot = spot,
                    travelMode = uiState.selectedTravelMode,
                  )
                },
              )
            }
          }
        }
      }
    }
  }

  // 5. Legend & Info Dialog
  if (uiState.showLegendDialog) {
    OverpassLegendDialog(
      lang = lang,
      onDismiss = { viewModel.setShowLegendDialog(false) },
    )
  }

  // 6. Free Map Style Switcher Dialog
  if (uiState.showMapStyleDialog) {
    MapStyleSelectionDialog(
      lang = lang,
      selectedStyle = uiState.selectedMapStyle,
      onSelectStyle = { style ->
        viewModel.setMapStyle(style)
        mapViewRef?.let { map ->
          map.setTileSource(style.tileSource)
          map.tileProvider.clearTileCache()
          map.invalidate()
        }
      },
      onDismiss = { viewModel.setShowMapStyleDialog(false) },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoolSpotDetailCard(
  spot: CoolSpot,
  lang: AppLanguage,
  activeRoute: RouteInfo?,
  isCalculatingRoute: Boolean,
  selectedTravelMode: TravelMode,
  referenceLat: Double,
  referenceLon: Double,
  onClose: () -> Unit,
  onShowRouteAndCloseDetails: (TravelMode) -> Unit,
  onRemoveRoute: () -> Unit,
  onOpenInGoogleMaps: () -> Unit,
) {
  val catColor = Color(spot.category.colorHex)
  val isThisSpotRouteActive = activeRoute != null && activeRoute.targetSpot.id == spot.id

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 10.dp),
    shape = RoundedCornerShape(24.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(catColor),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = spot.category.iconVector(),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = spot.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = if (lang == AppLanguage.EN) {
              "${spot.category.title(lang)} • ${spot.formattedDistanceTo(referenceLat, referenceLon)} away"
            } else {
              "${spot.category.title(lang)} • ${spot.formattedDistanceTo(referenceLat, referenceLon)} entfernt"
            },
            style = MaterialTheme.typography.bodySmall,
            color = catColor,
            fontWeight = FontWeight.SemiBold,
          )
        }
        IconButton(onClick = onClose) {
          Icon(Icons.Default.Clear, contentDescription = if (lang == AppLanguage.EN) "Close" else "Schließen")
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // Description / Subtitle
      Text(
        text = spot.description ?: spot.category.subtitle(lang),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Only clean user badges (NO osm: parameter line!)
      val hasAnyBadge = spot.openingHours != null ||
        spot.bottle == "yes" ||
        spot.fee == "no" ||
        spot.category == SpotCategory.REFILL_STATION ||
        spot.category == SpotCategory.DRINKING_WATER ||
        spot.wheelchair == "yes" ||
        spot.address != null

      if (hasAnyBadge) {
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          spot.openingHours?.let {
            MetadataBadge(text = if (lang == AppLanguage.EN) "Hours: $it" else "Zeiten: $it")
          }
          if (spot.bottle == "yes") {
            MetadataBadge(text = if (lang == AppLanguage.EN) "Bottle refillable" else "Flaschenbefüllung möglich")
          }
          if (spot.fee == "no" || spot.category == SpotCategory.REFILL_STATION || spot.category == SpotCategory.DRINKING_WATER) {
            MetadataBadge(text = if (lang == AppLanguage.EN) "Free" else "Kostenlos")
          }
          if (spot.wheelchair == "yes") {
            MetadataBadge(text = if (lang == AppLanguage.EN) "Wheelchair accessible" else "Barrierefrei")
          }
          spot.address?.let { MetadataBadge(text = it) }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      HorizontalDivider()
      Spacer(modifier = Modifier.height(10.dp))

      // Travel Mode selector + duration info
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = selectedTravelMode == TravelMode.WALKING,
            onClick = { onShowRouteAndCloseDetails(TravelMode.WALKING) },
            label = { Text(TravelMode.WALKING.title(lang)) },
            leadingIcon = {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
          )
          FilterChip(
            selected = selectedTravelMode == TravelMode.BICYCLING,
            onClick = { onShowRouteAndCloseDetails(TravelMode.BICYCLING) },
            label = { Text(TravelMode.BICYCLING.title(lang)) },
            leadingIcon = {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
          )
        }

        if (isCalculatingRoute) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text("…", style = MaterialTheme.typography.labelMedium)
          }
        } else if (isThisSpotRouteActive) {
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = activeRoute.formattedDuration(lang),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF0066CC),
            )
            Text(
              text = activeRoute.formattedDistance,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action buttons: "Route" (clearly readable), "In Google Maps", and if route is active: "Route entfernen"
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // High-contrast, clearly legible "Route" button
        Button(
          onClick = { onShowRouteAndCloseDetails(selectedTravelMode) },
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0D47A1),
            contentColor = Color.White,
          ),
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Route",
            fontWeight = FontWeight.Bold,
            color = Color.White,
          )
        }

        Button(
          onClick = onOpenInGoogleMaps,
          colors = ButtonDefaults.buttonColors(containerColor = catColor),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1.2f),
        ) {
          Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (lang == AppLanguage.EN) "Google Maps" else "In Google Maps",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // If route is active, offer "Route entfernen" button
        if (isThisSpotRouteActive) {
          IconButton(
            onClick = onRemoveRoute,
            modifier = Modifier
              .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(14.dp))
              .size(48.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = if (lang == AppLanguage.EN) "Remove Route" else "Route entfernen",
              tint = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MetadataBadge(
  text: String,
  color: Color = MaterialTheme.colorScheme.surfaceVariant,
  textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  Surface(
    color = color,
    shape = RoundedCornerShape(8.dp),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = textColor,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun SpotListItemCard(
  spot: CoolSpot,
  lang: AppLanguage,
  distanceText: String,
  onSelectAndRoute: () -> Unit,
  onOpenGoogleMaps: () -> Unit,
) {
  val catColor = Color(spot.category.colorHex)
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onSelectAndRoute),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(catColor),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = spot.category.iconVector(),
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(20.dp),
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = spot.name,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${spot.category.title(lang)} • ${spot.description ?: spot.category.subtitle(lang)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = distanceText,
        style = MaterialTheme.typography.labelLarge,
        color = catColor,
        fontWeight = FontWeight.Bold,
      )
      IconButton(onClick = onOpenGoogleMaps) {
        Icon(
          imageVector = Icons.Default.Directions,
          contentDescription = "Google Maps",
          tint = catColor,
        )
      }
    }
  }
}

@Composable
private fun OverpassLegendDialog(
  lang: AppLanguage,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.AcUnit,
          contentDescription = null,
          tint = Color(0xFF0077E6),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (lang == AppLanguage.EN) "CoolDown – Categories" else "CoolDown – Kühle Orte & Wasser",
          style = MaterialTheme.typography.titleMedium,
        )
      }
    },
    text = {
      LazyColumn(
        modifier = Modifier.heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        item {
          Text(
            text = if (lang == AppLanguage.EN) {
              "All supported cooling and drinking water categories:"
            } else {
              "Alle unterstützten Abkühlungs- und Trinkwasser-Kategorien:"
            },
            style = MaterialTheme.typography.bodySmall,
          )
        }
        items(SpotCategory.entries) { cat ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(cat.colorHex)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = cat.iconVector(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = cat.title(lang),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = cat.subtitle(lang),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(if (lang == AppLanguage.EN) "Got it" else "Verstanden")
      }
    },
  )
}

@Composable
private fun MapStyleSelectionDialog(
  lang: AppLanguage,
  selectedStyle: FreeMapStyle,
  onSelectStyle: (FreeMapStyle) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Layers,
          contentDescription = null,
          tint = Color(0xFF0077E6),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (lang == AppLanguage.EN) "Choose free map" else "Kostenlose Karte wählen",
          style = MaterialTheme.typography.titleMedium,
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = if (lang == AppLanguage.EN) {
            "All map sources are completely free with no API key required:"
          } else {
            "Alle Karten sind komplett kostenlos und ohne API-Schlüssel nutzbar:"
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FreeMapStyle.entries.forEach { style ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { onSelectStyle(style) }
              .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = style == selectedStyle,
              onClick = { onSelectStyle(style) },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = style.titleDe,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = style.subtitleDe,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(if (lang == AppLanguage.EN) "Close" else "Schließen")
      }
    },
  )
}

fun SpotCategory.iconVector(): ImageVector = when (this) {
  SpotCategory.DRINKING_WATER -> Icons.Default.WaterDrop
  SpotCategory.REFILL_STATION -> Icons.Default.LocalDrink
  SpotCategory.SWIMMING_BATHING -> Icons.Default.Pool
  SpotCategory.WATER_PLAYGROUND -> Icons.Default.Shower
  SpotCategory.FOUNTAIN -> Icons.Default.Water
  SpotCategory.KNEIPP -> Icons.Default.Pool
  SpotCategory.PARK -> Icons.Default.Park
  SpotCategory.ICE_CREAM -> Icons.Default.Icecream
  SpotCategory.CHURCH_COOL_BUILDING -> Icons.Default.Church
  SpotCategory.LIBRARY -> Icons.Default.LocalLibrary
  SpotCategory.MUSEUM -> Icons.Default.AccountBalance
  SpotCategory.SHADED_BENCH_SHELTER -> Icons.Default.BeachAccess
  SpotCategory.TREE_SHADE -> Icons.Default.Forest
}

private fun triggerSearchFromMap(
  mapView: MapView,
  viewModel: MainScreenViewModel,
  auto: Boolean,
) {
  val bb = mapView.boundingBox
  val center = mapView.mapCenter
  val zoom = mapView.zoomLevelDouble

  val maxSpan = 0.08
  val rawLatSpan = (bb.latNorth - bb.latSouth).let { if (it <= 0.0005) 0.024 else it }
  val rawLonSpan = (bb.lonEast - bb.lonWest).let { if (it <= 0.0005) 0.032 else it }
  val latSpan = rawLatSpan.coerceAtMost(maxSpan)
  val lonSpan = rawLonSpan.coerceAtMost(maxSpan)
  val clampedBbox = BoundingBoxCoords(
    south = center.latitude - latSpan / 2.0,
    west = center.longitude - lonSpan / 2.0,
    north = center.latitude + latSpan / 2.0,
    east = center.longitude + lonSpan / 2.0,
  )

  viewModel.onMapViewportChanged(
    centerLat = center.latitude,
    centerLon = center.longitude,
    zoom = zoom,
    bbox = clampedBbox,
    triggerAutoFetch = auto,
  )
  if (!auto) {
    viewModel.fetchSpotsInBounds(clampedBbox, zoom)
  }
}

private fun zoomMapToFitRoute(
  mapView: MapView?,
  originLat: Double,
  originLon: Double,
  destLat: Double,
  destLon: Double,
) {
  mapView ?: return
  val north = maxOf(originLat, destLat) + 0.0025
  val south = minOf(originLat, destLat) - 0.0025
  val east = maxOf(originLon, destLon) + 0.0035
  val west = minOf(originLon, destLon) - 0.0035
  try {
    mapView.zoomToBoundingBox(BoundingBox(north, east, south, west), true, 110)
  } catch (_: Exception) {
    mapView.controller.animateTo(GeoPoint(destLat, destLon))
  }
}

private fun checkHasLocationPermission(context: Context): Boolean {
  val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
  val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
  return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
private fun getLastKnownLocation(context: Context): Location? {
  if (!checkHasLocationPermission(context)) return null
  val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
  val providers = listOf(
    LocationManager.GPS_PROVIDER,
    LocationManager.NETWORK_PROVIDER,
    LocationManager.PASSIVE_PROVIDER,
  )
  for (provider in providers) {
    try {
      if (lm.isProviderEnabled(provider)) {
        lm.getLastKnownLocation(provider)?.let { return it }
      }
    } catch (_: Exception) {
    }
  }
  return null
}

private fun openRouteInGoogleMaps(
  context: Context,
  originLat: Double?,
  originLon: Double?,
  spot: CoolSpot,
  travelMode: TravelMode,
) {
  val originParam = if (originLat != null && originLon != null) {
    "&origin=$originLat,$originLon"
  } else {
    ""
  }
  val googleMapsDirectionsUrl =
    "https://www.google.com/maps/dir/?api=1$originParam&destination=${spot.lat},${spot.lon}&travelmode=${travelMode.googleMapsMode}"

  val uri = Uri.parse(googleMapsDirectionsUrl)

  val googleMapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
    setPackage("com.google.android.apps.maps")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
  try {
    context.startActivity(googleMapsIntent)
    return
  } catch (_: Exception) {
  }

  val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
  try {
    context.startActivity(fallbackIntent)
  } catch (_: Exception) {
  }
}
