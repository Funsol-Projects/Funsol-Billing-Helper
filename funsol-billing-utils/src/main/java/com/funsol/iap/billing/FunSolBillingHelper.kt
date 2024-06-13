package com.funsol.iap.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.*
import com.funsol.iap.billing.model.ErrorType
import com.funsol.iap.billing.model.ProductPriceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FunSolBillingHelper(private val context: Context) {

    private val TAG = "FunSolBillingHelper"

    companion object {

        private var isClientReady = false
        private var billingClient: BillingClient? = null
        private var billingEventListener: BillingEventListener? = null
        private var billingClientListener: BillingClientListener? = null
        private var purchasesUpdatedListener: PurchasesUpdatedListener? = null

        private val subKeys by lazy { mutableListOf<String>() }
        private val inAppKeys by lazy { mutableListOf<String>() }
        private val consumeAbleKeys by lazy { mutableListOf<String>() }
        private val allProducts by lazy { mutableListOf<ProductDetails>() }
        private val purchasedSubsProductList by lazy { mutableListOf<Purchase>() }
        private val purchasedInAppProductList by lazy { mutableListOf<Purchase>() }

        private var enableLog = true
    }

    init {
        if (billingClient == null) {
            isClientReady = false
            logFunSolBilling("Setup new billing client")
            purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        purchases?.let {
                            for (purchase in it) {
                                logFunSolBilling("purchases --> $purchase")
                                CoroutineScope(IO).launch {
                                    handlePurchase(purchase)
                                }
                            }
                            billingEventListener?.onProductsPurchased(purchasedSubsProductList)
                        }
                    }

                    BillingClient.BillingResponseCode.USER_CANCELED -> {
                        logFunSolBilling("User pressed back or canceled a dialog." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.USER_CANCELED)
                    }

                    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                        logFunSolBilling("Network connection is down." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.SERVICE_UNAVAILABLE)

                    }

                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                        logFunSolBilling("Billing API version is not supported for the type requested." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.BILLING_UNAVAILABLE)

                    }

                    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                        logFunSolBilling("Requested product is not available for purchase." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.ITEM_UNAVAILABLE)

                    }

                    BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                        logFunSolBilling("Invalid arguments provided to the API." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.DEVELOPER_ERROR)

                    }

                    BillingClient.BillingResponseCode.ERROR -> {
                        logFunSolBilling("Fatal error during the API action." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.ERROR)
                    }

                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                        logFunSolBilling("Failure to purchase since item is already owned." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.ITEM_ALREADY_OWNED)
                    }

                    BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                        logFunSolBilling("Failure to consume since item is not owned." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.ITEM_NOT_OWNED)
                    }

                    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> {
                        logFunSolBilling("Initialization error: service disconnected/timeout. Trying to reconnect...")
                        billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
                    }

                    else -> {
                        logFunSolBilling("Initialization error: ")
                        billingEventListener?.onBillingError(ErrorType.ERROR)
                    }
                }
            }
            billingClient = BillingClient.newBuilder(context)
                .setListener(purchasesUpdatedListener!!)
                .enablePendingPurchases().build()
            startConnection()
        } else {
            billingClientListener?.onClientAllReadyConnected()
        }
    }

    private fun startConnection() {

        logFunSolBilling("Connect start with Google Play")
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    logFunSolBilling("Connected to Google Play")
                    isClientReady = true

                    CoroutineScope(Main).launch {
                        // Define CompletableDeferred for each async task
                        val subsDeferred = CompletableDeferred<Unit>()
                        val inAppDeferred = CompletableDeferred<Unit>()
                        val purchasesDeferred = CompletableDeferred<Unit>()

                        // Fetch subscriptions
                        withContext(IO) {
                            fetchAvailableAllSubsProducts(subKeys, subsDeferred)
                        }

                        // Fetch in-app products
                        withContext(IO) {
                            fetchAvailableAllInAppProducts(inAppKeys, inAppDeferred)
                        }

                        // Fetch active purchases
                        withContext(IO) {
                            fetchActivePurchases(purchasesDeferred)
                        }

                        // Await all CompletableDeferred to complete
                        awaitAll(subsDeferred, inAppDeferred, purchasesDeferred)

                        // Notify the listener on the Main thread
                        withContext(Main) {
                            logFunSolBilling("Billing client is ready")
                            billingClientListener?.onClientReady()
                        }
                    }

                } else if (billingResult.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
                    billingClientListener?.onPurchasesUpdated()
                }
            }

            override fun onBillingServiceDisconnected() {
                logFunSolBilling("Fail to connect with Google Play")
                isClientReady = false

                // callback with Main thread because billing throw it in IO thread
                CoroutineScope(Main).launch {
                    billingClientListener?.onClientInitError()
                }
            }
        })
    }

    private fun fetchAvailableAllSubsProducts(productListKeys: MutableList<String>, subsDeferred: CompletableDeferred<Unit>) {
        // Early return if billing client is null
        val client = billingClient ?: run {
            logFunSolBilling("Billing client null while fetching All Subscription Products")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
            subsDeferred.complete(Unit)
            return
        }

        // Create a list of QueryProductDetailsParams.Product from the productListKeys
        val productList = productListKeys.map {
            logFunSolBilling("Subscription key: $it")
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        // Build the QueryProductDetailsParams
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        // Query product details asynchronously
        client.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {

                productDetailsList.forEach { productDetails ->
                    logFunSolBilling("Subscription product details: $productDetails")
                    allProducts.add(productDetails)
                }
            } else {
                logFunSolBilling("Failed to retrieve SUBS prices: ${billingResult.debugMessage}")
            }

            subsDeferred.complete(Unit)

        }
    }

    fun subscribe(activity: Activity, productId: String, offerId: String = "") {
        if (billingClient != null) {
            val productInfo = getProductDetail(productId, offerId, BillingClient.ProductType.SUBS)
            if (productInfo != null) {
                val productDetailsParamsList = ArrayList<BillingFlowParams.ProductDetailsParams>()
                if (productInfo.productType == BillingClient.ProductType.SUBS && productInfo.subscriptionOfferDetails != null) {
                    val offerToken =
                        getOfferToken(productInfo.subscriptionOfferDetails, productId, offerId)
                    if (offerToken.trim { it <= ' ' } != "") {
                        productDetailsParamsList.add(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productInfo).setOfferToken(offerToken).build()
                        )
                    } else {
                        billingEventListener?.onBillingError(ErrorType.OFFER_NOT_EXIST)
                        logFunSolBilling("The offer id: $productId doesn't seem to exist on Play Console")
                        return
                    }
                } else {
                    productDetailsParamsList.add(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productInfo).build()
                    )
                }
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList).build()
                billingClient!!.launchBillingFlow(activity, billingFlowParams)
            } else {
                billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
                logFunSolBilling("Billing client can not launch billing flow because product details are missing")
            }
        } else {
            logFunSolBilling("Billing client null while purchases")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
        }
    }

    private fun upgradeOrDowngradeSubscription(activity: Activity, updateProductId: String, updateOfferId: String, oldProductID: String, policy: Int) {

        if (billingClient != null) {
            val productInfo =
                getProductDetail(updateProductId, updateOfferId, BillingClient.ProductType.SUBS)
            if (productInfo != null) {
                val oldToken = getOldPurchaseToken(oldProductID)
                if (oldToken.trim().isNotEmpty()) {
                    val productDetailsParamsList =
                        ArrayList<BillingFlowParams.ProductDetailsParams>()
                    if (productInfo.productType == BillingClient.ProductType.SUBS && productInfo.subscriptionOfferDetails != null) {
                        val offerToken = getOfferToken(
                            productInfo.subscriptionOfferDetails, updateProductId, updateOfferId
                        )
                        if (offerToken.trim { it <= ' ' } != "") {
                            productDetailsParamsList.add(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productInfo).setOfferToken(offerToken)
                                    .build()
                            )
                        } else {
                            billingEventListener?.onBillingError(ErrorType.OFFER_NOT_EXIST)
                            logFunSolBilling("The offer id: $updateProductId doesn't seem to exist on Play Console")
                            return
                        }
                    } else {
                        productDetailsParamsList.add(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productInfo).build()
                        )
                    }
                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(productDetailsParamsList)
                        .setSubscriptionUpdateParams(
                            BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                                .setOldPurchaseToken(oldToken)
                                .setSubscriptionReplacementMode(policy)
                                .build()
                        ).build()
                    billingClient!!.launchBillingFlow(activity, billingFlowParams)
                } else {
                    logFunSolBilling("old purchase token not found")
                    billingEventListener?.onBillingError(ErrorType.OLD_PURCHASE_TOKEN_NOT_FOUND)

                }
            } else {
                logFunSolBilling("Billing client can not launch billing flow because product details are missing while update")
                billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
            }
        } else {
            logFunSolBilling("Billing client null while Update subs")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
        }
    }

    private fun getOldPurchaseToken(basePlanKey: String): String {
        // Find the product that matches the subscription and base plan key
        val matchingProduct = allProducts.firstOrNull { product ->
            product.productType == BillingClient.ProductType.SUBS && product.subscriptionOfferDetails?.any { it.basePlanId == basePlanKey } == true
        }

        // If a matching product is found, find the corresponding purchase token
        matchingProduct?.let { product ->
            val matchingPurchase = purchasedSubsProductList.firstOrNull { purchase ->
                purchase.products.firstOrNull() == product.productId
            }
            return matchingPurchase?.purchaseToken ?: ""
        }

        // Return empty string if no matching product or purchase is found
        return ""
    }

    private fun getOfferToken(offerList: List<ProductDetails.SubscriptionOfferDetails>?, productId: String, offerId: String): String {
        for (product in offerList!!) {
            if (product.offerId != null && product.offerId == offerId && product.basePlanId == productId) {
                return product.offerToken
            } else if (offerId.trim { it <= ' ' } == "" && product.basePlanId == productId && product.offerId == null) {
                // case when no offer in base plan
                return product.offerToken
            }
        }
        logFunSolBilling("No Offer find")
        return ""
    }

    fun setSubKeys(keysList: MutableList<String>): FunSolBillingHelper {
        subKeys.addAll(keysList)
        return this
    }

    fun isSubsPremiumUser(): Boolean {
        return purchasedSubsProductList.isNotEmpty()
    }

    fun isSubsPremiumUserByBasePlanKey(basePlanKey: String): Boolean {
        val isPremiumUser = allProducts.any { product ->
            product.productType == BillingClient.ProductType.SUBS &&
                    product.subscriptionOfferDetails?.any { it.basePlanId == basePlanKey } == true &&
                    purchasedSubsProductList.any { it.products.firstOrNull() == product.productId }
        }

        if (!isPremiumUser) {
            billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
        }

        return isPremiumUser
    }

    fun isSubsPremiumUserBySubIDKey(subId: String): Boolean {
        return purchasedSubsProductList.any { it.products.first() == subId }
    }

    fun areSubscriptionsSupported(): Boolean {
        return if (billingClient != null) {
            val responseCode =
                billingClient!!.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
            responseCode.responseCode == BillingClient.BillingResponseCode.OK
        } else {
            logFunSolBilling("billing client null while check subscription support ")
            billingEventListener?.onBillingError(ErrorType.BILLING_UNAVAILABLE)

            false
        }
    }

    fun unsubscribe(activity: Activity, SubId: String) {
        try {
            val subscriptionUrl =
                "http://play.google.com/store/account/subscriptions?package=" + activity.packageName + "&sku=" + SubId
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(subscriptionUrl)
            activity.startActivity(intent)
            activity.finish()
        } catch (e: Exception) {
            logFunSolBilling("Handling subscription cancellation: error while trying to unsubscribe")
            e.printStackTrace()
        }
    }

    //////////////////////////////////////////////////// In-App /////////////////////////////////////////////////////////////

    fun buyInApp(activity: Activity, productId: String, isPersonalizedOffer: Boolean = false) {
        val client = billingClient ?: run {
            logFunSolBilling("Error: Billing client is null.")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
            return
        }

        val productInfo = getProductDetail(productId, "", BillingClient.ProductType.INAPP)
        if (productInfo != null) {
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productInfo)
                    .build()
            )
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .setIsOfferPersonalized(isPersonalizedOffer)
                .build()

            client.launchBillingFlow(activity, billingFlowParams)
            logFunSolBilling("Initiating purchase for IN-APP product: $productId")
        } else {
            logFunSolBilling("Error: IN-APP product details missing for product ID: $productId")
            billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
        }
    }

    private fun fetchAvailableAllInAppProducts(productListKeys: MutableList<String>, inAppDeferred: CompletableDeferred<Unit>) {
        // Early return if billing client is null
        val client = billingClient ?: run {
            logFunSolBilling("Billing client null while fetching All In-App Products")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
            inAppDeferred.complete(Unit)
            return
        }

        val productList = productListKeys.map {
            logFunSolBilling("In-App key: $it")
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList.forEach { productDetails ->
                    logFunSolBilling("In-app product details: $productDetails")
                    allProducts.add(productDetails)
                }
            } else {
                logFunSolBilling("Failed to retrieve In-APP prices: ${billingResult.debugMessage}")
            }
            inAppDeferred.complete(Unit)
        }
    }

    fun isInAppPremiumUser(): Boolean {
        return purchasedInAppProductList.isNotEmpty()
    }

    fun isInAppPremiumUserByInAppKey(inAppKey: String): Boolean {
        return purchasedInAppProductList.any { purchase ->
            purchase.products.any { it == inAppKey }
        }
    }

    fun setInAppKeys(keysList: MutableList<String>): FunSolBillingHelper {
        inAppKeys.addAll(keysList)
        return this
    }

    fun setConsumableKeys(keysList: MutableList<String>): FunSolBillingHelper {
        consumeAbleKeys.addAll(keysList)
        return this
    }

    ///////////////////////////////////////////////// Common ////////////////////////////////////////////////////////////

    fun getAllProductPrices(): MutableList<ProductPriceInfo> {
        val priceList = mutableListOf<ProductPriceInfo>()

        // Place try catch because billing internal class throw null pointer some time on ProductType
        try {
            allProducts.forEach {

                if (it.productType == BillingClient.ProductType.INAPP) {
                    val productPrice = ProductPriceInfo()
                    productPrice.title = it.title
                    productPrice.type = it.productType
                    productPrice.subsKey = it.productId
                    productPrice.productBasePlanKey = ""
                    productPrice.productOfferKey = ""
                    productPrice.price = it.oneTimePurchaseOfferDetails?.formattedPrice.toString()
                    productPrice.duration = "lifeTime"
                    priceList.add(productPrice)
                } else {
                    it.subscriptionOfferDetails?.forEach { subIt ->
                        val productPrice = ProductPriceInfo()
                        productPrice.title = it.title
                        productPrice.type = it.productType
                        productPrice.subsKey = it.productId
                        productPrice.productBasePlanKey = subIt.basePlanId
                        productPrice.productOfferKey = subIt.offerId.toString()
                        productPrice.price =
                            subIt.pricingPhases.pricingPhaseList.first().formattedPrice
                        productPrice.duration =
                            subIt.pricingPhases.pricingPhaseList.first().billingPeriod
                        priceList.add(productPrice)
                    }

                }
            }
        } catch (e: java.lang.Exception) {
            return mutableListOf()
        } catch (e: Exception) {
            return mutableListOf()
        }

        return priceList
    }

    fun getProductPriceByKey(basePlanKey: String, offerKey: String): ProductPriceInfo? {
        // Place try catch because billing internal class throw null pointer some time on ProductType
        try {
            allProducts.forEach {
                if (it.productType == BillingClient.ProductType.SUBS) {
                    it.subscriptionOfferDetails?.forEach { subIt ->
                        if (offerKey.trim().isNotEmpty()) {
                            if (subIt.basePlanId == basePlanKey && subIt.offerId == offerKey) {
                                val productPrice = ProductPriceInfo()
                                productPrice.title = it.title
                                productPrice.type = it.productType
                                productPrice.subsKey = it.productId
                                productPrice.productBasePlanKey = subIt.basePlanId
                                productPrice.productOfferKey = subIt.offerId.toString()
                                productPrice.price = subIt.pricingPhases.pricingPhaseList.first().formattedPrice
                                productPrice.duration = subIt.pricingPhases.pricingPhaseList.first().billingPeriod
                                return productPrice
                            }
                        } else {
                            if (subIt.basePlanId == basePlanKey && subIt.offerId == null) {
                                val productPrice = ProductPriceInfo()
                                productPrice.title = it.title
                                productPrice.type = it.productType
                                productPrice.subsKey = it.productId
                                productPrice.productBasePlanKey = subIt.basePlanId
                                productPrice.productOfferKey = subIt.offerId.toString()
                                productPrice.price = subIt.pricingPhases.pricingPhaseList.first().formattedPrice
                                productPrice.duration = subIt.pricingPhases.pricingPhaseList.first().billingPeriod
                                return productPrice
                            }
                        }
                    }
                }

            }
        } catch (e: java.lang.Exception) {
            ///leave blank because below code auto handle this
        } catch (e: Exception) {
            ///leave blank because below code auto handle this
        }
        logFunSolBilling("SUBS Product Price not found because product is missing")
        billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
        return null
    }

    fun getProductPriceByKey(productKey: String): ProductPriceInfo? {
        // Place try catch because billing internal class throw null pointer some time on ProductType
        try {
            allProducts.forEach {
                if (it.productType == BillingClient.ProductType.INAPP) {
                    if (it.productId == productKey) {
                        val productPrice = ProductPriceInfo()
                        productPrice.title = it.title
                        productPrice.type = it.productType
                        productPrice.subsKey = it.productId
                        productPrice.productBasePlanKey = ""
                        productPrice.productOfferKey = ""
                        productPrice.price =
                            it.oneTimePurchaseOfferDetails?.formattedPrice.toString()
                        productPrice.duration = "lifeTime"
                        return productPrice
                    }
                }

            }
        } catch (e: java.lang.Exception) {
            ///leave blank because below code auto handle this
        } catch (e: Exception) {
            ///leave blank because below code auto handle this
        }
        logFunSolBilling("IN-APP Product Price not found because product is missing")
        billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
        return null
    }

    private fun handlePurchase(purchase: Purchase) {
        // Ensure billingClient is not null
        val billingClient = billingClient ?: run {
            logFunSolBilling("Billing client is null while handling purchases")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
            return
        }

        // Get the product type of the purchase
        val productType = getProductType(purchase.products.first())

        // Handle non-purchased states
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            logFunSolBilling("No item purchased: ${purchase.packageName}")
            if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                logFunSolBilling("Purchase is pending, cannot acknowledge until purchased")
                billingEventListener?.onBillingError(ErrorType.ACKNOWLEDGE_WARNING)
            }
            return
        }

        // Handle purchase acknowledgment
        if (!purchase.isAcknowledged) {
            acknowledgePurchase(billingClient, purchase, productType)
        } else {
            logFunSolBilling("Item already acknowledged")
            purchasedSubsProductList.add(purchase)
            billingClientListener?.onPurchasesUpdated()
        }

        // Handle consumable purchases
        if (consumeAbleKeys.contains(purchase.products.first())) {
            consumePurchase(billingClient, purchase)
        } else {
            logFunSolBilling("This purchase is not consumable")
        }
    }

    // Helper function to acknowledge a purchase
    private fun acknowledgePurchase(billingClient: BillingClient, purchase: Purchase, productType: String) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) {
            if (it.responseCode == BillingClient.BillingResponseCode.OK) {
                logFunSolBilling("$productType item acknowledged")
                // Add purchase to the appropriate list
                if (productType.trim().isNotEmpty()) {
                    if (productType == BillingClient.ProductType.INAPP) {
                        purchasedInAppProductList.add(purchase)
                    } else {
                        purchasedSubsProductList.add(purchase)
                    }
                    billingClientListener?.onPurchasesUpdated()
                } else {
                    logFunSolBilling("Product type not found while handling purchase")
                }
                billingEventListener?.onPurchaseAcknowledged(purchase)
            } else {
                logFunSolBilling("Acknowledge error: ${it.debugMessage} (code: ${it.responseCode})")
                billingEventListener?.onBillingError(ErrorType.ACKNOWLEDGE_ERROR)
            }
        }
    }

    // Helper function to consume a purchase
    private fun consumePurchase(billingClient: BillingClient, purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient.consumeAsync(consumeParams) { result, _ ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                logFunSolBilling("Purchase consumed")
                billingEventListener?.onPurchaseConsumed(purchase)
            } else {
                logFunSolBilling("Failed to consume purchase: ${result.debugMessage} (code: ${result.responseCode})")
                billingEventListener?.onBillingError(ErrorType.CONSUME_ERROR)
            }
        }
    }

    fun fetchActivePurchases(purchasesDeferred: CompletableDeferred<Unit> = CompletableDeferred()) {
        fetchAndUpdateActivePurchases(purchasesDeferred)
//        fetchActiveInAppPurchasesHistory()
    }

    private fun fetchAndUpdateActivePurchases(purchasesDeferred: CompletableDeferred<Unit>) {
        val billingClient = billingClient
        if (billingClient == null) {
            logFunSolBilling("Billing client is null while fetching active purchases")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
            purchasesDeferred.complete(Unit)
            return
        }

        val scope = CoroutineScope(IO)

        fun handleBillingResult(billingResult: BillingResult, purchases: List<Purchase>, productType: String, purchasesDeferred: CompletableDeferred<Unit>) {
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val activePurchases = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                logFunSolBilling("$productType purchases found: ${activePurchases.size}")

                if (activePurchases.isEmpty()) {
                    billingClientListener?.onPurchasesUpdated()
                    purchasesDeferred.complete(Unit)
                    return
                }

                scope.launch {
                    activePurchases.forEach { purchase ->
                        logFunSolBilling("$productType purchase: ${purchase.products.first()}")
                        handlePurchase(purchase)
                    }
                    purchasesDeferred.complete(Unit)
                }
            } else {
                logFunSolBilling("No $productType purchases found")
            }
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult, purchases ->
            handleBillingResult(billingResult, purchases, "SUBS", purchasesDeferred)
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { billingResult, purchases ->
            handleBillingResult(billingResult, purchases, "IN-APP", purchasesDeferred)
        }
    }

    fun getProductDetail(productKey: String, offerKey: String = "", productType: String): ProductDetails? {

        val offerKeyNormalized = offerKey.trim().takeIf { it.isNotEmpty() } ?: "null"

        if (allProducts.isEmpty()) {
            billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
            return null
        }

        val product = allProducts.find { product ->
            when (productType) {
                BillingClient.ProductType.INAPP -> {
                    if (product.productId == productKey) {
                        logFunSolBilling("In App product detail: title: ${product.title} price: ${product.oneTimePurchaseOfferDetails?.formattedPrice}")
                        true
                    } else {
                        false
                    }
                }

                BillingClient.ProductType.SUBS -> {
                    product.subscriptionOfferDetails?.any { subDetails ->
                        val isMatchingBasePlan = subDetails.basePlanId.equals(productKey, true)
                        val isMatchingOfferId = subDetails.offerId.toString().equals(offerKeyNormalized, true)
                        if (isMatchingBasePlan && isMatchingOfferId) {
                            logFunSolBilling("Subscription product detail: basePlanId: ${subDetails.basePlanId} offerId: ${subDetails.offerId}")
                        }
                        isMatchingBasePlan && isMatchingOfferId
                    } ?: false
                }

                else -> false
            }
        }

        if (product == null) {
            billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
        }

        return product
    }

    private fun getProductType(productKey: String): String {
        allProducts.forEach { productDetail ->
            if (productDetail.productType == BillingClient.ProductType.INAPP) {
                if (productDetail.productId == productKey) {
                    return productDetail.productType
                }
            } else {
                productDetail.subscriptionOfferDetails?.forEach {
                    if (it.basePlanId == productKey) {
                        return productDetail.productType
                    }
                }
            }
        }
        return ""
    }

    fun isClientReady(): Boolean {
        return isClientReady
    }

    fun enableLogging(isEnableLog: Boolean = true): FunSolBillingHelper {
        enableLog = isEnableLog
        return this
    }

    private fun logFunSolBilling(message: String) {
        if (enableLog) {
            Log.d(TAG, message)
        }
    }

    fun release() {
        if (billingClient != null && billingClient!!.isReady) {
            logFunSolBilling("BillingHelper instance release: ending connection...")
            billingClient?.endConnection()
        }
    }

    fun setBillingEventListener(billingEventListeners: BillingEventListener?): FunSolBillingHelper {
        billingEventListener = billingEventListeners
        return this
    }

    fun setBillingClientListener(billingClientListeners: BillingClientListener?): FunSolBillingHelper {
        billingClientListener = billingClientListeners
        return this
    }
}