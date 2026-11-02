package com.findcoolplaceswithcooldown.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.findcoolplaceswithcooldown.data.AppLanguage

@Composable
fun GdprConsentBanner(
  lang: AppLanguage,
  onAcceptPersonalized: () -> Unit,
  onAcceptNonPersonalized: () -> Unit,
  onOpenPrivacyDetails: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 4.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = if (lang == AppLanguage.EN) {
          "Privacy & Advertising Notice (GDPR)"
        } else {
          "Datenschutz & Werbe-Hinweis (DSGVO)"
        },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = if (lang == AppLanguage.EN) {
          "To keep CoolDown free, we display ads via Google AdMob and load map data from OpenStreetMap. You can choose whether ads may be personalized or non-personalized."
        } else {
          "Damit CoolDown kostenlos bleibt, blenden wir dezente Anzeigen über Google AdMob ein und laden Kartendaten von OpenStreetMap. Du kannst frei wählen, ob Werbung personalisiert sein darf."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onOpenPrivacyDetails) {
          Text(
            text = if (lang == AppLanguage.EN) "Privacy Info" else "Details",
            fontSize = 12.sp,
          )
        }
        OutlinedButton(
          onClick = onAcceptNonPersonalized,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Text(
            text = if (lang == AppLanguage.EN) "Standard only" else "Unpersonalisiert",
            fontSize = 11.sp,
            maxLines = 1,
          )
        }
        Button(
          onClick = onAcceptPersonalized,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Text(
            text = if (lang == AppLanguage.EN) "Accept" else "Akzeptieren",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
fun LegalPrivacyDialog(
  lang: AppLanguage,
  personalizedAdsConsent: Boolean,
  onTogglePersonalizedAds: (Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth(0.94f)
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
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Gavel,
              contentDescription = null,
              tint = Color(0xFF0077E6),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (lang == AppLanguage.EN) {
                "Privacy Policy & Legal Info"
              } else {
                "Datenschutz, Lizenzen & Rechtliches"
              },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // DSGVO Advertising Toggle
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (lang == AppLanguage.EN) {
                  "Personalized Ads (Google AdMob)"
                } else {
                  "Personalisierte Werbung (DSGVO)"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = if (lang == AppLanguage.EN) {
                  "Can be changed at any time. When disabled, only non-personalized ads (npa=1) are requested."
                } else {
                  "Jederzeit änderbar. Bei Deaktivierung werden nur unpersonalisierte Anzeigen (npa=1) geladen."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
              checked = personalizedAdsConsent,
              onCheckedChange = onTogglePersonalizedAds,
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // Section 1: Privacy Policy (DSGVO)
        Text(
          text = if (lang == AppLanguage.EN) "1. Privacy Policy (GDPR)" else "1. Datenschutzerklärung (DSGVO)",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = if (lang == AppLanguage.EN) {
            """
            • Location Data (GPS): CoolDown uses your device location (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION) exclusively while using the app to display nearby cool places, drinking water spots, and routes on the map. Your GPS coordinates are never stored on our own servers or sold to third parties.
            • Map & Routing Services: To display map tiles, search locations, and calculate routes, your current map bounding box or route coordinates are transmitted to public OpenStreetMap servers (tile.openstreetmap.org, overpass-api.de, routing.openstreetmap.de, photon.komoot.io).
            • Advertising (Google AdMob): This app uses Google AdMob (Google Ireland Limited, Gordon House, Barrow Street, Dublin 4, Ireland) to display ads. AdMob may use the device's advertising ID (AD_ID) and approximate IP address to serve ads and prevent fraud. You can disable personalized ads above at any time.
            • In-App Donations (Google Play Billing): Voluntary donations ("Spendiere ein Eis") are processed directly and securely by Google Play Billing. We do not receive or store any personal banking or credit card details.
            """.trimIndent()
          } else {
            """
            • Standortdaten (GPS): CoolDown nutzt deinen Gerätestandort (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION) ausschließlich während der aktiven Nutzung der App, um kühle Orte, Trinkwasserbrunnen und Routen in deiner unmittelbaren Umgebung auf der Karte anzuzeigen. Deine Standortdaten werden niemals auf eigenen Servern gespeichert oder verkauft.
            • Karten-, Such- & Routingdienste: Zur Darstellung der Karte, für die Ortssuche und für die Routenberechnung wird der sichtbare Kartenausschnitt bzw. die Start-/Zielkoordinate an öffentliche Dienste des OpenStreetMap-Ökosystems übertragen (tile.openstreetmap.org, overpass-api.de, routing.openstreetmap.de, photon.komoot.io).
            • Werbung (Google AdMob): Zur Finanzierung der kostenlosen App binden wir Google AdMob (Google Ireland Limited, Gordon House, Barrow Street, Dublin 4, Irland) ein. Dabei kann die Werbe-ID des Geräts (AD_ID) sowie die IP-Adresse zur Auslieferung von Anzeigen und zur Betrugsprävention verarbeitet werden. Über den Schalter oben kannst du personalisierte Werbung jederzeit deaktivieren (Rechtsgrundlage: Art. 6 Abs. 1 lit. a/f DSGVO).
            • Freiwillige Spenden (Google Play Billing): Käufe im Bereich „Spendiere ein Eis" werden vollständig und sicher über Google Play abgerechnet. Wir erhalten oder speichern keinerlei Bank- oder Kreditkartendaten.
            """.trimIndent()
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 18.sp,
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // Section 2: OpenStreetMap & Open Source Attribution (ODbL)
        Text(
          text = if (lang == AppLanguage.EN) {
            "2. Map Data & Open-Data Licenses (ODbL)"
          } else {
            "2. Kartendaten & Urheberrechtshinweise (ODbL)"
          },
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = if (lang == AppLanguage.EN) {
            """
            • Map Data: © OpenStreetMap contributors, licensed under the Open Data Commons Open Database License (ODbL) – https://www.openstreetmap.org/copyright
            • Map Library: OSMDroid (Apache License 2.0)
            • Routing: OSRM / FOSSGIS e.V. (routing.openstreetmap.de)
            • Geocoding: Nominatim & Komoot Photon (© OpenStreetMap contributors)
            """.trimIndent()
          } else {
            """
            • Kartendaten & POIs: © OpenStreetMap-Mitwirkende (OpenStreetMap contributors), lizenziert unter der Open Data Commons Open Database License (ODbL) – https://www.openstreetmap.org/copyright
            • Karten-Bibliothek: OSMDroid (Apache License 2.0)
            • Routing-Engine: OSRM / FOSSGIS e.V. (routing.openstreetmap.de)
            • Ortssuche: Nominatim & Komoot Photon (© OpenStreetMap contributors)
            """.trimIndent()
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 18.sp,
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // Section 3: Legal Notice / Disclaimer
        Text(
          text = if (lang == AppLanguage.EN) {
            "3. Disclaimer & Legal Notice (§ 5 DDG)"
          } else {
            "3. Haftungshinweis & Anbieterkennzeichnung (§ 5 DDG)"
          },
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = if (lang == AppLanguage.EN) {
            """
            • Package ID: com.findcoolplaceswithcooldown
            • Important Notice: All drinking water, bathing, and cooling spots originate from community-maintained OpenStreetMap data. No guarantee is given for water quality, accessibility, or current opening hours. Please always observe local signs on site (e.g. "Kein Trinkwasser").
            • Developer Contact: Please refer to the developer contact address and imprint deposited directly on the Google Play Store listing page for CoolDown.
            """.trimIndent()
          } else {
            """
            • Paketname: com.findcoolplaceswithcooldown
            • Wichtiger Sicherheitshinweis: Alle eingetragenen Trinkwasserbrunnen, Badestellen und kühlen Orte stammen aus den offenen Community-Daten von OpenStreetMap. Für die tatsächliche Trinkwasserqualität, Zugänglichkeit oder aktuelle Öffnungszeiten wird keine Gewähr übernommen. Bitte beachte vor Ort stets die offiziellen Hinweisschilder (z. B. „Kein Trinkwasser").
            • Kontakt / Impressum: Siehe die im offiziellen Google Play Store-Eintrag von CoolDown hinterlegten Entwickler-Kontaktdaten.
            """.trimIndent()
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 18.sp,
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
