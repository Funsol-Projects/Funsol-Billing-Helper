package com.funsol.iap.billing.helper

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.funsol.iap.billing.helper.BillingData.billingClient
import com.funsol.iap.billing.helper.BillingData.billingClientListener
import com.funsol.iap.billing.helper.BillingData.billingEventListener
import com.funsol.iap.billing.helper.BillingData.consumeAbleProductIds
import com.funsol.iap.billing.helper.BillingData.lastPurchasedProduct
import com.funsol.iap.billing.helper.BillingData.purchasedInAppProductList
import com.funsol.iap.billing.helper.BillingData.purchasedSubsProductList
import com.funsol.iap.billing.helper.billingPrefernces.PurchasedProduct
import com.funsol.iap.billing.model.ErrorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class BuyProducts {

    private val productDetail = ProductDetail()
    private val productPrices = ProductPrices()

    /**
     * Initiates the subscription purchase flow for a product.
     *
     * @param activity The activity context used to launch the billing flow.
     * @param basePlanId The base plan ID to subscribe to.
     * @param offerId The ID of the offer associated with the product (optional, default is null).
     */
    fun subscribe(activity: Activity, basePlanId: String, offerId: String? = null) {
        billingClient?.let { client ->
            // Try to get product information with both basePlanId and offerId
            var productInfo = productDetail.getProductDetail(basePlanId, offerId, BillingClient.ProductType.SUBS)
            var productPriceInfo = productPrices.getSubscriptionProductPriceById(basePlanId = basePlanId, offerId = offerId)
            // Check if productInfo is null when using basePlanId and offerId
            var effectiveOfferId = offerId
            if (productInfo == null && effectiveOfferId != null) {
                billingEventListener?.onBillingError(ErrorType.OFFER_NOT_EXIST)
                logFunsolBilling("The offer id: $offerId doesn't exist for basePlanId: $basePlanId on Play Console")
                // Retry with only basePlanId (set effectiveOfferId to null)
                effectiveOfferId = null

                productInfo = productDetail.getProductDetail(basePlanId, null, BillingClient.ProductType.SUBS)
                productPriceInfo = productPrices.getSubscriptionProductPriceById(basePlanId = basePlanId, offerId = null)
            }

            if (productInfo != null) {
                val productDetailsParamsList = mutableListOf<BillingFlowParams.ProductDetailsParams>()

                if (productInfo.productType == BillingClient.ProductType.SUBS && productInfo.subscriptionOfferDetails != null) {
                    val offerToken = productDetail.getOfferToken(productInfo.subscriptionOfferDetails, basePlanId, effectiveOfferId)

                    if (offerToken.isNotBlank()) {
                        productDetailsParamsList.add(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productInfo)
                                .setOfferToken(offerToken)
                                .build()
                        )
                    } else {
                        billingEventListener?.onBillingError(ErrorType.OFFER_NOT_EXIST)
                        logFunsolBilling("The offer id: $offerId doesn't seem to exist on Play Console")
                        return
                    }
                } else {
                    productDetailsParamsList.add(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productInfo)
                            .build()
                    )
                }
                // Initiate the billing flow
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()
                client.launchBillingFlow(activity, billingFlowParams)

                productPriceInfo?.let {
                    lastPurchasedProduct = PurchasedProduct(
                        productId = productPriceInfo.productId,
                        basePlanId = productPriceInfo.basePlanId,
                        offerId = productPriceInfo.offerId,
                        title = productInfo.title,
                        type = productInfo.productType,
                        price = productPriceInfo.price,
                        priceMicro = productPriceInfo.priceMicro,
                        currencyCode = productPriceInfo.currencyCode
                    )
                }

            } else {
                billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
                logFunsolBilling("Billing client cannot launch billing flow because product details for basePlanId: $basePlanId are missing")
            }
        } ?: run {
            // If billingClient is null, handle service disconnected error
            logFunsolBilling("Billing client is null while attempting purchase")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
        }
    }

    /**
     * Initiates the purchase flow for an in-app product.
     *
     * @param activity The activity context used to launch the billing flow.
     * @param productId The ID of the in-app product to purchase.
     * @param isPersonalizedOffer Flag to indicate if the offer is personalized (default is false).
     */
    fun buyInApp(activity: Activity, productId: String, isPersonalizedOffer: Boolean = false) {
        // Ensure billing client is available
        val client = billingClient ?: run {
            logFunsolBilling("Error: Billing client is null.")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
            return
        }
        // Get product details
        val productInfo = productDetail.getProductDetail(productId = productId, offerId = null, productType = BillingClient.ProductType.INAPP)
        val productPriceInfo = productPrices.getInAppProductPriceById(inAppProductId = productId)

        if (productInfo == null) {
            logFunsolBilling("Error: IN-APP product details missing for product ID: $productId")
            billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
            return
        }
        // Build the billing flow parameters and initiate the purchase
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productInfo)
                        .build()
                )
            )
            .setIsOfferPersonalized(isPersonalizedOffer)
            .build()
        // Launch the billing flow
        client.launchBillingFlow(activity, billingFlowParams)

        productPriceInfo?.let {
            lastPurchasedProduct = PurchasedProduct(
                productId = productPriceInfo.productId,
                basePlanId = productPriceInfo.basePlanId,
                offerId = productPriceInfo.offerId,
                title = productInfo.title,
                type = productInfo.productType,
                price = productPriceInfo.price,
                priceMicro = productPriceInfo.priceMicro,
                currencyCode = productPriceInfo.currencyCode
            )
        }

        logFunsolBilling("Initiating purchase for IN-APP product: $productId")
    }

    /**
     * Handles the purchase flow for a given purchase by verifying its state, acknowledging it,
     * and consuming it if necessary.
     *
     * @param purchase The purchase instance containing details about the purchase transaction.
     *
     * This function performs the following steps:
     * - Checks if the billing client is available, logging and notifying listeners of an error if it is not.
     * - Determines the product type associated with the purchase.
     * - Validates the purchase state. If it is not in a "PURCHASED" state, logs a message and, if pending,
     *   sends a warning through the billing event listener.
     * - If the purchase is not yet acknowledged, it proceeds to acknowledge it. Once acknowledged,
     *   it adds the purchase to the list of purchased subscription products and notifies the billing client listener.
     * - For consumable purchases, triggers the consumption process. If the purchase is non-consumable, logs it as such.
     */
    fun handlePurchase(purchase: Purchase, productType: String) {
        // Ensure billingClient is not null
        val billingClient = billingClient ?: run {
            logFunsolBilling("Billing client is null while handling purchases")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
            return
        }

        // Handle non-purchased states
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            logFunsolBilling("No item purchased: ${purchase.packageName}")
            if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                logFunsolBilling("Purchase is pending, cannot acknowledge until purchased")
                billingEventListener?.onBillingError(ErrorType.ACKNOWLEDGE_WARNING)
            }
            return
        }

        if (productType == BillingClient.ProductType.INAPP) {
            purchasedInAppProductList.add(purchase)
        } else if (productType == BillingClient.ProductType.SUBS) {
            purchasedSubsProductList.add(purchase)
        }

        // Handle purchase acknowledgment
        if (!purchase.isAcknowledged) {
            acknowledgePurchase(billingClient, purchase, productType)
            billingEventListener?.onBillingError(ErrorType.ACKNOWLEDGE_ERROR)
        } else {
            logFunsolBilling("Item already acknowledged")
//            billingClientListener?.onPurchasesUpdated()
        }
        // Handle consumable purchases
        if (consumeAbleProductIds.contains(purchase.products.first())) {
            consumePurchase(billingClient, purchase)
        } else {
            logFunsolBilling("This purchase is not consumable")
        }
    }


    /**
     * Acknowledges a purchase to confirm the transaction and make the item available to the user.
     *
     * @param billingClient The BillingClient instance used for handling billing operations.
     * @param purchase The purchase to acknowledge.
     *
     * This function builds acknowledgment parameters, then processes the acknowledgment
     * response, logging any errors and notifying listeners accordingly. If acknowledgment is
     * successful, the purchase is added to the respective list, and the purchase update is notified.
     */

    private fun acknowledgePurchase(billingClient: BillingClient, purchase: Purchase, productType: String) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                logFunsolBilling("Purchase acknowledged ProductType = $productType && Product = ${purchase.products.first()}")
                billingClientListener?.onPurchasesUpdated()
                billingEventListener?.onPurchaseAcknowledged(purchase.toFunsolPurchase())
            } else {
                logFunsolBilling("Failed to acknowledge purchase: ${billingResult.debugMessage} (code: ${billingResult.responseCode})")
                checkAndRetryForPurchaseAcknowledgement()
                billingEventListener?.onBillingError(ErrorType.ACKNOWLEDGE_ERROR)
            }
        }
    }

    /**
     * Consumes a consumable purchase, making it available for repurchase if needed.
     *
     * @param billingClient The BillingClient instance used for handling billing operations.
     * @param purchase The purchase to consume.
     *
     * This function builds consumption parameters and processes the result of the consumption
     * operation. If consumption is successful, the purchase consumed event is triggered; otherwise,
     * any errors encountered are logged, and the relevant billing error is notified.
     */
    private fun consumePurchase(billingClient: BillingClient, purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { result, _ ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                logFunsolBilling("Purchase consumed")
                billingEventListener?.onPurchaseConsumed(purchase.toFunsolPurchase())
            } else {
                logFunsolBilling("Failed to consume purchase: ${result.debugMessage} (code: ${result.responseCode})")
                billingEventListener?.onBillingError(ErrorType.CONSUME_ERROR)
            }
        }
    }

    fun checkAndRetryForPurchaseAcknowledgement(retryCount: Int = 1) {
        val billingClient = billingClient ?: run {
            logFunsolBilling("Billing client is null while fetching active purchases in retrying acknowledgment")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
            return
        }

        if (retryCount > 3) {  // Prevent infinite retries (max 3 attempts)
            logFunsolBilling("Max retries reached for purchase acknowledgment")
            return
        }

        val pendingQueries = AtomicInteger(2) // Track both IN-APP & SUBS
        var hasUnacknowledgedPurchases = false // Flag to check if retries are needed

        fun queryAndHandlePurchases(productType: String) {
            val params = QueryPurchasesParams.newBuilder().setProductType(productType).build()
            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val unacknowledgedPurchases = purchases.filter {
                        it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged
                    }

                    if (unacknowledgedPurchases.isNotEmpty()) {
                        hasUnacknowledgedPurchases = true
                        logFunsolBilling("${unacknowledgedPurchases.size} $productType purchases found that are not acknowledged yet")

                        unacknowledgedPurchases.forEach { purchase ->
                            logFunsolBilling("$productType unacknowledged purchase: ${purchase.products.first()}")

                            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()

                            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                    logFunsolBilling("Purchase acknowledged: ${purchase.products.first()}")
                                    billingClientListener?.onPurchasesUpdated()
                                    billingEventListener?.onPurchaseAcknowledged(purchase.toFunsolPurchase())
                                } else {
                                    logFunsolBilling("Failed to acknowledge purchase: ${billingResult.debugMessage} (code: ${billingResult.responseCode})")
                                }
                            }
                        }
                    } else {
                        logFunsolBilling("No unacknowledged purchases found for $productType")
                    }
                }

                if (pendingQueries.decrementAndGet() == 0) {
                    if (hasUnacknowledgedPurchases && retryCount < 3) {
                        logFunsolBilling("Retrying acknowledgment... attempt $retryCount")
                        CoroutineScope(IO).launch {
                            delay(3000) // Wait 3 seconds before retrying
                            checkAndRetryForPurchaseAcknowledgement(retryCount + 1)
                        }
                    } else {
//                        logFunsolBilling("No retries needed. Exiting purchase acknowledgment.")
                    }
                }
            }
        }

        queryAndHandlePurchases(BillingClient.ProductType.SUBS)
        queryAndHandlePurchases(BillingClient.ProductType.INAPP)
    }

}