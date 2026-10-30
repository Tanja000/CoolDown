package com.example.cooldown.data

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
  ): Result<RouteInfo> = withContext(Dispatchers.IO) {
    val endpoints = when (travelMode) {
      TravelMode.WALKING -> listOf(
        "https://routing.openstreetmap.de/routed-foot/route/v1/driving/$originLon,$originLat;${destination.lon},${destination.lat}?overview=full&geometries=geojson",
        "https://router.project-osrm.org/route/v1/foot/$originLon,$originLat;${destination.lon},${destination.lat}?overview=full&geometries=geojson",
      )
      TravelMode.BICYCLING -> listOf(
        "https://routing.openstreetmap.de/routed-bike/route/v1/driving/$originLon,$originLat;${destination.lon},${destination.lat}?overview=full&geometries=geojson",
        "https://router.project-osrm.org/route/v1/bike/$originLon,$originLat;${destination.lon},${destination.lat}?overview=full&geometries=geojson",
      )
    }

    for (url in endpoints) {
      try {
        val body = executeGetWithGzip(url, connectTimeoutMs = 4000, readTimeoutMs = 6000)
        val parsed = json.decodeFromString<OsrmRouteResponse>(body)
        val route = parsed.routes.firstOrNull()
        if (route != null && route.geometry != null && route.geometry.coordinates.isNotEmpty()) {
          val pts = route.geometry.coordinates.mapNotNull { coord ->
            if (coord.size >= 2) {
              coord[1] to coord[0]
            } else {
              null
            }
          }
          val distMeters = route.distance.roundToInt().coerceAtLeast(1)
          val speedMetersPerSec = if (travelMode == TravelMode.WALKING) 1.35 else 4.2
          val durationSec = if (url.contains("project-osrm.org")) {
            (distMeters / speedMetersPerSec).roundToInt()
          } else {
            route.duration.roundToInt().coerceAtLeast((distMeters / speedMetersPerSec).roundToInt())
          }

          return@withContext Result.success(
            RouteInfo(
              targetSpot = destination,
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

    val straightDist = destination.distanceMetersTo(originLat, originLon)
    val speed = if (travelMode == TravelMode.WALKING) 1.3 else 4.0
    Result.success(
      RouteInfo(
        targetSpot = destination,
        originLat = originLat,
        originLon = originLon,
        travelMode = travelMode,
        distanceMeters = straightDist,
        durationSeconds = (straightDist / speed).roundToInt().coerceAtLeast(60),
        polylinePoints = listOf(
          originLat to originLon,
          destination.lat to destination.lon,
        ),
      )
    )
  }

  suspend fun searchCityOrAddress(query: String): Result<List<NominatimResult>> =
    withContext(Dispatchers.IO) {
      try {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5&accept-language=de"
        val body = executeGetWithGzip(url, connectTimeoutMs = 6000, readTimeoutMs = 6000)
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
      setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; CoolDownApp/1.5; +https://openstreetmap.org)")
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
      setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; CoolDownApp/1.5; +https://openstreetmap.org)")
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
        (tags["leisure"] == "swimming_pool" && tags["access"] != "private" && tags["indoor"] != "yes") ||
        tags["natural"] == "beach" -> SpotCategory.SWIMMING_BATHING
      tags["amenity"] == "fountain" -> SpotCategory.FOUNTAIN
      tags["amenity"] == "ice_cream" || tags["shop"] == "ice_cream" -> SpotCategory.ICE_CREAM
      tags["amenity"] == "library" -> SpotCategory.LIBRARY
      tags["tourism"] == "museum" || tags["tourism"] == "gallery" -> SpotCategory.MUSEUM
      tags["amenity"] == "place_of_worship" || tags["building"] == "church" ||
        tags["building"] == "cathedral" || tags["building"] == "chapel" -> SpotCategory.CHURCH_COOL_BUILDING
      tags["amenity"] == "shelter" ||
        (tags["amenity"] == "bench" && (tags["covered"] == "yes" || tags["shade"] == "yes")) -> SpotCategory.SHADED_BENCH_SHELTER
      tags["leisure"] == "park" || (tags["leisure"] == "garden" && tags["access"] != "private") ||
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
