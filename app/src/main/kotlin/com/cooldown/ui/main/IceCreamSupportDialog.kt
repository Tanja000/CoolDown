package com.cooldown.ui.main

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cooldown.data.AppLanguage
import com.cooldown.data.GooglePlayBillingManager

@Composable
fun IceCreamSupportDialog(
  lang: AppLanguage,
  onDismiss: () -> Unit,
  onSupportSuccess: (tier: String) -> Unit,
) {
  val context = LocalContext.current
  val activity = context as? Activity

  val billingManager = remember {
    GooglePlayBillingManager(context.applicationContext) { prodId ->
      onSupportSuccess(prodId)
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .padding(vertical = 24.dp)
        .heightIn(max = 620.dp),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Header Row with Close
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .size(46.dp)
              .background(Color(0xFFE0F2FE), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            Text(text = "🍦", fontSize = 26.sp)
          }

          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = if (lang == AppLanguage.EN) "Spendiere ein Eis 🍦" else "Spendiere ein Eis 🍦",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.ExtraBold,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = if (lang == AppLanguage.EN) {
            "Support the continuous development of CoolDown and other apps with an ice-cold donation. In the fight against melting brain cells, you are our savior! Thank you so much for your support! ❤️"
          } else {
            "Unterstütze die Weiterentwicklung von CoolDown und anderen Apps mit einer eisgekühlten Spende. Im Kampf gegen schmelzende Gehirnzellen bist du die Rettung. Vielen herzlichen Dank für deinen Support! ❤️"
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          lineHeight = 20.sp,
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Tier 1: Small Scoop (2 EUR)
        IceCreamTierItem(
          emoji = "🍦",
          title = if (lang == AppLanguage.EN) "Small Lemon Scoop" else "Kleines Bällchen Zitrone",
          price = "2,00 €",
          subtitle = if (lang == AppLanguage.EN) "Refreshing kickstart for app development" else "Erfrischender Kickstart für die Entwicklung",
          onClick = {
            if (activity != null) {
              billingManager.launchPurchase(activity, "ice_cream_small") {
                onSupportSuccess("ice_cream_small")
                Toast.makeText(
                  context,
                  if (lang == AppLanguage.EN) "Thank you so much for the ice cream! ❤️🍦" else "Vielen herzlichen Dank für das Eis! ❤️🍦",
                  Toast.LENGTH_LONG,
                ).show()
                onDismiss()
              }
            } else {
              onSupportSuccess("ice_cream_small")
              onDismiss()
            }
          },
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Tier 2: Spaghetti Sundae (3.99 EUR)
        IceCreamTierItem(
          emoji = "🍨",
          title = if (lang == AppLanguage.EN) "Spaghetti Ice Cream Sundae" else "Spaghetti Eis Becher",
          price = "3,99 €",
          subtitle = if (lang == AppLanguage.EN) "Absolute Summer Hit! Savior against heat" else "Der absolute Sommer-Hit! Rettung gegen Hitze",
          isPopular = true,
          onClick = {
            if (activity != null) {
              billingManager.launchPurchase(activity, "ice_cream_medium") {
                onSupportSuccess("ice_cream_medium")
                Toast.makeText(
                  context,
                  if (lang == AppLanguage.EN) "Huge thanks for the delicious sundae! ❤️🍨" else "Riesiges Dankeschön für den Spaghetti-Eisbecher! ❤️🍨",
                  Toast.LENGTH_LONG,
                ).show()
                onDismiss()
              }
            } else {
              onSupportSuccess("ice_cream_medium")
              onDismiss()
            }
          },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
          onClick = onDismiss,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (lang == AppLanguage.EN) "Close" else "Schließen")
        }
      }
    }
  }
}

@Composable
private fun IceCreamTierItem(
  emoji: String,
  title: String,
  price: String,
  subtitle: String,
  isPopular: Boolean = false,
  onClick: () -> Unit,
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable { onClick() }
      .border(
        width = if (isPopular) 2.dp else 1.dp,
        color = if (isPopular) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(14.dp),
      ),
    color = if (isPopular) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    shape = RoundedCornerShape(14.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = emoji, fontSize = 28.sp)
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
          )
          if (isPopular) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.primary,
            ) {
              Text(
                text = "TIPP",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
              )
            }
          }
        }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary,
      ) {
        Text(
          text = price,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
      }
    }
  }
}
