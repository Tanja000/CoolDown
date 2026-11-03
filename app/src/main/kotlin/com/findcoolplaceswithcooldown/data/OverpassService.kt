package com.findcoolplaceswithcooldown.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import java.util.zip.GZIPInputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class PhotonResponse(
  val features: List<PhotonFeatureItem> = emptyList(),
)

@Serializable
data class PhotonFeatureItem(
  val geometry: PhotonPointGeometry? = null,
  val properties: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
)

@Serializable
data class PhotonPointGeometry(
  val coordinates: List<Double> = emptyList(),
)

@Serializable
data class OverpassResponse(
  val elements: List<OverpassElement> = emptyList(),
)

@Serializable
data class OverpassElement(
  val type: String,
  val id: Long,
  val lat: Double? = null,
  val lon: Double? = null,
  val center: OverpassCenter? = null,
  val nodes: List<Long>? = null,
  val tags: Map<String, String> = emptyMap(),
)

@Serializable
data class OverpassCenter(
  val lat: Double,
  val lon: Double,
)

@Serializable
data class NominatimResult(
  val place_id: Long? = null,
  val osm_id: Long? = null,
  val display_name: String = "",
  val name: String? = null,
  val lat: String = "0.0",
  val lon: String = "0.0",
  val extratags: Map<String, String>? = null,
)

@Serializable
data class OsrmRouteResponse(
  val code: String = "",
  val routes: List<OsrmRoute> = emptyList(),
)

@Serializable
data class OsrmTripResponse(
  val code: String = "",
  val trips: List<OsrmRoute> = emptyList(),
  val waypoints: List<OsrmWaypoint> = emptyList(),
)

@Serializable
data class OsrmWaypoint(
  val waypoint_index: Int = 0,
  val trips_index: Int = 0,
  val location: List<Double> = emptyList(),
)

@Serializable
data class OsrmRoute(
  val distance: Double = 0.0,
  val duration: Double = 0.0,
  val geometry: OsrmGeometry? = null,
)

@Serializable
data class OsrmGeometry(
  val coordinates: List<List<Double>> = emptyList(),
)

