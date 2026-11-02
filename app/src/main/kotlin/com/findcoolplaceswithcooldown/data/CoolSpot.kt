package com.findcoolplaceswithcooldown.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

enum class AppLanguage(val code: String, val displayName: String) {
  DE("de", "Deutsch"),
  EN("en", "English"),
}

@Serializable
enum class SpotCategory(
  val titleDe: String,
  val titleEn: String,
  val subtitleDe: String,
  val subtitleEn: String,
  val osmRuleDescription: String,
  val colorHex: Int,
  val isWaterSource: Boolean,
) {
  DRINKING_WATER(
    titleDe = "Trinkwasserbrunnen",
    titleEn = "Drinking Water",
    subtitleDe = "Öffentliches Trinkwasser",
    subtitleEn = "Public drinking water fountain",
    osmRuleDescription = "amenity=drinking_water",
    colorHex = 0xFF0077E6.toInt(),
    isWaterSource = true,
  ),
  REFILL_STATION(
    titleDe = "Refill-Station",
    titleEn = "Refill Station",
    subtitleDe = "Gratis Leitungswasser nachfüllen",
    subtitleEn = "Free tap water bottle refill",
    osmRuleDescription = "refill=yes",
    colorHex = 0xFF00ACC1.toInt(),
    isWaterSource = true,
  ),
  SWIMMING_BATHING(
    titleDe = "Freibad & Badestelle",
    titleEn = "Swimming & Beach",
    subtitleDe = "Abkühlung im Wasser (Freibad, Badesee, Flussbad)",
    subtitleEn = "Outdoor pool, swimming lake, beach",
    osmRuleDescription = "leisure=swimming_area / water_park",
    colorHex = 0xFF0288D1.toInt(),
    isWaterSource = false,
  ),
  WATER_PLAYGROUND(
    titleDe = "Wasserspielplatz & Nebeldusche",
    titleEn = "Water Playground & Mist",
    subtitleDe = "Planschbecken, Sprühnebel & Wasserfontänen",
    subtitleEn = "Splash pad, cooling mist & fountain",
    osmRuleDescription = "playground=splash_pad / water",
    colorHex = 0xFF26A69A.toInt(),
    isWaterSource = false,
  ),
  FOUNTAIN(
    titleDe = "Springbrunnen & Wasserspiel",
    titleEn = "Fountain & Water Feature",
    subtitleDe = "Kühlende Verdunstung & erfrischende Gischt",
    subtitleEn = "Cooling water spray and splash",
    osmRuleDescription = "amenity=fountain",
    colorHex = 0xFF039BE5.toInt(),
    isWaterSource = false,
  ),
  KNEIPP(
    titleDe = "Kneipp-Anlage",
    titleEn = "Kneipp Water Basin",
    subtitleDe = "Kneippbecken & Wassertreten zur Abkühlung",
    subtitleEn = "Cooling wading & hydrotherapy basin",
    osmRuleDescription = "amenity=kneipp_water_facility",
    colorHex = 0xFF00897B.toInt(),
    isWaterSource = false,
  ),
  PARK(
    titleDe = "Park & Grünanlage",
    titleEn = "Park & Green Space",
    subtitleDe = "Kühler Ort im Grünen & Wald",
    subtitleEn = "Shady green park and trees",
    osmRuleDescription = "leisure=park",
    colorHex = 0xFF2E7D32.toInt(),
    isWaterSource = false,
  ),
  ICE_CREAM(
    titleDe = "Eisdiele & Eiscafé",
    titleEn = "Ice Cream Shop",
    subtitleDe = "Kalte Erfrischung & Eis an heißen Tagen",
    subtitleEn = "Cold treat & ice cream on hot days",
    osmRuleDescription = "amenity=ice_cream",
    colorHex = 0xFFD81B60.toInt(),
    isWaterSource = false,
  ),
  CHURCH_COOL_BUILDING(
    titleDe = "Kühle Kirche & Bauwerk",
    titleEn = "Cool Historic Church",
    subtitleDe = "Dicke Steinmauern – im Sommer natürlich kühl",
    subtitleEn = "Thick stone walls – naturally cold indoors",
    osmRuleDescription = "amenity=place_of_worship / building=church",
    colorHex = 0xFF6D4C41.toInt(),
    isWaterSource = false,
  ),
  LIBRARY(
    titleDe = "Bibliothek",
    titleEn = "Library (Air-Conditioned)",
    subtitleDe = "Klimatisierter / kühler Innenraum",
    subtitleEn = "Quiet & air-conditioned cool space",
    osmRuleDescription = "amenity=library",
    colorHex = 0xFF3949AB.toInt(),
    isWaterSource = false,
  ),
  MUSEUM(
    titleDe = "Museum",
    titleEn = "Museum (Cool Indoors)",
    subtitleDe = "Oft stark gekühlter Innenraum",
    subtitleEn = "Well air-conditioned cultural building",
    osmRuleDescription = "tourism=museum",
    colorHex = 0xFF8E24AA.toInt(),
    isWaterSource = false,
  ),
  SHADED_BENCH_SHELTER(
    titleDe = "Schattenbank & Pavillon",
    titleEn = "Shaded Bench & Pavilion",
    subtitleDe = "Überdachter Rastplatz oder Bank im Schatten",
    subtitleEn = "Covered shelter or bench in shade",
    osmRuleDescription = "amenity=shelter / bench (shaded)",
    colorHex = 0xFF5D4037.toInt(),
    isWaterSource = false,
  ),
  TREE_SHADE(
    titleDe = "Baum & Schattige Allee",
    titleEn = "Tree & Shaded Alley",
    subtitleDe = "Natürlicher Schattenplatz unter Bäumen",
    subtitleEn = "Natural cool shade under trees",
    osmRuleDescription = "natural=tree / natural=tree_row",
    colorHex = 0xFF558B2F.toInt(),
    isWaterSource = false,
  );

  fun title(lang: AppLanguage): String = if (lang == AppLanguage.EN) titleEn else titleDe
  fun subtitle(lang: AppLanguage): String = if (lang == AppLanguage.EN) subtitleEn else subtitleDe
}

