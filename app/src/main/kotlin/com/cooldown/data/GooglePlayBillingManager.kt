package com.cooldown.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GooglePlayBillingManager(
  private val context: Context,
  private val onPurchaseSuccess: (productId: String) -> Unit = {},
) : PurchasesUpdatedListener {

  private var billingClient: BillingClient? = null
  private var isConnected = false
  private val scope = CoroutineScope(Dispatchers.IO)

  init {
    try {
      billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()
      startConnection()
    } catch (e: Throwable) {
      Log.w("PlayBilling", "BillingClient initialization: ${e.message}")
    }
  }

  private fun startConnection() {
    billingClient?.startConnection(object : BillingClientStateListener {
      override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
          isConnected = true
          Log.i("PlayBilling", "Connected to Google Play Billing")
        }
      }

      override fun onBillingServiceDisconnected() {
        isConnected = false
      }
    })
  }

  fun launchPurchase(
    activity: Activity,
    productId: String,
    onFallbackComplete: () -> Unit,
  ) {
    val client = billingClient
    if (client == null || !isConnected) {
      // In offline / testing mode before Google Play Console publishing:
      onFallbackComplete()
      return
    }

    scope.launch {
      try {
        val productList = listOf(
          QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
          .setProductList(productList)
          .build()

        val productDetailsResult = client.queryProductDetails(params)
        val details = productDetailsResult.productDetailsList?.firstOrNull()

        if (details != null) {
          val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
              listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                  .setProductDetails(details)
                  .build()
              )
            )
            .build()

          activity.runOnUiThread {
            client.launchBillingFlow(activity, flowParams)
          }
        } else {
          // If products are not yet configured in Play Console, proceed smoothly
          activity.runOnUiThread {
            onFallbackComplete()
          }
        }
      } catch (e: Throwable) {
        Log.w("PlayBilling", "Error querying/launching billing: ${e.message}")
        activity.runOnUiThread {
          onFallbackComplete()
        }
      }
    }
  }

  override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<com.android.billingclient.api.Purchase>?) {
    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
      for (purchase in purchases) {
        // Consume in-app one-time donation so user can donate again if desired
        val consumeParams = ConsumeParams.newBuilder()
          .setPurchaseToken(purchase.purchaseToken)
          .build()
        billingClient?.consumeAsync(consumeParams) { _, _ -> }
        purchase.products.forEach { prodId ->
          onPurchaseSuccess(prodId)
        }
      }
    }
  }
}
