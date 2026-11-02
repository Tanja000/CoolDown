package com.findcoolplaceswithcooldown.ui.main

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.findcoolplaceswithcooldown.data.AppLanguage
import com.findcoolplaceswithcooldown.data.MonetizationConfig
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Erstellt einen DSGVO-konformen AdRequest.
 * Wenn `personalizedAds == false`, wird das `npa=1` Flag (Non-Personalized Ads) an Google AdMob übergeben.
 */
fun buildGdprAdRequest(personalizedAds: Boolean): AdRequest {
  val builder = AdRequest.Builder()
  if (!personalizedAds) {
    val extras = Bundle().apply {
      putString("npa", "1")
    }
    builder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
  }
  return builder.build()
}

/**
 * Verwalter für dezente, aber umsatzstarke Vollbild-Anzeigen (Interstitial Ads) an natürlichen
 * Übergängen (z. B. beim Öffnen einer Route in Google Maps oder alle 3 Routen-Aktionen,
 * jedoch maximal alle 3 Minuten), damit der Nutzer nicht gestört wird.
 */
object InterstitialAdManager {
  private var interstitialAd: InterstitialAd? = null
  private var isLoadingAd = false
  private var lastShownTimestampMs = 0L
  private var actionCounter = 0

  fun preload(context: Context, personalizedAds: Boolean) {
    if (interstitialAd != null || isLoadingAd) return
    isLoadingAd = true
    try {
      val adRequest = buildGdprAdRequest(personalizedAds)
      InterstitialAd.load(
        context.applicationContext,
        MonetizationConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
        adRequest,
        object : InterstitialAdLoadCallback() {
          override fun onAdLoaded(ad: InterstitialAd) {
            interstitialAd = ad
            isLoadingAd = false
          }

          override fun onAdFailedToLoad(error: LoadAdError) {
            interstitialAd = null
            isLoadingAd = false
            Log.w("CoolDownAd", "Interstitial failed to load: ${error.message}")
          }
        },
      )
    } catch (t: Throwable) {
      isLoadingAd = false
      Log.w("CoolDownAd", "Interstitial load exception: ${t.message}")
    }
  }

  /**
   * Zählt Nutzer-Interaktionen (z. B. Route hinzufügen) und zeigt nur dann eine Anzeige,
   * wenn der Schwellenwert erreicht UND die Mindestpause (3 Min.) abgelaufen ist.
   */
  fun onRouteAction(
    context: Context,
    personalizedAds: Boolean,
    forceIfCooldownPassed: Boolean = false,
    onContinue: () -> Unit = {},
  ) {
    actionCounter++
    val now = System.currentTimeMillis()
    val cooldownPassed = (now - lastShownTimestampMs) >= MonetizationConfig.INTERSTITIAL_MIN_INTERVAL_MS
    val thresholdReached = forceIfCooldownPassed || (actionCounter >= MonetizationConfig.INTERSTITIAL_ACTION_THRESHOLD)

    val activity = context as? Activity
    val ad = interstitialAd

    if (activity != null && ad != null && cooldownPassed && thresholdReached) {
      actionCounter = 0
      lastShownTimestampMs = now
      interstitialAd = null

      ad.fullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
          preload(context, personalizedAds)
          onContinue()
        }

        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
          preload(context, personalizedAds)
          onContinue()
        }
      }
      try {
        ad.show(activity)
      } catch (t: Throwable) {
        preload(context, personalizedAds)
        onContinue()
      }
    } else {
      if (ad == null) {
        preload(context, personalizedAds)
      }
      onContinue()
    }
  }
}

@Composable
fun SubtleAdBanner(
  isAdFree: Boolean,
  personalizedAds: Boolean,
  lang: AppLanguage,
  onOpenSupportDialog: () -> Unit,
  onOpenPrivacySettings: () -> Unit = {},
  modifier: Modifier = Modifier,
  adUnitId: String = MonetizationConfig.ADMOB_BANNER_AD_UNIT_ID,
) {
  if (isAdFree) return

  var hasAdError by remember { mutableStateOf(false) }

  if (hasAdError) {
    return
  }

  AnimatedVisibility(
    visible = !isAdFree,
    enter = fadeIn(),
    exit = fadeOut(),
  ) {
    Surface(
      modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 2.dp),
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
      tonalElevation = 1.dp,
      shadowElevation = 2.dp,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (lang == AppLanguage.EN) {
              if (personalizedAds) "Ad • Privacy" else "Ad (non-personalized) • Privacy"
            } else {
              if (personalizedAds) "Anzeige • Datenschutz" else "Anzeige (unpersonalisiert) • Info"
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .clickable { onOpenPrivacySettings() }
              .padding(horizontal = 2.dp, vertical = 1.dp),
          )
          Text(
            text = if (lang == AppLanguage.EN) "Buy Ice Cream 🍦" else "Eis spendieren 🍦",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .clickable { onOpenSupportDialog() }
              .padding(horizontal = 4.dp, vertical = 1.dp),
          )
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp, max = 65.dp),
          contentAlignment = Alignment.Center,
        ) {
          AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context: Context ->
              try {
                // Preload interstitial in background while banner initializes
                InterstitialAdManager.preload(context, personalizedAds)

                val displayMetrics = context.resources.displayMetrics
                val adWidthPixels = displayMetrics.widthPixels.toFloat()
                val density = displayMetrics.density
                val adWidth = (adWidthPixels / density).toInt().coerceAtLeast(320)
                val adaptiveSize = try {
                  AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
                } catch (_: Throwable) {
                  AdSize.BANNER
                }

                AdView(context).apply {
                  setAdSize(adaptiveSize)
                  this.adUnitId = adUnitId
                  layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                  )
                  adListener = object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                      Log.w("CoolDownAd", "Banner failed to load: ${error.message}")
                    }
                  }
                  try {
                    loadAd(buildGdprAdRequest(personalizedAds))
                  } catch (e: Throwable) {
                    Log.w("CoolDownAd", "loadAd exception: ${e.message}")
                  }
                }
              } catch (t: Throwable) {
                Log.e("CoolDownAd", "Could not initialize AdView: ${t.message}")
                hasAdError = true
                View(context)
              }
            },
          )
        }
      }
    }
  }
}
