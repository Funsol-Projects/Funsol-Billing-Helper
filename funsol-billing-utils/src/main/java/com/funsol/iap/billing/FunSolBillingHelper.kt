package com.funsol.iap.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.*
import com.funsol.iap.billing.model.ErrorType
import com.funsol.iap.billing.model.ProductPriceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


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
        private val AllProducts by lazy { mutableListOf<ProductDetails>() }
        private val purchasedProductList by lazy { mutableListOf<Purchase>() }
        private val purchasedInAppProductList by lazy { mutableListOf<PurchaseHistoryRecord>() }


        private var enableLog = false
        private var enableLogWhileRelease = false
    }

    init {
        if (billingClient == null) {
            isClientReady = false
            Log("Setup new billing client")
            purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        purchases?.let {
                            for (purchase in it) {
                                Log("purchases --> $purchase")
                                CoroutineScope(Dispatchers.IO).launch {
                                    handlePurchase(purchase)
                                }
                            }
                            billingEventListener?.onProductsPurchased(purchasedProductList)
                        }
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED -> {
                        Log("User pressed back or canceled a dialog." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.USER_CANCELED)
                    }
                    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                        Log("Network connection is down." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.SERVICE_UNAVAILABLE)

                    }
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                        Log("Billing API version is not supported for the type requested." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.BILLING_UNAVAILABLE)

                    }
                    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                        Log("Requested product is not available for purchase." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.ITEM_UNAVAILABLE)

                    }
                    BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                        Log("Invalid arguments provided to the API." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.DEVELOPER_ERROR)

                    }
                    BillingClient.BillingResponseCode.ERROR -> {
                        Log("Fatal error during the API action." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.ERROR)
                    }
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                        Log("Failure to purchase since item is already owned." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.ITEM_ALREADY_OWNED)
                    }
                    BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                        Log("Failure to consume since item is not owned." + " Response code: " + billingResult.responseCode)
                        billingEventListener?.onBillingError(ErrorType.ITEM_NOT_OWNED)
                    }
                    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> {
                        Log("Initialization error: service disconnected/timeout. Trying to reconnect...")
                        billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
                    }
                    else -> {
                        Log("Initialization error: ")
                        billingEventListener?.onBillingError(ErrorType.ERROR)
                    }
                }
            }
            billingClient = BillingClient.newBuilder(context).setListener(purchasesUpdatedListener!!).enablePendingPurchases().build()
            startConnection()
        } else {
            Log("Client already connected")
        }
    }

    private fun startConnection() {

        Log("Connect start with Google Play")
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log("Connected to Google Play")
                    isClientReady = true
                    billingClientListener?.onClientReady()
                    fetchAvailableAllSubsProducts(subKeys)
                    fetchAvailableAllInAppProducts(inAppKeys)
                    CoroutineScope(Dispatchers.IO).launch {
                        fetchActivePurchases()
                    }

                }
            }

            override fun onBillingServiceDisconnected() {
                Log("Fail to connect with Google Play")
                billingClientListener?.onClientInitError()
                isClientReady = false
            }
        })
    }


    private fun fetchAvailableAllSubsProducts(productListKeys: MutableList<String>) {
        val productList = mutableListOf<QueryProductDetailsParams.Product>()

        productListKeys.forEach {
            Log("keys List ${productListKeys.size} $it")
            productList.add(QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build())
        }
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        if (billingClient != null) {
            billingClient!!.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    productDetailsList.forEach { productDetails ->
                        AllProducts.add(productDetails)
                    }
                } else {
                    Log("Failed to retrieve SUBS Prices ${billingResult.debugMessage}")
                }
            }
        } else {
            Log("Billing client null while fetching All Products")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
        }
    }

    public fun subscribe(activity: Activity, productId: String, offerId: String = "") {
        if (billingClient != null) {
            val productInfo = getProductDetail(productId, offerId, BillingClient.ProductType.SUBS)
            if (productInfo != null) {
                val productDetailsParamsList = ArrayList<BillingFlowParams.ProductDetailsParams>()
                if (productInfo.productType == BillingClient.ProductType.SUBS && productInfo.subscriptionOfferDetails != null) {
                    val offerToken = getOfferToken(productInfo.subscriptionOfferDetails, productId, offerId)
                    if (offerToken.trim { it <= ' ' } != "") {
                        productDetailsParamsList.add(
                            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productInfo).setOfferToken(offerToken).build()
                        )
                    } else {
                        billingEventListener?.onBillingError(ErrorType.OFFER_NOT_EXIST)
                        Log("The offer id: $productId doesn't seem to exist on Play Console")
                        return
                    }
                } else {
                    productDetailsParamsList.add(
                        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productInfo).build()
                    )
                }
                val billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build()
                billingClient!!.launchBillingFlow(activity, billingFlowParams)
            } else {
                billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
                Log("Billing client can not launch billing flow because product details are missing")
            }
        } else {
            Log("Billing client null while purchases")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
        }
    }

    fun upgradeOrDowngradeSubscription(activity: Activity, updateProductId: String, updateOfferId: String, OldProductID: String, policy: Int) {

        if (billingClient != null) {
            val productInfo = getProductDetail(updateProductId, updateOfferId, BillingClient.ProductType.SUBS)
            if (productInfo != null) {
                val oldToken = getOldPurchaseToken(OldProductID)
                if (oldToken.trim().isNotEmpty()) {
                    val productDetailsParamsList = ArrayList<BillingFlowParams.ProductDetailsParams>()
                    if (productInfo.productType == BillingClient.ProductType.SUBS && productInfo.subscriptionOfferDetails != null) {
                        val offerToken = getOfferToken(productInfo.subscriptionOfferDetails, updateProductId, updateOfferId)
                        if (offerToken.trim { it <= ' ' } != "") {
                            productDetailsParamsList.add(
                                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productInfo).setOfferToken(offerToken).build()
                            )
                        } else {
                            billingEventListener?.onBillingError(ErrorType.OFFER_NOT_EXIST)
                            Log("The offer id: $updateProductId doesn't seem to exist on Play Console")
                            return
                        }
                    } else {
                        productDetailsParamsList.add(
                            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productInfo).build()
                        )
                    }
                    val billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).setSubscriptionUpdateParams(
                        BillingFlowParams.SubscriptionUpdateParams.newBuilder().setOldPurchaseToken(oldToken).setReplaceProrationMode(policy).build()
                    ).build()
                    billingClient!!.launchBillingFlow(activity, billingFlowParams)
                } else {
                    Log("old purchase token not found")
                    billingEventListener?.onBillingError(ErrorType.OLD_PURCHASE_TOKEN_NOT_FOUND)

                }
            } else {
                Log("Billing client can not launch billing flow because product details are missing while update")
                billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
            }
        } else {
            Log("Billing client null while Update subs")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
        }
    }

    private fun getOldPurchaseToken(basePlanKey: String): String {
        AllProducts.forEach { pro ->
            if (pro.productType == BillingClient.ProductType.SUBS) {
                pro.subscriptionOfferDetails?.forEach { sub ->
                    if (sub.basePlanId == basePlanKey) {
                        purchasedProductList.forEach {
                            if (it.products.first() == pro.productId) {
                                return it.purchaseToken
                            }
                        }


                    }
                }
            }
        }
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
        Log("No Offer find")
        return ""
    }

    fun setSubKeys(keysList: MutableList<String>): FunSolBillingHelper {
        subKeys.addAll(keysList)
        return this
    }

    fun isSubsPremiumUser(): Boolean {
        return purchasedProductList.isNotEmpty()
    }

    fun isSubsPremiumUserByBasePlanKey(basePlanKey: String): Boolean {
        AllProducts.forEach { pro ->
            if (pro.productType == BillingClient.ProductType.SUBS) {
                pro.subscriptionOfferDetails?.forEach { sub ->
                    if (sub.basePlanId == basePlanKey) {
                        return purchasedProductList.any { it.products.first() == pro.productId }
                    }
                }
            }
        }
        billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)

        return false
    }

    fun isSubsPremiumUserBySubIDKey(SubId: String): Boolean {
        return purchasedProductList.any { it.products.first() == SubId }
    }

    fun areSubscriptionsSupported(): Boolean {
        return if (billingClient != null) {
            val responseCode = billingClient!!.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
            responseCode.responseCode == BillingClient.BillingResponseCode.OK
        } else {
            Log("billing client null while check subscription support ")
            billingEventListener?.onBillingError(ErrorType.BILLING_UNAVAILABLE)

            false
        }
    }

    fun unsubscribe(activity: Activity, SubId: String) {
        try {
            val subscriptionUrl = "http://play.google.com/store/account/subscriptions?package=" + activity.packageName + "&sku=" + SubId
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(subscriptionUrl)
            activity.startActivity(intent)
            activity.finish()
        } catch (e: Exception) {
            Log("Handling subscription cancellation: error while trying to unsubscribe")
            e.printStackTrace()
        }
    }


    //////////////////////////////////////////////////// In-App /////////////////////////////////////////////////////////////

    fun buyInApp(activity: Activity, productId: String, isPersonalizedOffer: Boolean = false) {
        if (billingClient != null) {
            val productInfo = getProductDetail(productId, "", BillingClient.ProductType.INAPP)
            if (productInfo != null) {
                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productInfo).build()
                )
                val billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).setIsOfferPersonalized(isPersonalizedOffer).build()

                billingClient!!.launchBillingFlow(activity, billingFlowParams)
                Log("Buying IN-APP : $productId")
            } else {
                billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
                Log("Billing client can not launch billing flow because IN-APP product details are missing")
            }
        } else {
            Log("Billing client null while purchases IN-APP")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
        }
    }

    private suspend fun fetchActiveInAppPurchasesHistory() {
        val params = QueryPurchaseHistoryParams.newBuilder().setProductType(BillingClient.ProductType.INAPP)
        billingClient!!.queryPurchaseHistoryAsync(params.build()) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log("in-APP History item already buy founded list size ${purchases?.size}")
                purchases?.forEach {
                    Log("in-APP History already buy products : ${it.products.first()}")
                    purchasedInAppProductList.add(it)
                }
            } else {
                Log("no in-APP History  item already buy")
            }

        }
    }

    private fun fetchAvailableAllInAppProducts(productListKeys: MutableList<String>) {
        val productList = mutableListOf<QueryProductDetailsParams.Product>()

        productListKeys.forEach {
            Log("in-App keys List ${productListKeys.size} $it")
            productList.add(QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build())
        }
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        if (billingClient != null) {
            billingClient!!.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log("in productDetailsList ${productDetailsList.size}")
                    productDetailsList.forEach { productDetails ->
                        Log("SS in app product details ${productDetails.toString()}")
                        AllProducts.add(productDetails)
                    }
                } else {
                    Log("Failed to retrieve In-APP Prices ${billingResult.debugMessage}")
                }
            }
        } else {
            Log("Billing client null while fetching All In-App Products")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
        }
    }

    fun isInAppPremiumUser(): Boolean {
        return purchasedInAppProductList.isNotEmpty()
    }

    fun isInAppPremiumUserByInAppKey(InAppKey: String): Boolean {
        purchasedInAppProductList.forEach { pro ->
            pro.products.forEach {
                if (it == InAppKey) {
                    return true
                }
            }
        }
        return false
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
        AllProducts.forEach {
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
                    productPrice.price = subIt.pricingPhases.pricingPhaseList.first().formattedPrice
                    productPrice.duration = subIt.pricingPhases.pricingPhaseList.first().billingPeriod
                    priceList.add(productPrice)
                }

            }
        }

        return priceList
    }

    fun getProductPriceByKey(BasePlanKey: String, offerKey: String): ProductPriceInfo? {
        AllProducts.forEach {
            if (it.productType == BillingClient.ProductType.SUBS) {
                it.subscriptionOfferDetails?.forEach { subIt ->
                    if (offerKey.trim().isNotEmpty()) {
                        if (subIt.basePlanId == BasePlanKey && subIt.offerId == offerKey) {
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
                        if (subIt.basePlanId == BasePlanKey && subIt.offerId == null) {
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
        Log("Product Price not found because product is missing")
        billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
        return null
    }

    fun getProductPriceByKey(productKey: String): ProductPriceInfo? {
        AllProducts.forEach {
            if (it.productType == BillingClient.ProductType.INAPP) {
                if (it.productId == productKey) {
                    val productPrice = ProductPriceInfo()
                    productPrice.title = it.title
                    productPrice.type = it.productType
                    productPrice.subsKey = it.productId
                    productPrice.productBasePlanKey = ""
                    productPrice.productOfferKey = ""
                    productPrice.price = it.oneTimePurchaseOfferDetails?.formattedPrice.toString()
                    productPrice.duration = "lifeTime"
                    return productPrice
                }
            }
        }
        Log("IN-APP Product Price not found because product is missing")
        billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
        return null
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (billingClient != null) {
            val productType = getProductType(purchase.products.first())
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged) {

                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken)
                    billingClient!!.acknowledgePurchase(acknowledgePurchaseParams.build()) {
                        if (it.responseCode == BillingClient.BillingResponseCode.OK) {
                            if (productType.trim().isNotEmpty()) {
                                if (productType == BillingClient.ProductType.INAPP) {
                                    Log("IN-APP item buy after acknowledge ")
                                    purchasedInAppProductList.add(PurchaseHistoryRecord(purchase.originalJson, purchase.signature))
                                } else {
                                    Log("SUBS item buy after acknowledge ")
                                    purchasedProductList.add(purchase)
                                }
                            } else {
                                Log("Product type not found while handle purchases")
                            }
                            billingEventListener?.onPurchaseAcknowledged(purchase)
                        } else {
                            Log("Acknowledge error while handle purchase message:  ${it.debugMessage}    code: ${it.responseCode}")
                            billingEventListener?.onBillingError(ErrorType.ACKNOWLEDGE_ERROR)
                        }
                    }

                } else {
                    Log("item purchased already acknowledge")
                    purchasedProductList.add(purchase)

                }

                if (consumeAbleKeys.isNotEmpty()) {
                    if (consumeAbleKeys.contains(purchase.products.first())) {
                        Log("this purchase is consumable")

                        val consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                        billingClient?.consumeAsync(consumeParams) { result, str ->
                            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                                Log("Purchase consumed")
                                billingEventListener?.onPurchaseConsumed(purchase)
                            } else {
                                Log("Purchase fail to consume")
                                billingEventListener?.onBillingError(ErrorType.CONSUME_ERROR)
                            }

                        }
                    } else {
                        Log("this purchase is not consumable")
                    }
                }
            } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                Log(
                    "Handling acknowledges: purchase can not be acknowledged because the state is PENDING. " + "A purchase can be acknowledged only when the state is PURCHASED"
                )
                billingEventListener?.onBillingError(ErrorType.ACKNOWLEDGE_WARNING)
            } else {
                Log("no item purchased ${purchase.packageName}")

            }

        } else {
            Log("Billing client null while handle purchases")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
        }
    }

    suspend fun fetchActivePurchases() {
        fetchActiveSubsPurchases()
        fetchActiveInAppPurchasesHistory()
    }

    private suspend fun fetchActiveSubsPurchases() {
        if (billingClient != null) {
            billingClient!!.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { billingResult: BillingResult, purchases: List<Purchase> ->


                Log("BillingResult $billingResult")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log("SUBS item already buy founded list size ${purchases.size}")
                    val activePurchases = purchases.filter { purchase ->
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    Log(" SUBS active item already buy founded list size ${activePurchases.size}")
                    CoroutineScope(Dispatchers.IO).launch {
                        activePurchases.forEach { purchase ->
                            Log("SUBS item already buy founded item:${purchase.products.first()}")
                            handlePurchase(purchase)
                        }
                    }
                } else {
                    Log("no SUBS item already buy")
                }
            }
            billingClient!!.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            ) { billingResult: BillingResult, purchases: List<Purchase> ->


                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log("IN-APP item already buy founded list size ${purchases.size}")
                    val activePurchases = purchases.filter { purchase ->
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    Log("active IN-APP item already buy founded list size ${activePurchases.size}")
                    CoroutineScope(Dispatchers.IO).launch {
                        activePurchases.forEach { purchase ->
                            Log("IN-APP item already buy founded item:${purchase.products.first()}")
                            handlePurchase(purchase)
                        }
                    }
                } else {
                    Log("no IN-APP item already buy")
                }
            }
        } else {
            Log("Billing client null while fetching active purchases")
            billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
        }
    }

    fun getProductDetail(productKey: String, offerKey: String = "", productType: String): ProductDetails? {
        val offerKeyNew = if (offerKey.trim().isNotEmpty()) {
            offerKey
        } else {
            "null"
        }
        if (AllProducts.isNotEmpty()) {
            AllProducts.forEach {
                if (productType == BillingClient.ProductType.INAPP) {
                    if (it.productId == productKey) {
                        Log("In App product detail:  title: ${it.title}  price : ${it.oneTimePurchaseOfferDetails?.formattedPrice}")
                        return it
                    }
                } else {
                    it.subscriptionOfferDetails?.forEach { subDetails ->
                        if (subDetails.basePlanId.equals(productKey, true) && subDetails.offerId.toString().equals(offerKeyNew, true)) {
                            Log("sub product detail ${subDetails.basePlanId}==  $productKey   =====> ${subDetails.offerId}==$offerKeyNew")
                            return it
                        } else {
                            Log("sub ELSE CASE ${subDetails.basePlanId}==$productKey   =====> ${subDetails.offerId}==$offerKeyNew")
                        }
                    }
                }

            }
            billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
            return null
        } else {
            billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
            return null
        }
    }

    private fun getProductType(productKey: String): String {
        AllProducts.forEach { productDetail ->
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

    fun enableLogging(isEnableWhileRelease: Boolean = false): FunSolBillingHelper {
        enableLog = true
        enableLogWhileRelease = isEnableWhileRelease
        return this
    }

    private fun Log(debugMessage: String) {

        if (enableLog) {
            if (enableLogWhileRelease) {
                Log.d(TAG, debugMessage)
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, debugMessage)
                }
            }
        }
    }

    fun release() {
        if (billingClient != null && billingClient!!.isReady) {
            Log("BillingHelper instance release: ending connection...")
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