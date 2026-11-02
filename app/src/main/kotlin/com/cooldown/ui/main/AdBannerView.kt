package com.cooldown.ui.main

import android.content.Context
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
import androidx.compose.foundation.layout.height
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
import com.cooldown.data.AppLanguage
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Offizielle Google AdMob Test-Banner Unit ID.
 * Vor dem Release im Play Store kannst du hier deine echte AdMob Banner-ID eintragen:
 * z. B. "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY"
 */
const val ADMOB_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

@Composable
fun SubtleAdBanner(
  isAdFree: Boolean,
  lang: AppLanguage,
  onOpenSupportDialog: () -> Unit,
  modifier: Modifier = Modifier,
  adUnitId: String = ADMOB_BANNER_AD_UNIT_ID,
) {
  if (isAdFree) return

  var hasAdError by remember { mutableStateOf(false) }

  if (hasAdError) {
    // Falls das Gerät (z.B. Samsung ohne Play Services oder mit Knox/AdBlocker) keine Werbung laden kann,
    // wird kein Fehler geworfen, sondern die Ansicht bleibt einfach sauber und absturzsicher.
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
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
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
            text = if (lang == AppLanguage.EN) "Advertisement" else "Anzeige",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
            .height(50.dp),
          contentAlignment = Alignment.Center,
        ) {
          AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context: Context ->
              try {
                AdView(context).apply {
                  setAdSize(AdSize.BANNER)
                  this.adUnitId = adUnitId
                  layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                  )
                  adListener = object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                      Log.w("CoolDownAd", "Ad failed to load: ${error.message}")
                    }
                  }
                  try {
                    loadAd(AdRequest.Builder().build())
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
