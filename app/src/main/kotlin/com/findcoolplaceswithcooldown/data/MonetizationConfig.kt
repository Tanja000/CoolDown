package com.findcoolplaceswithcooldown.data

/**
 * Zentrale Konfiguration für Werbung (Google AdMob) und Spenden (Google Play Billing).
 *
 * WICHTIG FÜR DEN GOOGLE PLAY STORE UPLOAD:
 * 1. Erstelle im Google AdMob Dashboard (https://admob.google.com) deine App:
 *    - Paketname: com.findcoolplaceswithcooldown
 *    - Ersetze unten [ADMOB_BANNER_AD_UNIT_ID] und [ADMOB_INTERSTITIAL_AD_UNIT_ID] durch deine eigenen Ad-Unit-IDs
 *      (und die App-ID in der AndroidManifest.xml).
 *    - Solange du noch testest, funktionieren die voreingestellten offiziellen Google-IDs sofort!
 *
 * 2. Lege in der Google Play Console unter "Monetarisierung -> In-App-Produkte" folgende Produkt-IDs an:
 *    - donation_099          (Preis: 0,99 €)
 *    - donation_199          (Preis: 1,99 €)
 *    - ice_cream_small       (Preis: 2,00 €)
 *    - ice_cream_medium      (Preis: 3,99 €)
 *    - donation_500          (Preis: 5,00 €)
 *    - donation_1000         (Preis: 10,00 €)
 *    - donation_unit_1eur    (Preis: 1,00 € - mit aktivierter "Mehrfachkauf-Option" für beliebige Wunschbeträge)
 */
object MonetizationConfig {
  // Adaptive Banner Ad Unit ID (Google AdMob)
  const val ADMOB_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

  // Interstitial (Vollbild) Ad Unit ID – bringt 10x höheren eCPM, erscheint nur dezent mit Cooldown
  const val ADMOB_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

  // Mindestabstand zwischen zwei Vollbild-Werbeanzeigen (3 Minuten = 180 Sekunden), damit Nutzer nicht genervt werden
  const val INTERSTITIAL_MIN_INTERVAL_MS = 180_000L

  // Nach wie vielen Routen-Aktionen frühestens ein Interstitial gezeigt wird
  const val INTERSTITIAL_ACTION_THRESHOLD = 3

  // In-App-Produkt-IDs für Google Play Billing
  const val SKU_DONATION_099 = "donation_099"
  const val SKU_DONATION_199 = "donation_199"
  const val SKU_DONATION_200 = "ice_cream_small"
  const val SKU_DONATION_399 = "ice_cream_medium"
  const val SKU_DONATION_500 = "donation_500"
  const val SKU_DONATION_1000 = "donation_1000"
  const val SKU_DONATION_UNIT_1EUR = "donation_unit_1eur"
}