class OverpassService {
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
  }

  private val iceCreamNameRegex = Regex(
    "(?i)(eiscaf|eisdiele|eissalon|gelat|eishaus|eiskind|eismacher|eiswerkstatt|eismanufaktur|" +
      "eisboutique|eisbar|eisladen|eisgarten|eismeister|eisbrunnen|eismeer|eiskonditorei|" +
      "eis\\s*(&|und)\\s*|\\beis\\s+cafe|frozen\\s*yogurt|yomaro|ballabeni|sarcletti|venchi|rivareno|ice\\s*cream)"
  )

  private val waterPlayNameRegex = Regex(
    "(?i)(wasserspiel|plansch|matschanlage|matschplatz|wasserfont|nebeldusche|sprühnebel|splash\\s*pad|water\\s*play)"
  )

  private val kneippNameRegex = Regex(
    "(?i)(kneipp|wassertret)"
  )

  suspend fun fetchCoolSpots(
    bbox: BoundingBoxCoords,
    includeIndividualTrees: Boolean,
    onPartialSpots: ((List<CoolSpot>) -> Unit)? = null,
  ): Result<List<CoolSpot>> = withContext(Dispatchers.IO) {
    val combinedSpots = Collections.synchronizedList(mutableListOf<CoolSpot>())
    var anySuccess = false
    var lastError: Throwable? = null

    fun emitProgressIfAvailable() {
      if (onPartialSpots != null && combinedSpots.isNotEmpty()) {
        val snapshot = synchronized(combinedSpots) { combinedSpots.toList() }
        val deduped = deduplicateSpots(snapshot)
        if (deduped.isNotEmpty()) {
          onPartialSpots(deduped)
        }
      }
    }

    supervisorScope {
      // 1. High-speed Photon Multi-Category Search (Reverse by OSM tags + Bounded keyword search)
      // Responds in ~1-2 seconds without rate limiting and finds ice cream parlors, pools, fountains, parks, etc.
      val photonDeferred = async {
        runCatching {
          fetchFromPhotonMultiSource(bbox) { partialBatch ->
            if (partialBatch.isNotEmpty()) {
              anySuccess = true
              combinedSpots.addAll(partialBatch)
              emitProgressIfAvailable()
            }
          }
        }
      }

      // 2. Official OpenStreetMap API v0.6 map.json (tiled for speed & no timeouts, extracts all raw OSM nodes/ways)
      val osmMapDeferred = async {
        runCatching {
          fetchFromOfficialOsmMapApiTiled(bbox, includeIndividualTrees) { tileSpots ->
            if (tileSpots.isNotEmpty()) {
              anySuccess = true
              combinedSpots.addAll(tileSpots)
              emitProgressIfAvailable()
            }
          }
        }
      }

      val photonResult = photonDeferred.await()
      photonResult.onSuccess { spots ->
        if (spots.isNotEmpty()) anySuccess = true
        combinedSpots.addAll(spots)
        emitProgressIfAvailable()
      }.onFailure { lastError = it }

      val osmResult = osmMapDeferred.await()
      osmResult.onSuccess { spots ->
        if (spots.isNotEmpty()) anySuccess = true
        combinedSpots.addAll(spots)
        emitProgressIfAvailable()
      }.onFailure { if (lastError == null) lastError = it }

      // 3. If very few spots were found so far, also query Overpass and/or expanded radius search
      // so the user ALWAYS gets places ("es sollen auf jeden Fall Plätze gefunden werden")
      val currentCount = synchronized(combinedSpots) { combinedSpots.size }
      if (currentCount < 25) {
        val overpassDeferred = async {
          runCatching { fetchFromOverpassServers(bbox, includeIndividualTrees) }
        }
        val expandedDeferred = async {
          runCatching { fetchExpandedRadiusFallback(bbox) }
        }

        overpassDeferred.await().onSuccess { spots ->
          if (spots.isNotEmpty()) {
            anySuccess = true
            combinedSpots.addAll(spots)
            emitProgressIfAvailable()
          }
        }.onFailure { if (lastError == null) lastError = it }

        expandedDeferred.await().onSuccess { spots ->
          if (spots.isNotEmpty()) {
            anySuccess = true
            combinedSpots.addAll(spots)
            emitProgressIfAvailable()
          }
        }.onFailure { if (lastError == null) lastError = it }
      }
    }

    val snapshot = synchronized(combinedSpots) { combinedSpots.toList() }
    val deduplicated = deduplicateSpots(snapshot)
    if (deduplicated.isNotEmpty() || anySuccess) {
      Result.success(deduplicated)
    } else {
      // Final guaranteed fallback with wide radius (35 km) so loading never fails empty
      val emergencySpots = runCatching { fetchExpandedRadiusFallback(bbox, radiusKm = 35) }.getOrDefault(emptyList())
      if (emergencySpots.isNotEmpty()) {
        Result.success(deduplicateSpots(emergencySpots))
      } else {
        Result.success(emptyList())
      }
    }
  }

  private suspend fun fetchFromOfficialOsmMapApiTiled(
    bbox: BoundingBoxCoords,
    includeIndividualTrees: Boolean,
    onTileLoaded: (List<CoolSpot>) -> Unit = {},
  ): List<CoolSpot> = coroutineScope {
    val centerLat = (bbox.south + bbox.north) / 2.0
    val centerLon = (bbox.west + bbox.east) / 2.0

    // Keep each tile compact (~0.0065 deg) so OSM v0.6 responds quickly in 1.5-3s and never hits 50k node limits
    val tileMaxSpan = 0.0065
    val totalLatSpan = (bbox.north - bbox.south).coerceIn(0.004, 0.014)
    val totalLonSpan = (bbox.east - bbox.west).coerceIn(0.004, 0.016)

    val tiles = mutableListOf<BoundingBoxCoords>()
    // Tile 0: Immediate center tile (highest priority)
    val cLatHalf = (totalLatSpan.coerceAtMost(tileMaxSpan)) / 2.0
    val cLonHalf = (totalLonSpan.coerceAtMost(tileMaxSpan)) / 2.0
    tiles.add(
      BoundingBoxCoords(
        south = centerLat - cLatHalf,
        west = centerLon - cLonHalf,
        north = centerLat + cLatHalf,
        east = centerLon + cLonHalf,
      )
    )

    // If viewport is larger, add 4 quadrant tiles around center
    if (totalLatSpan > tileMaxSpan * 1.2 || totalLonSpan > tileMaxSpan * 1.2) {
      val qLatHalf = (totalLatSpan / 2.0).coerceAtMost(tileMaxSpan)
      val qLonHalf = (totalLonSpan / 2.0).coerceAtMost(tileMaxSpan)
      // NW, NE, SW, SE
      tiles.add(BoundingBoxCoords(centerLat, centerLon - qLonHalf, centerLat + qLatHalf, centerLon))
      tiles.add(BoundingBoxCoords(centerLat, centerLon, centerLat + qLatHalf, centerLon + qLonHalf))
      tiles.add(BoundingBoxCoords(centerLat - qLatHalf, centerLon - qLonHalf, centerLat, centerLon))
      tiles.add(BoundingBoxCoords(centerLat - qLatHalf, centerLon, centerLat, centerLon + qLonHalf))
    }

    val allTileSpots = Collections.synchronizedList(mutableListOf<CoolSpot>())
    val sem = Semaphore(3)

    tiles.map { tile ->
      async {
        sem.withPermit {
          val spots = runCatching {
            fetchSingleOsmMapTile(tile, includeIndividualTrees)
          }.getOrDefault(emptyList())
          if (spots.isNotEmpty()) {
            allTileSpots.addAll(spots)
            onTileLoaded(spots)
          }
          spots
        }
      }
    }.awaitAll()

    allTileSpots.toList()
  }

  private fun fetchSingleOsmMapTile(
    tile: BoundingBoxCoords,
    includeIndividualTrees: Boolean,
  ): List<CoolSpot> {
    val urlStr =
      "https://api.openstreetmap.org/api/0.6/map.json?bbox=${tile.west},${tile.south},${tile.east},${tile.north}"
    val body = executeGetWithGzip(urlStr, connectTimeoutMs = 30000, readTimeoutMs = 90000)
    val parsed = json.decodeFromString<OverpassResponse>(body)

    val nodeCoords = HashMap<Long, Pair<Double, Double>>(parsed.elements.size)
    for (el in parsed.elements) {
      if (el.type == "node" && el.lat != null && el.lon != null) {
        nodeCoords[el.id] = el.lat to el.lon
      }
    }

    val spots = mutableListOf<CoolSpot>()
    var treeCounter = 0
    var benchCounter = 0
    val maxIndividualTrees = if (includeIndividualTrees) 120 else 50
    val maxBenchesPerTile = 50

    for (el in parsed.elements) {
      if (el.tags.isEmpty()) continue
      val resolvedEl = if (el.lat == null && el.center == null && !el.nodes.isNullOrEmpty()) {
        var sumLat = 0.0
        var sumLon = 0.0
        var count = 0
        for (nid in el.nodes) {
          val pt = nodeCoords[nid]
          if (pt != null) {
            sumLat += pt.first
            sumLon += pt.second
            count++
          }
        }
        if (count > 0) {
          el.copy(center = OverpassCenter(sumLat / count, sumLon / count))
        } else {
          el
        }
      } else {
        el
      }

      val spot = mapElementToCoolSpot(resolvedEl) ?: continue
      if (spot.category == SpotCategory.TREE_SHADE && el.tags["natural"] == "tree") {
        treeCounter++
        if (treeCounter > maxIndividualTrees) continue
      }
      if (spot.category == SpotCategory.SHADED_BENCH_SHELTER && el.tags["amenity"] == "bench") {
        benchCounter++
        if (benchCounter > maxBenchesPerTile) continue
      }
      spots.add(spot)
    }
    return spots
  }

  private data class PhotonTask(
    val url: String,
    val fallbackCategory: SpotCategory,
    val filterByBounds: Boolean = true,
  )

  private suspend fun fetchFromPhotonMultiSource(
    bbox: BoundingBoxCoords,
    onBatchLoaded: (List<CoolSpot>) -> Unit = {},
  ): List<CoolSpot> = coroutineScope {
    val centerLat = (bbox.south + bbox.north) / 2.0
    val centerLon = (bbox.west + bbox.east) / 2.0
    val latSpan = (bbox.north - bbox.south).coerceAtLeast(0.008)
    val lonSpan = (bbox.east - bbox.west).coerceAtLeast(0.010)

    // Pad bbox slightly so edge spots in the user's view are never cut off
    val minLat = bbox.south - latSpan * 0.15
    val maxLat = bbox.north + latSpan * 0.15
    val minLon = bbox.west - lonSpan * 0.15
    val maxLon = bbox.east + lonSpan * 0.15
    val photonBbox = "$minLon,$minLat,$maxLon,$maxLat"

    // Compute approximate radius in km for /reverse queries (1 deg lat ~ 111 km)
    val latKm = latSpan * 111.0
    val lonKm = lonSpan * 111.0 * cos(Math.toRadians(centerLat)).coerceAtLeast(0.2)
    val radiusKm = max(latKm, lonKm).coerceIn(2.5, 25.0).roundToInt()

    val tasks = mutableListOf<PhotonTask>()

    // --- 1. ICE CREAM (Eisdielen, Eiscafés, Gelaterias, Frozen Yogurt) ---
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50" +
          "&osm_tag=amenity:ice_cream&osm_tag=shop:ice_cream&osm_tag=shop:frozen_yogurt",
        fallbackCategory = SpotCategory.ICE_CREAM,
      )
    )
    // Also query 4 quadrant centers for ICE_CREAM so large viewports find ALL ice cream parlors
    val qLatOff = latSpan * 0.25
    val qLonOff = lonSpan * 0.25
    listOf(
      (centerLat + qLatOff) to (centerLon - qLonOff),
      (centerLat + qLatOff) to (centerLon + qLonOff),
      (centerLat - qLatOff) to (centerLon - qLonOff),
      (centerLat - qLatOff) to (centerLon + qLonOff),
    ).forEach { (qLat, qLon) ->
      tasks.add(
        PhotonTask(
          url = "https://photon.komoot.io/reverse?lon=$qLon&lat=$qLat&radius=$radiusKm&limit=50" +
            "&osm_tag=amenity:ice_cream&osm_tag=shop:ice_cream",
          fallbackCategory = SpotCategory.ICE_CREAM,
        )
      )
    }
    // Text queries for Eiscafés tagged as amenity=cafe or shop=*
    listOf("Eiscafe", "Eisdiele", "Gelato", "Gelateria", "Eis").forEach { kw ->
      val enc = URLEncoder.encode(kw, "UTF-8")
      tasks.add(
        PhotonTask(
          url = "https://photon.komoot.io/api/?q=$enc&bbox=$photonBbox&limit=50&lang=de" +
            "&osm_tag=amenity:ice_cream&osm_tag=shop:ice_cream&osm_tag=amenity:cafe&osm_tag=shop:frozen_yogurt&osm_tag=shop:confectionery",
          fallbackCategory = SpotCategory.ICE_CREAM,
        )
      )
    }

    // --- 2. DRINKING WATER & REFILL ---
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50" +
          "&osm_tag=amenity:drinking_water&osm_tag=man_made:water_tap&osm_tag=man_made:drinking_fountain",
        fallbackCategory = SpotCategory.DRINKING_WATER,
      )
    )
    listOf("Trinkwasser", "Trinkbrunnen", "Trinkwasserbrunnen").forEach { kw ->
      val enc = URLEncoder.encode(kw, "UTF-8")
      tasks.add(
        PhotonTask(
          url = "https://photon.komoot.io/api/?q=$enc&bbox=$photonBbox&limit=40&lang=de",
          fallbackCategory = SpotCategory.DRINKING_WATER,
        )
      )
    }

    // --- 3. FOUNTAINS (Springbrunnen & Wasserspiele) ---
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50" +
          "&osm_tag=amenity:fountain",
        fallbackCategory = SpotCategory.FOUNTAIN,
      )
    )
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/api/?q=Brunnen&bbox=$photonBbox&limit=50&lang=de" +
          "&osm_tag=amenity:fountain&osm_tag=amenity:drinking_water&osm_tag=man_made:water_well",
        fallbackCategory = SpotCategory.FOUNTAIN,
      )
    )

    // --- 4. SWIMMING & BATHING (Freibad, Badesee, Strandbad, Flussbad) ---
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50" +
          "&osm_tag=leisure:swimming_area&osm_tag=leisure:water_park&osm_tag=leisure:bathing_place" +
          "&osm_tag=leisure:swimming_pool&osm_tag=leisure:beach_resort&osm_tag=natural:beach",
        fallbackCategory = SpotCategory.SWIMMING_BATHING,
      )
    )
    listOf("Freibad", "Badesee", "Strandbad", "Schwimmbad", "Naturbad").forEach { kw ->
      val enc = URLEncoder.encode(kw, "UTF-8")
      tasks.add(
        PhotonTask(
          url = "https://photon.komoot.io/api/?q=$enc&bbox=$photonBbox&limit=35&lang=de",
          fallbackCategory = SpotCategory.SWIMMING_BATHING,
        )
      )
    }

    // --- 5. WATER PLAYGROUND & MIST (Wasserspielplatz, Planschbecken, Außendusche) ---
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50" +
          "&osm_tag=playground:splash_pad&osm_tag=playground:water&osm_tag=amenity:shower",
        fallbackCategory = SpotCategory.WATER_PLAYGROUND,
      )
    )
    listOf("Wasserspielplatz", "Planschbecken", "Wasserspiele", "Matschanlage").forEach { kw ->
      val enc = URLEncoder.encode(kw, "UTF-8")
      tasks.add(
        PhotonTask(
          url = "https://photon.komoot.io/api/?q=$enc&bbox=$photonBbox&limit=35&lang=de",
          fallbackCategory = SpotCategory.WATER_PLAYGROUND,
        )
      )
    }

    // --- 6. KNEIPP ---
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50" +
          "&osm_tag=amenity:kneipp_water_facility",
        fallbackCategory = SpotCategory.KNEIPP,
      )
    )
    listOf("Kneipp", "Wassertretbecken").forEach { kw ->
      val enc = URLEncoder.encode(kw, "UTF-8")
      tasks.add(
        PhotonTask(
          url = "https://photon.komoot.io/api/?q=$enc&bbox=$photonBbox&limit=30&lang=de",
          fallbackCategory = SpotCategory.KNEIPP,
        )
      )
    }

    // --- 7. PARKS & GREEN SPACES ---
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50" +
          "&osm_tag=leisure:park&osm_tag=leisure:garden&osm_tag=landuse:recreation_ground&osm_tag=leisure:nature_reserve",
        fallbackCategory = SpotCategory.PARK,
      )
    )
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/api/?q=Park&bbox=$photonBbox&limit=50&lang=de" +
          "&osm_tag=leisure:park&osm_tag=leisure:garden&osm_tag=leisure:nature_reserve",
        fallbackCategory = SpotCategory.PARK,
      )
    )

    // --- 8. CHURCHES & COOL HISTORIC BUILDINGS ---
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50" +
          "&osm_tag=amenity:place_of_worship&osm_tag=building:church&osm_tag=building:cathedral" +
          "&osm_tag=building:chapel&osm_tag=historic:castle&osm_tag=historic:monastery",
        fallbackCategory = SpotCategory.CHURCH_COOL_BUILDING,
      )
    )

    // --- 9. LIBRARIES ---
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50" +
          "&osm_tag=amenity:library",
        fallbackCategory = SpotCategory.LIBRARY,
      )
    )
    listOf("Bibliothek", "Bücherei").forEach { kw ->
      val enc = URLEncoder.encode(kw, "UTF-8")
      tasks.add(
        PhotonTask(
          url = "https://photon.komoot.io/api/?q=$enc&bbox=$photonBbox&limit=35&lang=de" +
            "&osm_tag=amenity:library&osm_tag=building:library",
          fallbackCategory = SpotCategory.LIBRARY,
        )
      )
    }

    // --- 10. MUSEUMS ---
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50" +
          "&osm_tag=tourism:museum&osm_tag=tourism:gallery&osm_tag=amenity:arts_centre",
        fallbackCategory = SpotCategory.MUSEUM,
      )
    )

    // --- 11. SHADED BENCH & PAVILION / SHELTER ---
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50" +
          "&osm_tag=amenity:shelter&osm_tag=amenity:bench&osm_tag=building:pavilion&osm_tag=leisure:gazebo",
        fallbackCategory = SpotCategory.SHADED_BENCH_SHELTER,
      )
    )
    listOf("Schutzhütte", "Pavillon", "Unterstand").forEach { kw ->
      val enc = URLEncoder.encode(kw, "UTF-8")
      tasks.add(
        PhotonTask(
          url = "https://photon.komoot.io/api/?q=$enc&bbox=$photonBbox&limit=35&lang=de" +
            "&osm_tag=amenity:shelter&osm_tag=building:pavilion&osm_tag=leisure:gazebo&osm_tag=tourism:picnic_site",
          fallbackCategory = SpotCategory.SHADED_BENCH_SHELTER,
        )
      )
    }

    // --- 12. TREE SHADE & SHADED ALLEYS ---
    tasks.add(
      PhotonTask(
        url = "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50" +
          "&osm_tag=natural:tree&osm_tag=natural:tree_row",
        fallbackCategory = SpotCategory.TREE_SHADE,
      )
    )

    val sem = Semaphore(8)
    val allResults = Collections.synchronizedList(mutableListOf<CoolSpot>())

    tasks.map { task ->
      async {
        sem.withPermit {
          val spots = runCatching {
            val responseText = executeGetWithGzip(task.url, connectTimeoutMs = 20000, readTimeoutMs = 45000)
            val parsed = json.decodeFromString<PhotonResponse>(responseText)
            parsed.features.mapNotNull { feat ->
              mapPhotonFeatureToCoolSpot(
                feat = feat,
                fallbackCategory = task.fallbackCategory,
                minLat = if (task.filterByBounds) minLat else -90.0,
                maxLat = if (task.filterByBounds) maxLat else 90.0,
                minLon = if (task.filterByBounds) minLon else -180.0,
                maxLon = if (task.filterByBounds) maxLon else 180.0,
              )
            }
          }.getOrDefault(emptyList())

          if (spots.isNotEmpty()) {
            allResults.addAll(spots)
            onBatchLoaded(spots)
          }
          spots
        }
      }
    }.awaitAll()

    allResults.toList()
  }

  private suspend fun fetchExpandedRadiusFallback(
    bbox: BoundingBoxCoords,
    radiusKm: Int = 20,
  ): List<CoolSpot> = coroutineScope {
    val centerLat = (bbox.south + bbox.north) / 2.0
    val centerLon = (bbox.west + bbox.east) / 2.0

    val fallbackTasks = listOf(
      "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50&osm_tag=amenity:ice_cream&osm_tag=shop:ice_cream" to SpotCategory.ICE_CREAM,
      "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50&osm_tag=amenity:drinking_water&osm_tag=amenity:fountain" to SpotCategory.DRINKING_WATER,
      "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50&osm_tag=leisure:swimming_area&osm_tag=leisure:water_park&osm_tag=leisure:bathing_place&osm_tag=natural:beach" to SpotCategory.SWIMMING_BATHING,
      "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50&osm_tag=leisure:park&osm_tag=leisure:garden" to SpotCategory.PARK,
      "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50&osm_tag=amenity:place_of_worship&osm_tag=building:church&osm_tag=historic:castle" to SpotCategory.CHURCH_COOL_BUILDING,
      "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50&osm_tag=amenity:shelter&osm_tag=amenity:bench" to SpotCategory.SHADED_BENCH_SHELTER,
      "https://photon.komoot.io/reverse?lon=$centerLon&lat=$centerLat&radius=$radiusKm&limit=50&osm_tag=tourism:museum&osm_tag=amenity:library" to SpotCategory.MUSEUM,
    )

    fallbackTasks.map { (url, cat) ->
      async {
        runCatching {
          val text = executeGetWithGzip(url, connectTimeoutMs = 25000, readTimeoutMs = 60000)
          val parsed = json.decodeFromString<PhotonResponse>(text)
          parsed.features.mapNotNull { feat ->
            mapPhotonFeatureToCoolSpot(feat, cat, -90.0, 90.0, -180.0, 180.0)
          }
        }.getOrDefault(emptyList())
      }
    }.awaitAll().flatten()
  }

  private fun mapPhotonFeatureToCoolSpot(
    feat: PhotonFeatureItem,
    fallbackCategory: SpotCategory,
    minLat: Double,
    maxLat: Double,
    minLon: Double,
    maxLon: Double,
  ): CoolSpot? {
    val coords = feat.geometry?.coordinates ?: return null
    if (coords.size < 2) return null
    val lon = coords[0]
    val lat = coords[1]
    if (lat !in minLat..maxLat || lon !in minLon..maxLon) return null

    val props = feat.properties
    val osmId = props["osm_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
      ?: ((lat * 1_000_000).toLong() * 31L + (lon * 1_000_000).toLong())
    val osmKey = props["osm_key"]?.jsonPrimitive?.contentOrNull ?: ""
    val osmValue = props["osm_value"]?.jsonPrimitive?.contentOrNull ?: ""
    val rawName = props["name"]?.jsonPrimitive?.contentOrNull?.trim()

    // Filter out non-POI results like streets, bus stops, residential buildings or offices when keyword-searching
    if (osmKey in setOf("highway", "railway", "Public_transport", "public_transport", "boundary", "place", "landuse", "Aeroway", "aeroway")) {
      if (osmKey == "landuse" && osmValue in setOf("recreation_ground", "forest", "village_green", "orchard")) {
        // Valid green space
      } else {
        return null
      }
    }
    if (osmKey == "building" && osmValue in setOf("yes", "apartments", "residential", "house", "commercial", "industrial", "retail", "office", "detached")) {
      if (fallbackCategory == SpotCategory.ICE_CREAM && rawName != null && iceCreamNameRegex.containsMatchIn(rawName)) {
        // Keep ice cream shop mapped on building
      } else {
        return null
      }
    }

    // For ICE_CREAM fallback on generic amenity=cafe/restaurant, verify it's actually an ice cream place
    if (fallbackCategory == SpotCategory.ICE_CREAM && osmValue !in setOf("ice_cream", "frozen_yogurt")) {
      if (rawName == null || !iceCreamNameRegex.containsMatchIn(rawName)) {
        return null
      }
    }

    // Determine exact category from osm_key / osm_value / name
    val category = when {
      osmValue in setOf("ice_cream", "frozen_yogurt") ||
        (rawName != null && iceCreamNameRegex.containsMatchIn(rawName) && fallbackCategory == SpotCategory.ICE_CREAM) ->
        SpotCategory.ICE_CREAM
      osmValue == "drinking_water" || osmValue == "water_tap" || osmValue == "drinking_fountain" ->
        SpotCategory.DRINKING_WATER
      osmValue == "kneipp_water_facility" || (rawName != null && kneippNameRegex.containsMatchIn(rawName)) ->
        SpotCategory.KNEIPP
      osmValue in setOf("splash_pad", "shower") ||
        (rawName != null && waterPlayNameRegex.containsMatchIn(rawName)) ->
        SpotCategory.WATER_PLAYGROUND
      osmValue in setOf("swimming_area", "water_park", "bathing_place", "swimming_pool", "beach_resort", "beach", "public_bath") ->
        SpotCategory.SWIMMING_BATHING
      osmValue == "fountain" -> SpotCategory.FOUNTAIN
      osmValue == "library" -> SpotCategory.LIBRARY
      osmValue in setOf("museum", "gallery", "arts_centre") -> SpotCategory.MUSEUM
      osmValue in setOf("place_of_worship", "church", "cathedral", "chapel", "castle", "monastery") ->
        SpotCategory.CHURCH_COOL_BUILDING
      osmValue in setOf("shelter", "bench", "pavilion", "gazebo", "picnic_site") ->
        SpotCategory.SHADED_BENCH_SHELTER
      osmValue in setOf("park", "garden", "recreation_ground", "nature_reserve", "forest", "wood", "village_green") ->
        SpotCategory.PARK
      osmValue in setOf("tree", "tree_row") -> SpotCategory.TREE_SHADE
      else -> fallbackCategory
    }

    val title = when {
      !rawName.isNullOrBlank() -> rawName
      category == SpotCategory.ICE_CREAM -> "Eisdiele / Eiscafé"
      category == SpotCategory.DRINKING_WATER -> "Trinkwasserbrunnen"
      category == SpotCategory.FOUNTAIN -> "Springbrunnen / Wasserspiel"
      category == SpotCategory.SWIMMING_BATHING -> "Badestelle / Freibad"
      category == SpotCategory.WATER_PLAYGROUND -> "Wasserspielplatz / Abkühlung"
      category == SpotCategory.KNEIPP -> "Kneipp-Wasseranlage"
      category == SpotCategory.PARK -> "Park / Grünanlage"
      category == SpotCategory.CHURCH_COOL_BUILDING -> "Kühle Kirche / Historisches Bauwerk"
      category == SpotCategory.LIBRARY -> "Öffentliche Bibliothek"
      category == SpotCategory.MUSEUM -> "Museum"
      category == SpotCategory.SHADED_BENCH_SHELTER ->
        if (osmValue == "bench") "Schattige Sitzbank" else "Schatten-Pavillon / Unterstand"
      category == SpotCategory.TREE_SHADE -> "Schattenbaum / Baumallee"
      else -> category.titleDe
    }

    val street = props["street"]?.jsonPrimitive?.contentOrNull
    val housenumber = props["housenumber"]?.jsonPrimitive?.contentOrNull
    val postcode = props["postcode"]?.jsonPrimitive?.contentOrNull
    val city = props["city"]?.jsonPrimitive?.contentOrNull
      ?: props["town"]?.jsonPrimitive?.contentOrNull
      ?: props["village"]?.jsonPrimitive?.contentOrNull
      ?: props["district"]?.jsonPrimitive?.contentOrNull

    val address = buildString {
      if (!street.isNullOrBlank()) {
        append(street)
        if (!housenumber.isNullOrBlank()) append(" $housenumber")
      }
      if (!city.isNullOrBlank()) {
        if (isNotEmpty()) append(", ")
        if (!postcode.isNullOrBlank()) append("$postcode ")
        append(city)
      }
    }.takeIf { it.isNotBlank() }

    return CoolSpot(
      id = osmId,
      lat = lat,
      lon = lon,
      category = category,
      name = title,
      description = category.subtitleDe,
      address = address,
      tags = mapOf(osmKey to osmValue),
    )
  }

  private fun fetchFromOverpassServers(
    bbox: BoundingBoxCoords,
    includeIndividualTrees: Boolean,
  ): List<CoolSpot> {
    val centerLat = (bbox.south + bbox.north) / 2.0
    val centerLon = (bbox.west + bbox.east) / 2.0
    val isInSwitzerland = centerLat in 45.8..47.9 && centerLon in 5.9..10.6

    val endpoints = buildList {
      if (isInSwitzerland) add("https://overpass.osm.ch/api/interpreter")
      add("https://overpass-api.de/api/interpreter")
      add("https://overpass.private.coffee/api/interpreter")
      add("https://overpass.kumi.systems/api/interpreter")
    }

    val b = "${bbox.south},${bbox.west},${bbox.north},${bbox.east}"
    val treeNodeQuery = if (includeIndividualTrees) {
      """
      node["natural"="tree"]($b);
      way["natural"="tree_row"]($b);
      """.trimIndent()
    } else {
      """
      way["natural"="tree_row"]($b);
      """.trimIndent()
    }

    val query = """
      [out:json][timeout:45];
      (
        node["amenity"="drinking_water"]($b);
        way["amenity"="drinking_water"]($b);
        node["drinking_water"="yes"]($b);
        node["refill"="yes"]($b);
        way["refill"="yes"]($b);
        node["drinking_water:refill"="yes"]($b);
        way["drinking_water:refill"="yes"]($b);
        node["amenity"="kneipp_water_facility"]($b);
        way["amenity"="kneipp_water_facility"]($b);
        node["amenity"="fountain"]($b);
        way["amenity"="fountain"]($b);
        node["leisure"="swimming_area"]($b);
        way["leisure"="swimming_area"]($b);
        node["leisure"="water_park"]($b);
        way["leisure"="water_park"]($b);
        node["playground"="splash_pad"]($b);
        way["playground"="splash_pad"]($b);
        node["playground"="water"]($b);
        node["amenity"="shower"]["indoor"!="yes"]($b);
        node["amenity"="ice_cream"]($b);
        way["amenity"="ice_cream"]($b);
        node["shop"="ice_cream"]($b);
        way["shop"="ice_cream"]($b);
        node["shop"="frozen_yogurt"]($b);
        way["shop"="frozen_yogurt"]($b);
        node["cuisine"~"ice_cream|gelato|frozen_yogurt",i]($b);
        way["cuisine"~"ice_cream|gelato|frozen_yogurt",i]($b);
        node["ice_cream"="yes"]($b);
        way["ice_cream"="yes"]($b);
        node["leisure"="bathing_place"]($b);
        way["leisure"="bathing_place"]($b);
        node["leisure"="garden"]($b);
        way["leisure"="garden"]($b);
        node["amenity"="place_of_worship"]($b);
        way["amenity"="place_of_worship"]($b);
        node["amenity"="shelter"]($b);
        way["amenity"="shelter"]($b);
        node["leisure"="park"]($b);
        way["leisure"="park"]($b);
        node["amenity"="library"]($b);
        way["amenity"="library"]($b);
        node["tourism"="museum"]($b);
        way["tourism"="museum"]($b);
        $treeNodeQuery
      );
      out center 500;
    """.trimIndent()

    for (endpoint in endpoints) {
      try {
        val responseText = executePost(endpoint, "data=" + URLEncoder.encode(query, "UTF-8"))
        val parsed = json.decodeFromString<OverpassResponse>(responseText)
        val spots = parsed.elements.mapNotNull { element -> mapElementToCoolSpot(element) }
        if (spots.isNotEmpty()) return spots
      } catch (_: Exception) {
      }
    }
    return emptyList()
  }

  suspend fun calculateRoute(
    originLat: Double,
    originLon: Double,
    destination: CoolSpot,
    travelMode: TravelMode,
  ): Result<RouteInfo> = calculateMultiRoute(
    originLat = originLat,
    originLon = originLon,
    destinations = listOf(destination),
    travelMode = travelMode,
  )

  suspend fun calculateMultiRoute(
    originLat: Double,
    originLon: Double,
    destinations: List<CoolSpot>,
    travelMode: TravelMode,
  ): Result<RouteInfo> = withContext(Dispatchers.IO) {
    if (destinations.isEmpty()) {
      return@withContext Result.failure(IllegalArgumentException("No destinations provided"))
    }

    // Solve TSP order for waypoints using Nearest Neighbor
    val initialOrderedDestinations = optimizeWaypointOrder(originLat, originLon, destinations)
    val isMultiStop = initialOrderedDestinations.size > 1

    val coordsString = "$originLon,$originLat;" + initialOrderedDestinations.joinToString(";") { "${it.lon},${it.lat}" }

    // If multi-stop, try OSRM Trip API first (as it solves TSP optimal ordering!)
    if (isMultiStop) {
      val tripProfile = when (travelMode) {
        TravelMode.WALKING -> "foot"
        TravelMode.BICYCLING -> "bike"
        TravelMode.DRIVING -> "driving"
      }
      val tripUrl = "https://router.project-osrm.org/trip/v1/$tripProfile/$coordsString?overview=full&geometries=geojson&source=first&roundtrip=false"
      try {
        val body = executeGetWithGzip(tripUrl, connectTimeoutMs = 15000, readTimeoutMs = 30000)
        val parsed = json.decodeFromString<OsrmTripResponse>(body)
        val trip = parsed.trips.firstOrNull()
        if (trip != null && trip.geometry != null && trip.geometry.coordinates.isNotEmpty()) {
          val pts = trip.geometry.coordinates.mapNotNull { coord ->
            if (coord.size >= 2) coord[1] to coord[0] else null
          }
          val distMeters = trip.distance.roundToInt().coerceAtLeast(1)
          val speedMetersPerSec = when (travelMode) {
            TravelMode.WALKING -> 1.35
            TravelMode.BICYCLING -> 4.2
            TravelMode.DRIVING -> 8.5
          }
          val durationSec = (distMeters / speedMetersPerSec).roundToInt()

          // If waypoints returned in trip order, map them back to spots
          var orderedSpots = initialOrderedDestinations
          if (parsed.waypoints.size >= destinations.size + 1) {
            val sortedWps = parsed.waypoints
              .filter { it.waypoint_index > 0 }
              .sortedBy { it.trips_index }
            if (sortedWps.size == initialOrderedDestinations.size) {
              orderedSpots = sortedWps.mapNotNull { wp ->
                initialOrderedDestinations.getOrNull(wp.waypoint_index - 1)
              }
            }
          }

          return@withContext Result.success(
            RouteInfo(
              targetSpots = orderedSpots,
              originLat = originLat,
              originLon = originLon,
              travelMode = travelMode,
              distanceMeters = distMeters,
              durationSeconds = durationSec,
              polylinePoints = pts,
            )
          )
        }
      } catch (_: Exception) {
      }
    }

    // Standard OSRM Route API mit den echten, modusspezifischen Profilen (foot für Fußgänger, bike für Rad, car für Auto)
    val endpoints = when (travelMode) {
      TravelMode.WALKING -> listOf(
        "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$coordsString?overview=full&geometries=geojson",
        "https://routing.openstreetmap.de/routed-foot/route/v1/driving/$coordsString?overview=full&geometries=geojson",
      )
      TravelMode.BICYCLING -> listOf(
        "https://routing.openstreetmap.de/routed-bike/route/v1/bike/$coordsString?overview=full&geometries=geojson",
        "https://routing.openstreetmap.de/routed-bike/route/v1/driving/$coordsString?overview=full&geometries=geojson",
      )
      TravelMode.DRIVING -> listOf(
        "https://routing.openstreetmap.de/routed-car/route/v1/driving/$coordsString?overview=full&geometries=geojson",
        "https://router.project-osrm.org/route/v1/driving/$coordsString?overview=full&geometries=geojson",
      )
    }

    for (url in endpoints) {
      try {
        val body = executeGetWithGzip(url, connectTimeoutMs = 15000, readTimeoutMs = 30000)
        val parsed = json.decodeFromString<OsrmRouteResponse>(body)
        val route = parsed.routes.firstOrNull()
        if (route != null && route.geometry != null && route.geometry.coordinates.isNotEmpty()) {
          val pts = route.geometry.coordinates.mapNotNull { coord ->
            if (coord.size >= 2) coord[1] to coord[0] else null
          }
          val distMeters = route.distance.roundToInt().coerceAtLeast(1)
          val speedMetersPerSec = when (travelMode) {
            TravelMode.WALKING -> 1.35
            TravelMode.BICYCLING -> 4.2
            TravelMode.DRIVING -> 8.5
          }
          val durationSec = if (url.contains("project-osrm.org")) {
            (distMeters / speedMetersPerSec).roundToInt()
          } else {
            route.duration.roundToInt().coerceAtLeast((distMeters / speedMetersPerSec).roundToInt())
          }

          return@withContext Result.success(
            RouteInfo(
              targetSpots = initialOrderedDestinations,
              originLat = originLat,
              originLon = originLon,
              travelMode = travelMode,
              distanceMeters = distMeters,
              durationSeconds = durationSec,
              polylinePoints = pts,
            )
          )
        }
      } catch (_: Exception) {
      }
    }

    // Fallback: Direct line calculation with realistic speed estimate (never fails!)
    val fallbackPoints = mutableListOf(originLat to originLon)
    var totalStraightDist = 0
    var currLat = originLat
    var currLon = originLon
    for (dest in initialOrderedDestinations) {
      val d = dest.distanceMetersTo(currLat, currLon)
      totalStraightDist += d
      fallbackPoints.add(dest.lat to dest.lon)
      currLat = dest.lat
      currLon = dest.lon
    }
    val estimatedRoadDist = (totalStraightDist * 1.28).roundToInt().coerceAtLeast(1)
    val speedMetersPerSec = when (travelMode) {
      TravelMode.WALKING -> 1.35
      TravelMode.BICYCLING -> 4.2
      TravelMode.DRIVING -> 8.5
    }
    val estimatedDurationSec = (estimatedRoadDist / speedMetersPerSec).roundToInt()

    Result.success(
      RouteInfo(
        targetSpots = initialOrderedDestinations,
        originLat = originLat,
        originLon = originLon,
        travelMode = travelMode,
        distanceMeters = estimatedRoadDist,
        durationSeconds = estimatedDurationSec,
        polylinePoints = fallbackPoints,
      )
    )
  }

  /**
   * Optimizes the visitation order of multiple spots starting at (originLat, originLon):
   * - For <= 8 stops: Exact Traveling Salesperson (TSP) branch-and-bound permutation for 100% optimal shortest path.
   * - For > 8 stops: Nearest-Neighbor + 2-Opt local search refinement.
   */
  internal fun optimizeWaypointOrder(
    originLat: Double,
    originLon: Double,
    destinations: List<CoolSpot>,
  ): List<CoolSpot> {
    if (destinations.size <= 1) return destinations

    if (destinations.size <= 8) {
      return exactShortestHamiltonianPath(originLat, originLon, destinations)
    }

    val nnOrder = nearestNeighborOrder(originLat, originLon, destinations)
    return twoOptOptimize(originLat, originLon, nnOrder)
  }

  private fun exactShortestHamiltonianPath(
    originLat: Double,
    originLon: Double,
    destinations: List<CoolSpot>,
  ): List<CoolSpot> {
    var bestOrder = destinations
    var bestDistance = Double.MAX_VALUE

    fun permute(list: MutableList<CoolSpot>, start: Int) {
      if (start == list.size) {
        var total = 0.0
        var cLat = originLat
        var cLon = originLon
        for (spot in list) {
          total += spot.distanceMetersTo(cLat, cLon)
          if (total >= bestDistance) return
          cLat = spot.lat
          cLon = spot.lon
        }
        if (total < bestDistance) {
          bestDistance = total
          bestOrder = list.toList()
        }
        return
      }
      for (i in start until list.size) {
        val temp = list[start]
        list[start] = list[i]
        list[i] = temp

        permute(list, start + 1)

        val revert = list[start]
        list[start] = list[i]
        list[i] = revert
      }
    }

    permute(destinations.toMutableList(), 0)
    return bestOrder
  }

  private fun nearestNeighborOrder(
    originLat: Double,
    originLon: Double,
    destinations: List<CoolSpot>,
  ): List<CoolSpot> {
    val remaining = destinations.toMutableList()
    val ordered = mutableListOf<CoolSpot>()
    var currentLat = originLat
    var currentLon = originLon

    while (remaining.isNotEmpty()) {
      val nextSpot = remaining.minByOrNull { it.distanceMetersTo(currentLat, currentLon) } ?: remaining.first()
      ordered.add(nextSpot)
      remaining.remove(nextSpot)
      currentLat = nextSpot.lat
      currentLon = nextSpot.lon
    }
    return ordered
  }

  private fun twoOptOptimize(
    originLat: Double,
    originLon: Double,
    route: List<CoolSpot>,
  ): List<CoolSpot> {
    val current = route.toMutableList()
    var improved = true
    var iterations = 0

    fun calculateTotalDist(pts: List<CoolSpot>): Double {
      var d = 0.0
      var cLat = originLat
      var cLon = originLon
      for (p in pts) {
        d += p.distanceMetersTo(cLat, cLon)
        cLat = p.lat
        cLon = p.lon
      }
      return d
    }

    var bestDist = calculateTotalDist(current)

    while (improved && iterations < 50) {
      improved = false
      iterations++
      for (i in 0 until current.size - 1) {
        for (k in i + 1 until current.size) {
          val candidate = current.toMutableList()
          var l = i
          var r = k
          while (l < r) {
            val tmp = candidate[l]
            candidate[l] = candidate[r]
            candidate[r] = tmp
            l++
            r--
          }
          val candDist = calculateTotalDist(candidate)
          if (candDist + 0.5 < bestDist) {
            bestDist = candDist
            for (idx in current.indices) current[idx] = candidate[idx]
            improved = true
          }
        }
      }
    }
    return current
  }

  suspend fun searchCityOrAddress(query: String): Result<List<NominatimResult>> =
    searchLocation(query)

  suspend fun searchLocation(query: String): Result<List<NominatimResult>> =
    withContext(Dispatchers.IO) {
      val trimmed = query.trim()
      if (trimmed.isEmpty()) return@withContext Result.success(emptyList())

      // 1. Primary fast location search via Photon (Komoot OpenStreetMap Search)
      try {
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val photonUrl = "https://photon.komoot.io/api/?q=$encoded&limit=6&lang=de"
        val body = executeGetWithGzip(photonUrl, connectTimeoutMs = 15000, readTimeoutMs = 30000)
        val photonParsed = json.decodeFromString<PhotonResponse>(body)
        if (photonParsed.features.isNotEmpty()) {
          val results = photonParsed.features.mapNotNull { feat ->
            val coords = feat.geometry?.coordinates ?: return@mapNotNull null
            if (coords.size < 2) return@mapNotNull null
            val lon = coords[0]
            val lat = coords[1]
            val props = feat.properties

            val name = props["name"]?.jsonPrimitive?.contentOrNull
            val street = props["street"]?.jsonPrimitive?.contentOrNull
            val housenumber = props["housenumber"]?.jsonPrimitive?.contentOrNull
            val city = props["city"]?.jsonPrimitive?.contentOrNull
              ?: props["town"]?.jsonPrimitive?.contentOrNull
              ?: props["village"]?.jsonPrimitive?.contentOrNull
              ?: props["county"]?.jsonPrimitive?.contentOrNull
            val state = props["state"]?.jsonPrimitive?.contentOrNull
            val country = props["country"]?.jsonPrimitive?.contentOrNull

            val streetPart = if (street != null) {
              if (housenumber != null) "$street $housenumber" else street
            } else null

            val displayParts = listOfNotNull(name, streetPart, city, state, country).distinct()
            val displayName = if (displayParts.isNotEmpty()) {
              displayParts.joinToString(", ")
            } else {
              trimmed
            }

            NominatimResult(
              display_name = displayName,
              name = name ?: city ?: displayName,
              lat = lat.toString(),
              lon = lon.toString(),
            )
          }
          if (results.isNotEmpty()) {
            return@withContext Result.success(results)
          }
        }
      } catch (_: Exception) {
      }

      // 2. Fallback to Nominatim
      try {
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=6&accept-language=de"
        val body = executeGetWithGzip(url, connectTimeoutMs = 15000, readTimeoutMs = 30000)
        val results = json.decodeFromString<List<NominatimResult>>(body)
        Result.success(results)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  private fun executeGetWithGzip(
    urlStr: String,
    connectTimeoutMs: Int = 30000,
    readTimeoutMs: Int = 90000,
  ): String {
    val url = URL(urlStr)
    val conn = (url.openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = connectTimeoutMs
      readTimeout = readTimeoutMs
      setRequestProperty("User-Agent", "CoolDown-App/1.4 (tanjareize@web.de)")
      setRequestProperty("Accept", "application/json")
      setRequestProperty("Accept-Encoding", "gzip")
    }
    val code = conn.responseCode
    if (code !in 200..299) {
      throw Exception("HTTP $code von ${url.host}")
    }
    val stream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
      GZIPInputStream(conn.inputStream)
    } else {
      conn.inputStream
    }
    return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
  }

  private fun executePost(endpointUrl: String, formBody: String): String {
    val url = URL(endpointUrl)
    val conn = (url.openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      doOutput = true
      connectTimeout = 25000
      readTimeout = 60000
      setRequestProperty("User-Agent", "CoolDown-App/1.4 (tanjareize@web.de)")
      setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
      setRequestProperty("Accept-Encoding", "gzip")
    }
    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
      writer.write(formBody)
      writer.flush()
    }
    val responseCode = conn.responseCode
    if (responseCode !in 200..299) {
      throw Exception("Overpass HTTP $responseCode")
    }
    val stream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
      GZIPInputStream(conn.inputStream)
    } else {
      conn.inputStream
    }
    return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
      reader.readText()
    }
  }

  private fun deduplicateSpots(spots: List<CoolSpot>): List<CoolSpot> {
    val result = mutableListOf<CoolSpot>()
    for (spot in spots) {
      val duplicate = result.any { existing ->
        (existing.id == spot.id && existing.category == spot.category) ||
          (existing.category == spot.category && existing.distanceMetersTo(spot.lat, spot.lon) < 12)
      }
      if (!duplicate) {
        result.add(spot)
      }
    }
    return result
  }

  private fun mapElementToCoolSpot(el: OverpassElement): CoolSpot? {
    val lat = el.lat ?: el.center?.lat ?: return null
    val lon = el.lon ?: el.center?.lon ?: return null
    val tags = el.tags
    if (tags.isEmpty()) return null

    val rawName = tags["name:de"] ?: tags["name:en"] ?: tags["name"] ?: tags["brand"] ?: tags["operator"]
    val rawDesc = tags["description:de"] ?: tags["description"] ?: ""
    val cuisine = tags["cuisine"] ?: ""

    val isIceCreamTagOrName =
      tags["amenity"] == "ice_cream" ||
        tags["shop"] == "ice_cream" ||
        tags["shop"] == "frozen_yogurt" ||
        tags["vending"] == "ice_cream" ||
        tags["ice_cream"] == "yes" ||
        tags["diet:ice_cream"] == "yes" ||
        cuisine.contains("ice_cream", ignoreCase = true) ||
        cuisine.contains("gelato", ignoreCase = true) ||
        cuisine.contains("frozen_yogurt", ignoreCase = true) ||
        ((tags["amenity"] in setOf("cafe", "fast_food", "restaurant", "bar", "bistro") || tags["shop"] != null) &&
          rawName != null && iceCreamNameRegex.containsMatchIn(rawName))

    val category = when {
      isIceCreamTagOrName -> SpotCategory.ICE_CREAM
      tags["refill"] == "yes" || tags["drinking_water:refill"] == "yes" ||
        tags["free_water"] == "yes" || tags["free_water:refill"] == "yes" -> SpotCategory.REFILL_STATION
      tags["amenity"] == "drinking_water" || tags["drinking_water"] == "yes" ||
        tags["drinking_water:legal"] == "yes" ||
        tags["man_made"] == "drinking_fountain" ||
        (tags["man_made"] == "water_tap" && tags["drinking_water"] != "no") ||
        (tags["amenity"] == "fountain" && tags["drinking_water"] == "yes") ||
        (tags["natural"] == "spring" && tags["drinking_water"] == "yes") -> SpotCategory.DRINKING_WATER
      tags["amenity"] == "kneipp_water_facility" || tags["kneipp"] == "yes" ||
        (rawName != null && kneippNameRegex.containsMatchIn(rawName)) ||
        kneippNameRegex.containsMatchIn(rawDesc) -> SpotCategory.KNEIPP
      tags["playground"] == "splash_pad" || tags["playground"] == "water" ||
        tags["playground:water"] == "yes" || tags["water_play"] == "yes" ||
        tags["cooling"] == "mist" || tags["mist_sprayer"] == "yes" ||
        (tags["amenity"] == "shower" && tags["indoor"] != "yes") ||
        (tags["leisure"] == "playground" &&
          ((rawName != null && waterPlayNameRegex.containsMatchIn(rawName)) ||
            waterPlayNameRegex.containsMatchIn(rawDesc))) -> SpotCategory.WATER_PLAYGROUND
      tags["leisure"] == "swimming_area" || tags["leisure"] == "water_park" ||
        tags["leisure"] == "bathing_place" || tags["leisure"] == "beach_resort" ||
        (tags["leisure"] == "swimming_pool" && tags["access"] != "private" && tags["indoor"] != "yes") ||
        tags["natural"] == "beach" ||
        (tags["sport"] == "swimming" && tags["access"] != "private" && tags["indoor"] != "yes") ||
        (tags["amenity"] == "public_bath" && tags["indoor"] != "yes") -> SpotCategory.SWIMMING_BATHING
      tags["amenity"] == "fountain" || tags["man_made"] == "water_fountain" || tags["water"] == "fountain" ->
        SpotCategory.FOUNTAIN
      tags["amenity"] == "library" || tags["building"] == "library" -> SpotCategory.LIBRARY
      tags["tourism"] == "museum" || tags["tourism"] == "gallery" || tags["amenity"] == "arts_centre" ->
        SpotCategory.MUSEUM
      tags["amenity"] == "place_of_worship" || tags["building"] == "church" ||
        tags["building"] == "cathedral" || tags["building"] == "chapel" ||
        tags["building"] == "monastery" ||
        tags["historic"] == "castle" || tags["historic"] == "monastery" || tags["historic"] == "church" ->
        SpotCategory.CHURCH_COOL_BUILDING
      tags["amenity"] == "shelter" || tags["building"] == "pavilion" ||
        tags["leisure"] == "gazebo" || tags["leisure"] == "bandstand" ||
        (tags["leisure"] == "picnic_table" && tags["covered"] == "yes") ||
        (tags["amenity"] == "bench" && (tags["covered"] == "yes" || tags["shade"] == "yes" || tags["backrest"] == "yes")) ->
        SpotCategory.SHADED_BENCH_SHELTER
      tags["leisure"] == "park" || (tags["leisure"] == "garden" && tags["access"] != "private") ||
        tags["leisure"] == "nature_reserve" ||
        tags["landuse"] == "forest" || tags["landuse"] == "recreation_ground" ||
        tags["landuse"] == "village_green" ||
        tags["natural"] == "wood" -> SpotCategory.PARK
      tags["natural"] == "tree" || tags["natural"] == "tree_row" || tags["trees"] == "yes" -> SpotCategory.TREE_SHADE
      else -> return null
    }

    val name = when {
      !rawName.isNullOrBlank() -> rawName
      category == SpotCategory.DRINKING_WATER -> "Trinkwasserbrunnen"
      category == SpotCategory.REFILL_STATION -> {
        val shopOrAmenity = tags["amenity"] ?: tags["shop"]
        if (!shopOrAmenity.isNullOrBlank()) "Refill-Station ($shopOrAmenity)" else "Refill-Station"
      }
      category == SpotCategory.SWIMMING_BATHING -> "Badestelle / Freibad"
      category == SpotCategory.WATER_PLAYGROUND -> {
        if (tags["amenity"] == "shower") "Öffentliche Außendusche" else "Wasserspielplatz / Sprühnebel"
      }
      category == SpotCategory.FOUNTAIN -> "Springbrunnen / Wasserspiel"
      category == SpotCategory.KNEIPP -> "Kneipp-Wasseranlage"
      category == SpotCategory.PARK -> "Park / Grünanlage"
      category == SpotCategory.ICE_CREAM -> "Eisdiele / Eiscafé"
      category == SpotCategory.CHURCH_COOL_BUILDING -> "Kühle Kirche / Historisches Bauwerk"
      category == SpotCategory.LIBRARY -> "Öffentliche Bibliothek"
      category == SpotCategory.MUSEUM -> "Museum"
      category == SpotCategory.SHADED_BENCH_SHELTER -> {
        if (tags["amenity"] == "bench") "Schattige Sitzbank" else "Schatten-Pavillon / Unterstand"
      }
      category == SpotCategory.TREE_SHADE -> {
        val species = tags["species:de"] ?: tags["species:en"] ?: tags["genus:de"] ?: tags["species"] ?: tags["genus"]
        if (tags["natural"] == "tree_row" || tags["trees"] == "yes") {
          "Schattige Baumallee"
        } else if (!species.isNullOrBlank()) {
          "Schattenbaum ($species)"
        } else {
          "Schattenspender (Baum)"
        }
      }
      else -> category.titleDe
    }

    val descParts = mutableListOf<String>()
    tags["description:de"]?.let { descParts.add(it) }
      ?: tags["description:en"]?.let { descParts.add(it) }
      ?: tags["description"]?.let { descParts.add(it) }
    tags["note"]?.let { if (it !in descParts) descParts.add(it) }
    if (category == SpotCategory.TREE_SHADE) {
      val foliage = when (tags["leaf_type"]) {
        "broadleaved" -> "Laubbaum"
        "needleleaved" -> "Nadelbaum"
        else -> null
      }
      foliage?.let { descParts.add(it) }
      tags["crown_diameter"]?.let { descParts.add("Kronendurchmesser: $it m") }
    }
    if (tags["air_conditioning"] == "yes") {
      descParts.add("Klimatisiert (AC)")
    }

    val street = tags["addr:street"]
    val houseNumber = tags["addr:housenumber"]
    val city = tags["addr:city"]
    val address = buildString {
      if (!street.isNullOrBlank()) {
        append(street)
        if (!houseNumber.isNullOrBlank()) append(" $houseNumber")
      }
      if (!city.isNullOrBlank()) {
        if (isNotEmpty()) append(", ")
        append(city)
      }
    }.takeIf { it.isNotBlank() }

    return CoolSpot(
      id = el.id,
      lat = lat,
      lon = lon,
      category = category,
      name = name,
      description = descParts.joinToString(" • ").takeIf { it.isNotBlank() },
      openingHours = tags["opening_hours"],
      operator = tags["operator"],
      wheelchair = tags["wheelchair"],
      bottle = tags["bottle"],
      fee = tags["fee"],
      address = address,
      website = tags["website"] ?: tags["contact:website"],
      tags = tags,
    )
  }
}
