package com.findcoolplaceswithcooldown.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.findcoolplaceswithcooldown.R
import com.findcoolplaceswithcooldown.data.AppLanguage
import kotlinx.coroutines.delay

@Composable
fun OnboardingIntroDialog(
  lang: AppLanguage,
  isLoading: Boolean,
  spotsFoundCount: Int,
  onToggleLanguage: () -> Unit,
  onDismiss: () -> Unit,
) {
  val funnyLoadingLinesDe = remember {
    listOf(
      "🍦 Koste schon mal die Eisdielen vor…",
      "🌳 Vermesse den besten Baumschatten…",
      "⛲ Prüfe die Wassertemperatur der Brunnen…",
      "🧊 Zähle frische Eiswürfel in deiner Nähe…",
    )
  }
  val funnyLoadingLinesEn = remember {
    listOf(
      "🍦 Taste-testing local ice cream shops…",
      "🌳 Measuring the shadiest trees for you…",
      "⛲ Checking fountain water temperature…",
      "🧊 Counting fresh ice cubes nearby…",
    )
  }

  var tickerIndex by remember { mutableIntStateOf(0) }
  LaunchedEffect(isLoading) {
    while (isLoading) {
      delay(2100L)
      tickerIndex = (tickerIndex + 1) % funnyLoadingLinesDe.size
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .padding(vertical = 16.dp),
      shape = RoundedCornerShape(26.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Top Header with App Icon, Language Switcher & Close Button
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
              painter = painterResource(id = R.drawable.app_logo_snowflake),
              contentDescription = null,
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = if (lang == AppLanguage.EN) "Cool-Down Guide" else "Kurz & Kühl",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.ExtraBold,
            )
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onToggleLanguage() },
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
              shape = RoundedCornerShape(10.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Default.Language,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (lang == AppLanguage.EN) "EN | DE" else "DE | EN",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier.size(34.dp),
            ) {
              Icon(Icons.Default.Close, contentDescription = "Close")
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = if (lang == AppLanguage.EN) {
            "🥵 Brain cells melting? We got you!"
          } else {
            "🥵 Gehirnzellen am Schmelzen? Rettung naht!"
          },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF0066CC),
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3 ultra-short, entertaining feature cards
        IntroFeatureRow(
          emoji = "🍦",
          bgColor = Color(0xFFFCE4EC),
          title = if (lang == AppLanguage.EN) "Water, Ice Cream & Shade" else "Wasser, Eis & Schatten",
          subtitle = if (lang == AppLanguage.EN) {
            "Tap a filter chip at the top for gelaterias, pools or free tap water."
          } else {
            "Oben einfach antippen: Eisdielen, Trinkbrunnen, Freibäder & Parks."
          },
        )

        Spacer(modifier = Modifier.height(10.dp))

        IntroFeatureRow(
          emoji = "🗺️",
          bgColor = Color(0xFFE0F2FE),
          title = if (lang == AppLanguage.EN) "Explore Any Area" else "Jeden Kartenausschnitt laden",
          subtitle = if (lang == AppLanguage.EN) {
            "Move the map & tap 'Search this area' – or switch to full-screen view."
          } else {
            "Karte schieben & 'Hier suchen' tippen – oder Vollbild genießen."
          },
        )

        Spacer(modifier = Modifier.height(10.dp))

        IntroFeatureRow(
          emoji = "🚲",
          bgColor = Color(0xFFE8F5E9),
          title = if (lang == AppLanguage.EN) "Cool Multi-Stop Tours" else "Coole Kombi-Routen",
          subtitle = if (lang == AppLanguage.EN) {
            "Tap several pins in a row: first park, then fountain, then gelato!"
          } else {
            "Mehrere Pins nacheinander tippen: Erst Park, dann Brunnen, dann Eis!"
          },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Live humorous loading status box while spots load in the background
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          color = Color(0xFFF0F7FF),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.2.dp,
                color = Color(0xFF0077E6),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                AnimatedContent(
                  targetState = tickerIndex,
                  label = "loading_ticker",
                ) { idx ->
                  Text(
                    text = if (lang == AppLanguage.EN) funnyLoadingLinesEn[idx] else funnyLoadingLinesDe[idx],
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0D47A1),
                  )
                }
                if (spotsFoundCount > 0) {
                  Text(
                    text = if (lang == AppLanguage.EN) {
                      "✨ Already found $spotsFoundCount cool spots nearby!"
                    } else {
                      "✨ Schon $spotsFoundCount kühle Orte gefunden!"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                  )
                }
              }
            } else {
              Text(
                text = if (spotsFoundCount > 0) {
                  if (lang == AppLanguage.EN) {
                    "✅ $spotsFoundCount refreshing spots ready on your map!"
                  } else {
                    "✅ $spotsFoundCount erfrischende Orte sind auf der Karte bereit!"
                  }
                } else {
                  if (lang == AppLanguage.EN) {
                    "✅ Map is ready – let's cool down!"
                  } else {
                    "✅ Karte ist startklar – ab in den Schatten!"
                  }
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
                textAlign = TextAlign.Center,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
          onClick = onDismiss,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0077E6),
            contentColor = Color.White,
          ),
        ) {
          Text(
            text = if (lang == AppLanguage.EN) "Let's Cool Down! 🧊" else "Abkühlung starten! 🧊",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
          )
        }
      }
    }
  }
}

@Composable
private fun IntroFeatureRow(
  emoji: String,
  bgColor: Color,
  title: String,
  subtitle: String,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .background(bgColor, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Text(text = emoji, fontSize = 22.sp)
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 16.sp,
        )
      }
    }
  }
}
