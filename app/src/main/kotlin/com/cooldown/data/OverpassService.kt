package com.cooldown.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
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

  // Fast & responsive mirrors first
  private val overpassEndpoints = listOf(
    "https://overpass.osm.ch/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
  )

  suspend fun fetchCoolSpots(
    bbox: BoundingBoxCoords,
    includeIndividualTrees: Boolean,
  ): Result<List<CoolSpot>> = withContext(Dispatchers.IO) {
    val combinedSpots = mutableListOf<CoolSpot>()
    var anySuccess = false
    var lastError: Throwable? = null

    supervisorScope {
      // 1. Official OpenStreetMap API v0.6 map.json (fastest and most accurate for immediate surroundings)
      val osmMapDeferred = async {
        runCatching { fetchFromOfficialOsmMapApi(bbox, includeIndividualTrees) }
      }

      // 2. High-speed parallel Nominatim POI search across the viewport
      val nominatimDeferred = async {
        runCatching { fetchFromNominatimBoundedSearch(bbox) }
      }

      // 3. Overpass API query as background supplement
      val overpassDeferred = async {
        runCatching { fetchFromOverpassServers(bbox, includeIndividualTrees) }
      }

      val osmResult = osmMapDeferred.await()
      osmResult.onSuccess { spots ->
        if (spots.isNotEmpty()) anySuccess = true
        combinedSpots.addAll(spots)
      }.onFailure { lastError = it }

      val nomResult = nominatimDeferred.await()
      nomResult.onSuccess { spots ->
        if (spots.isNotEmpty()) anySuccess = true
        combinedSpots.addAll(spots)
      }.onFailure { if (lastError == null) lastError = it }

      val ovpResult = overpassDeferred.await()
      ovpResult.onSuccess { spots ->
        if (spots.isNotEmpty()) anySuccess = true
        combinedSpots.addAll(spots)
      }.onFailure { if (lastError == null) lastError = it }
    }

    val deduplicated = deduplicateSpots(combinedSpots)
    if (deduplicated.isNotEmpty() || anySuccess) {
      Result.success(deduplicated)
    } else {
      Result.failure(lastError ?: Exception("Keine Verbindung zu den OpenStreetMap-Servern möglich"))
    }
  }

  private fun fetchFromOfficialOsmMapApi(
    bbox: BoundingBoxCoords,
    includeIndividualTrees: Boolean,
  ): List<CoolSpot> {
    val centerLat = (bbox.south + bbox.north) / 2.0
    val centerLon = (bbox.west + bbox.east) / 2.0
    val maxSpan = 0.016 // ~1.6 km
    val latSpan = (bbox.north - bbox.south).coerceIn(0.004, maxSpan)
    val lonSpan = (bbox.east - bbox.west).coerceIn(0.004, maxSpan)

    val minLon = centerLon - lonSpan / 2.0
    val minLat = centerLat - latSpan / 2.0
    val maxLon = centerLon + lonSpan / 2.0
    val maxLat = centerLat + latSpan / 2.0

    val urlStr = "https://api.openstreetmap.org/api/0.6/map.json?bbox=$minLon,$minLat,$maxLon,$maxLat"
    val body = executeGetWithGzip(urlStr, connectTimeoutMs = 4500, readTimeoutMs = 7000)
    val parsed = json.decodeFromString<OverpassResponse>(body)

    val nodeCoords = HashMap<Long, Pair<Double, Double>>(parsed.elements.size)
    for (el in parsed.elements) {
      if (el.type == "node" && el.lat != null && el.lon != null) {
        nodeCoords[el.id] = el.lat to el.lon
      }
    }

    val spots = mutableListOf<CoolSpot>()
    var treeCounter = 0
    val maxIndividualTrees = if (includeIndividualTrees) 100 else 30

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
      spots.add(spot)
    }
    return spots
  }

  private suspend fun fetchFromNominatimBoundedSearch(bbox: BoundingBoxCoords): List<CoolSpot> =
    coroutineScope {
      val viewbox = "${bbox.west},${bbox.north},${bbox.east},${bbox.south}"
      val queries = listOf(
        "[amenity=drinking_water]" to SpotCategory.DRINKING_WATER,
        "[amenity=fountain]" to SpotCategory.FOUNTAIN,
        "[leisure=swimming_area]" to SpotCategory.SWIMMING_BATHING,
        "[leisure=water_park]" to SpotCategory.SWIMMING_BATHING,
        "[amenity=ice_cream]" to SpotCategory.ICE_CREAM,
        "[shop=ice_cream]" to SpotCategory.ICE_CREAM,
        "[cuisine=ice_cream]" to SpotCategory.ICE_CREAM,
        "[leisure=bathing_place]" to SpotCategory.SWIMMING_BATHING,
        "[amenity=place_of_worship]" to SpotCategory.CHURCH_COOL_BUILDING,
        "[amenity=shelter]" to SpotCategory.SHADED_BENCH_SHELTER,
        "[leisure=park]" to SpotCategory.PARK,
        "[amenity=library]" to SpotCategory.LIBRARY,
        "[tourism=museum]" to SpotCategory.MUSEUM,
        "Kneipp" to SpotCategory.KNEIPP,
        "[natural=tree]" to SpotCategory.TREE_SHADE,
      )

      queries.map { (q, cat) ->
        async {
          runCatching {
            val encodedQ = URLEncoder.encode(q, "UTF-8")
            val url =
              "https://nominatim.openstreetmap.org/search?q=$encodedQ&format=json&extratags=1&viewbox=$viewbox&bounded=1&limit=25&accept-language=de"
            val responseText = executeGetWithGzip(url, connectTimeoutMs = 4000, readTimeoutMs = 5000)
            val items = json.decodeFromString<List<NominatimResult>>(responseText)
            items.mapNotNull { item ->
              val lat = item.lat.toDoubleOrNull() ?: return@mapNotNull null
              val lon = item.lon.toDoubleOrNull() ?: return@mapNotNull null
              val tags = item.extratags ?: emptyMap()
              val shortTitle = item.name?.takeIf { it.isNotBlank() }
                ?: item.display_name.substringBefore(",").takeIf { it.isNotBlank() }
                ?: cat.titleDe
              val addressPart = item.display_name
                .substringAfter(",", "")
                .trim()
                .split(",")
                .take(3)
                .joinToString(",")
                .trim()
                .takeIf { it.isNotBlank() }

              CoolSpot(
                id = item.osm_id ?: item.place_id ?: (lat * 1000000 + lon * 100000).toLong(),
                lat = lat,
                lon = lon,
                category = when {
                  tags["refill"] == "yes" || tags["drinking_water:refill"] == "yes" -> SpotCategory.REFILL_STATION
                  cat == SpotCategory.FOUNTAIN && tags["drinking_water"] == "yes" -> SpotCategory.DRINKING_WATER
                  else -> cat
                },
                name = shortTitle,
                description = tags["description:de"] ?: tags["description"] ?: cat.subtitleDe,
                openingHours = tags["opening_hours"],
                operator = tags["operator"],
                wheelchair = tags["wheelchair"],
                bottle = tags["bottle"],
                fee = tags["fee"],
                address = addressPart,
                website = tags["website"] ?: tags["contact:website"],
                tags = tags,
              )
            }
          }.getOrDefault(emptyList())
        }
      }.awaitAll().flatten()
    }

  private fun fetchFromOverpassServers(
    bbox: BoundingBoxCoords,
    includeIndividualTrees: Boolean,
  ): List<CoolSpot> {
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
      [out:json][timeout:6];
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
        node["amenity"="shower"]["indoor"="no"]($b);
        node["amenity"="ice_cream"]($b);
        way["amenity"="ice_cream"]($b);
        node["shop"="ice_cream"]($b);
        way["shop"="ice_cream"]($b);
        node["cuisine"="ice_cream"]($b);
        way["cuisine"="ice_cream"]($b);
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
      out center 350;
    """.trimIndent()

    for (endpoint in overpassEndpoints) {
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
        val body = executeGetWithGzip(tripUrl, connectTimeoutMs = 5000, readTimeoutMs = 7000)
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
        val body = executeGetWithGzip(url, connectTimeoutMs = 4000, readTimeoutMs = 6000)
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

    // Fallback: straight lines connecting origin -> dest1 -> dest2 -> ...
    var totalDist = 0
    var prevLat = originLat
    var prevLon = originLon
    val polyline = mutableListOf(originLat to originLon)
    for (spot in initialOrderedDestinations) {
      totalDist += spot.distanceMetersTo(prevLat, prevLon)
      polyline.add(spot.lat to spot.lon)
      prevLat = spot.lat
      prevLon = spot.lon
    }
    val fallbackSpeed = when (travelMode) {
      TravelMode.WALKING -> 1.3
      TravelMode.BICYCLING -> 4.0
      TravelMode.DRIVING -> 8.0
    }
    Result.success(
      RouteInfo(
        targetSpots = initialOrderedDestinations,
        originLat = originLat,
        originLon = originLon,
        travelMode = travelMode,
        distanceMeters = totalDist.coerceAtLeast(1),
        durationSeconds = (totalDist / fallbackSpeed).roundToInt().coerceAtLeast(60),
        polylinePoints = polyline,
      )
    )
  }

  fun optimizeWaypointOrder(
    originLat: Double,
    originLon: Double,
    destinations: List<CoolSpot>,
  ): List<CoolSpot> {
    if (destinations.size <= 1) return destinations
    if (destinations.size <= 8) {
      return findShortestPermutation(originLat, originLon, destinations)
    }
    val initial = nearestNeighborOrder(originLat, originLon, destinations)
    return twoOptOptimize(originLat, originLon, initial)
  }

  private fun findShortestPermutation(
    originLat: Double,
    originLon: Double,
    destinations: List<CoolSpot>,
  ): List<CoolSpot> {
    var bestOrder = destinations
    var bestDist = Double.MAX_VALUE

    fun permute(list: MutableList<CoolSpot>, start: Int) {
      if (start >= list.size) {
        var dist = 0.0
        var curLat = originLat
        var curLon = originLon
        for (spot in list) {
          dist += spot.distanceMetersTo(curLat, curLon)
          curLat = spot.lat
          curLon = spot.lon
        }
        if (dist < bestDist) {
          bestDist = dist
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
          if (candDist < bestDist - 1.0) {
            bestDist = candDist
            for (idx in candidate.indices) {
              current[idx] = candidate[idx]
            }
            improved = true
            break
          }
        }
        if (improved) break
      }
    }
    return current
  }

  suspend fun searchCityOrAddress(query: String): Result<List<NominatimResult>> =
    withContext(Dispatchers.IO) {
      val trimmed = query.trim()
      if (trimmed.isEmpty()) return@withContext Result.success(emptyList())

      // 1. Try Photon (ultra-fast autocomplete from Komoot/OSM)
      try {
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val photonUrl = "https://photon.komoot.io/api/?q=$encoded&limit=6&lang=de"
        val body = executeGetWithGzip(photonUrl, connectTimeoutMs = 3500, readTimeoutMs = 4000)
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
        val body = executeGetWithGzip(url, connectTimeoutMs = 4500, readTimeoutMs = 5000)
        val results = json.decodeFromString<List<NominatimResult>>(body)
        Result.success(results)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  private fun executeGetWithGzip(
    urlStr: String,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
  ): String {
    val url = URL(urlStr)
    val conn = (url.openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = connectTimeoutMs
      readTimeout = readTimeoutMs
      setRequestProperty("User-Agent", "CoolDown-App/1.0 (info@cooldown.app)")
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
      connectTimeout = 3500
      readTimeout = 6000
      setRequestProperty("User-Agent", "CoolDown-App/1.0 (info@cooldown.app)")
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

    val category = when {
      tags["refill"] == "yes" || tags["drinking_water:refill"] == "yes" -> SpotCategory.REFILL_STATION
      tags["amenity"] == "drinking_water" || tags["drinking_water"] == "yes" ||
        (tags["man_made"] == "water_tap" && tags["drinking_water"] != "no") ||
        (tags["amenity"] == "fountain" && tags["drinking_water"] == "yes") -> SpotCategory.DRINKING_WATER
      tags["amenity"] == "kneipp_water_facility" -> SpotCategory.KNEIPP
      tags["playground"] == "splash_pad" || tags["playground"] == "water" ||
        (tags["amenity"] == "shower" && tags["indoor"] != "yes") -> SpotCategory.WATER_PLAYGROUND
      tags["leisure"] == "swimming_area" || tags["leisure"] == "water_park" ||
        tags["leisure"] == "bathing_place" ||
        (tags["leisure"] == "swimming_pool" && tags["access"] != "private" && tags["indoor"] != "yes") ||
        tags["natural"] == "beach" || tags["sport"] == "swimming" -> SpotCategory.SWIMMING_BATHING
      tags["amenity"] == "fountain" -> SpotCategory.FOUNTAIN
      tags["amenity"] == "ice_cream" || tags["shop"] == "ice_cream" ||
        tags["cuisine"] == "ice_cream" || tags["cuisine"]?.contains("ice_cream") == true ||
        tags["ice_cream"] == "yes" -> SpotCategory.ICE_CREAM
      tags["amenity"] == "library" -> SpotCategory.LIBRARY
      tags["tourism"] == "museum" || tags["tourism"] == "gallery" -> SpotCategory.MUSEUM
      tags["amenity"] == "place_of_worship" || tags["building"] == "church" ||
        tags["building"] == "cathedral" || tags["building"] == "chapel" ||
        tags["historic"] == "castle" || tags["historic"] == "monastery" -> SpotCategory.CHURCH_COOL_BUILDING
      tags["amenity"] == "shelter" ||
        (tags["amenity"] == "bench" && (tags["covered"] == "yes" || tags["shade"] == "yes")) -> SpotCategory.SHADED_BENCH_SHELTER
      tags["leisure"] == "park" || (tags["leisure"] == "garden" && tags["access"] != "private") ||
        tags["landuse"] == "forest" || tags["landuse"] == "recreation_ground" ||
        tags["natural"] == "wood" -> SpotCategory.PARK
      tags["natural"] == "tree" || tags["natural"] == "tree_row" || tags["trees"] == "yes" -> SpotCategory.TREE_SHADE
      else -> return null
    }

    val rawName = tags["name:de"] ?: tags["name:en"] ?: tags["name"] ?: tags["brand"] ?: tags["operator"]
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