@Serializable
data class CoolSpot(
  val id: Long,
  val lat: Double,
  val lon: Double,
  val category: SpotCategory,
  val name: String,
  val description: String? = null,
  val openingHours: String? = null,
  val operator: String? = null,
  val wheelchair: String? = null,
  val bottle: String? = null,
  val fee: String? = null,
  val address: String? = null,
  val website: String? = null,
  val tags: Map<String, String> = emptyMap(),
) {
  fun distanceMetersTo(otherLat: Double, otherLon: Double): Int {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(otherLat - lat)
    val dLon = Math.toRadians(otherLon - lon)
    val a =
      sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat)) * cos(Math.toRadians(otherLat)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadius * c).roundToInt()
  }

  fun formattedDistanceTo(otherLat: Double, otherLon: Double): String {
    val meters = distanceMetersTo(otherLat, otherLon)
    return if (meters < 1000) {
      "$meters m"
    } else {
      val km = (meters / 100.0).roundToInt() / 10.0
      "$km km"
    }
  }
}

@Serializable
data class BoundingBoxCoords(
  val south: Double,
  val west: Double,
  val north: Double,
  val east: Double,
)

enum class TravelMode(
  val titleDe: String,
  val titleEn: String,
  val googleMapsMode: String,
) {
  WALKING("Zu Fuß", "Walk", "walking"),
  BICYCLING("Fahrrad", "Bike", "bicycling"),
  DRIVING("Auto", "Car", "driving");

  fun title(lang: AppLanguage): String = if (lang == AppLanguage.EN) titleEn else titleDe
}

data class RouteInfo(
  val targetSpots: List<CoolSpot>,
  val originLat: Double,
  val originLon: Double,
  val travelMode: TravelMode,
  val distanceMeters: Int,
  val durationSeconds: Int,
  val polylinePoints: List<Pair<Double, Double>>,
) {
  constructor(
    targetSpot: CoolSpot,
    originLat: Double,
    originLon: Double,
    travelMode: TravelMode,
    distanceMeters: Int,
    durationSeconds: Int,
    polylinePoints: List<Pair<Double, Double>>,
  ) : this(
    targetSpots = listOf(targetSpot),
    originLat = originLat,
    originLon = originLon,
    travelMode = travelMode,
    distanceMeters = distanceMeters,
    durationSeconds = durationSeconds,
    polylinePoints = polylinePoints,
  )

  val targetSpot: CoolSpot
    get() = targetSpots.lastOrNull() ?: CoolSpot(
      id = 0L,
      lat = originLat,
      lon = originLon,
      category = SpotCategory.DRINKING_WATER,
      name = "Ziel",
    )

  val stopCount: Int
    get() = targetSpots.size

  val isMultiStop: Boolean
    get() = targetSpots.size > 1

  fun containsSpot(spotId: Long): Boolean = targetSpots.any { it.id == spotId }

  val formattedDistance: String
    get() = if (distanceMeters < 1000) {
      "$distanceMeters m"
    } else {
      val km = (distanceMeters / 100.0).roundToInt() / 10.0
      "$km km"
    }

  fun formattedDuration(lang: AppLanguage = AppLanguage.DE): String {
    val mins = (durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
    val minUnit = if (lang == AppLanguage.EN) "min" else "Min."
    val hrUnit = if (lang == AppLanguage.EN) "hr" else "Std."
    return if (mins < 60) {
      "$mins $minUnit"
    } else {
      val h = mins / 60
      val rem = mins % 60
      "$h $hrUnit $rem $minUnit"
    }
  }
}
