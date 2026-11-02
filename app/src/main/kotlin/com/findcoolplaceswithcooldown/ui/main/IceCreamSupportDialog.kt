package com.findcoolplaceswithcooldown.ui.main

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.findcoolplaceswithcooldown.data.AppLanguage
import com.findcoolplaceswithcooldown.data.GooglePlayBillingManager
import com.findcoolplaceswithcooldown.data.MonetizationConfig
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IceCreamSupportDialog(
  lang: AppLanguage,
  onDismiss: () -> Unit,
  onSupportSuccess: (tier: String) -> Unit,
) {
  val context = LocalContext.current
  val activity = context as? Activity

  var customAmountInput by remember { mutableStateOf("") }
  var customAmountError by remember { mutableStateOf<String?>(null) }

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
        .fillMaxWidth(0.93f)
        .padding(vertical = 20.dp)
        .heightIn(max = 680.dp),
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

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Spendiere ein Eis 🍦",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.ExtraBold,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

        // Option 1: 0,99 EUR
        IceCreamTierItem(
          emoji = "🍧",
          title = if (lang == AppLanguage.EN) "Refreshing Water Ice" else "Kleines Wassereis",
          price = "0,99 €",
          subtitle = if (lang == AppLanguage.EN) "Quick cool-down support" else "Schnelle Abkühlung für zwischendurch",
          onClick = {
            if (activity != null) {
              billingManager.launchPurchase(activity, MonetizationConfig.SKU_DONATION_099) {
                onSupportSuccess(MonetizationConfig.SKU_DONATION_099)
                Toast.makeText(
                  context,
                  if (lang == AppLanguage.EN) "Thank you so much for the 0,99 € support! ❤️🍧" else "Vielen herzlichen Dank für die 0,99 € Spende! ❤️🍧",
                  Toast.LENGTH_LONG,
                ).show()
                onDismiss()
              }
            } else {
              onSupportSuccess(MonetizationConfig.SKU_DONATION_099)
              onDismiss()
            }
          },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Option 2: 1,99 EUR
        IceCreamTierItem(
          emoji = "🍦",
          title = if (lang == AppLanguage.EN) "Small Lemon Scoop" else "Kleines Bällchen Zitrone",
          price = "1,99 €",
          subtitle = if (lang == AppLanguage.EN) "Refreshing kickstart for app development" else "Erfrischender Kickstart für die Entwicklung",
          onClick = {
            if (activity != null) {
              billingManager.launchPurchase(activity, MonetizationConfig.SKU_DONATION_199) {
                onSupportSuccess(MonetizationConfig.SKU_DONATION_199)
                Toast.makeText(
                  context,
                  if (lang == AppLanguage.EN) "Thank you so much for the ice cream! ❤️🍦" else "Vielen herzlichen Dank für das Eis (1,99 €)! ❤️🍦",
                  Toast.LENGTH_LONG,
                ).show()
                onDismiss()
              }
            } else {
              onSupportSuccess(MonetizationConfig.SKU_DONATION_199)
              onDismiss()
            }
          },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Option 3: 3,99 EUR (Spaghetti Eis Becher)
        IceCreamTierItem(
          emoji = "🍨",
          title = if (lang == AppLanguage.EN) "Spaghetti Ice Cream Sundae" else "Spaghetti Eis Becher",
          price = "3,99 €",
          subtitle = if (lang == AppLanguage.EN) "Absolute Summer Hit! Savior against heat" else "Der absolute Sommer-Hit! Rettung gegen Hitze",
          isPopular = true,
          onClick = {
            if (activity != null) {
              billingManager.launchPurchase(activity, MonetizationConfig.SKU_DONATION_399) {
                onSupportSuccess(MonetizationConfig.SKU_DONATION_399)
                Toast.makeText(
                  context,
                  if (lang == AppLanguage.EN) "Huge thanks for the delicious sundae! ❤️🍨" else "Riesiges Dankeschön für den Spaghetti-Eisbecher! ❤️🍨",
                  Toast.LENGTH_LONG,
                ).show()
                onDismiss()
              }
            } else {
              onSupportSuccess(MonetizationConfig.SKU_DONATION_399)
              onDismiss()
            }
          },
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // Option 4: Jeder andere frei eingebbare Wunschbetrag
        Text(
          text = if (lang == AppLanguage.EN) "Or enter any custom amount:" else "Oder freien Wunschbetrag eingeben:",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick-select chips for custom amounts
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf("0,99", "1,99", "5,00", "10,00").forEach { preset ->
            AssistChip(
              onClick = {
                customAmountInput = preset
                customAmountError = null
              },
              label = { Text("$preset €", fontWeight = FontWeight.SemiBold) },
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
          value = customAmountInput,
          onValueChange = { input ->
            // Allow digits, comma and dot
            if (input.length <= 8 && input.all { it.isDigit() || it == ',' || it == '.' }) {
              customAmountInput = input
              customAmountError = null
            }
          },
          modifier = Modifier.fillMaxWidth(),
          label = {
            Text(
              if (lang == AppLanguage.EN) "Custom amount in Euro (e.g. 2,50)"
              else "Wunschbetrag in Euro (z. B. 2,50)"
            )
          },
          suffix = { Text("€", fontWeight = FontWeight.Bold) },
          singleLine = true,
          isError = customAmountError != null,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          shape = RoundedCornerShape(14.dp),
        )

        customAmountError?.let { err ->
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = err,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
          onClick = {
            val normalized = customAmountInput.trim().replace(',', '.')
            val amount = normalized.toDoubleOrNull()
            if (amount == null || amount < 0.50) {
              customAmountError = if (lang == AppLanguage.EN) {
                "Please enter an amount of at least 0,50 €"
              } else {
                "Bitte gib einen Betrag ab 0,50 € ein"
              }
            } else {
              val formatted = String.format(Locale.GERMANY, "%.2f", amount)
              if (activity != null) {
                billingManager.launchCustomAmountPurchase(activity, amount) {
                  onSupportSuccess("custom_$formatted")
                  Toast.makeText(
                    context,
                    if (lang == AppLanguage.EN) {
                      "Thank you so much for your $formatted € donation! ❤️🍦"
                    } else {
                      "Vielen herzlichen Dank für deine $formatted € Spende! ❤️🍦"
                    },
                    Toast.LENGTH_LONG,
                  ).show()
                  onDismiss()
                }
              } else {
                onSupportSuccess("custom_$formatted")
                onDismiss()
              }
            }
          },
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0077E6),
            contentColor = Color.White,
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = if (lang == AppLanguage.EN) "Donate custom amount 🍦" else "Wunschbetrag spenden 🍦",
            fontWeight = FontWeight.Bold,
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
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
