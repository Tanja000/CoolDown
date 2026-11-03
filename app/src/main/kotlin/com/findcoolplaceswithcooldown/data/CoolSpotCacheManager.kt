package com.findcoolplaceswithcooldown.data

import android.content.Context
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CachedCoolDownState(
  val centerLat: Double,
  val centerLon: Double,
  val zoom: Double,
  val timestampMillis: Long,
  val spots: List<CoolSpot>,
)

class CoolSpotCacheManager(context: Context) {
  private val cacheFile = File(context.filesDir, "cooldown_last_spots_cache_v2.json")
  private val prefs = context.getSharedPreferences("cooldown_settings_prefs", Context.MODE_PRIVATE)
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  fun getSavedLanguage(): AppLanguage {
    val code = prefs.getString("selected_lang", "de") ?: "de"
    return if (code == "en") AppLanguage.EN else AppLanguage.DE
  }

  fun saveLanguage(lang: AppLanguage) {
    prefs.edit().putString("selected_lang", lang.code).apply()
  }

  fun isAdFree(): Boolean {
    return prefs.getBoolean("is_ad_free", false)
  }

  fun setAdFree(adFree: Boolean) {
    prefs.edit().putBoolean("is_ad_free", adFree).apply()
  }

  fun getPersonalizedAdsConsent(): Boolean {
    return prefs.getBoolean("personalized_ads_consent", true)
  }

  fun setPersonalizedAdsConsent(consent: Boolean) {
    prefs.edit()
      .putBoolean("personalized_ads_consent", consent)
      .putBoolean("has_seen_consent_banner", true)
      .apply()
  }

  fun hasSeenConsentBanner(): Boolean {
    return prefs.getBoolean("has_seen_consent_banner", false)
  }

  fun setHasSeenConsentBanner(seen: Boolean) {
    prefs.edit().putBoolean("has_seen_consent_banner", seen).apply()
  }

  fun hasSeenOnboarding(): Boolean {
    return prefs.getBoolean("has_seen_onboarding_v1", false)
  }

  fun setHasSeenOnboarding(seen: Boolean) {
    prefs.edit().putBoolean("has_seen_onboarding_v1", seen).apply()
  }

  fun saveCache(
    centerLat: Double,
    centerLon: Double,
    zoom: Double,
    spots: List<CoolSpot>,
  ) {
    if (spots.isEmpty()) return
    try {
      val payload = CachedCoolDownState(
        centerLat = centerLat,
        centerLon = centerLon,
        zoom = zoom,
        timestampMillis = System.currentTimeMillis(),
        spots = spots,
      )
      cacheFile.writeText(json.encodeToString(payload), Charsets.UTF_8)
    } catch (_: Exception) {
    }
  }

  fun loadCache(): CachedCoolDownState? {
    if (!cacheFile.exists()) return null
    return try {
      val content = cacheFile.readText(Charsets.UTF_8)
      json.decodeFromString<CachedCoolDownState>(content)
    } catch (_: Exception) {
      null
    }
  }

  fun isSamePosition(
    cached: CachedCoolDownState,
    currentLat: Double,
    currentLon: Double,
    thresholdMeters: Int = 550,
  ): Boolean {
    return distanceMeters(cached.centerLat, cached.centerLon, currentLat, currentLon) <= thresholdMeters
  }

  private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
      sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadius * c).roundToInt()
  }
}
