package com.funsol.iap.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.*
import com.funsol.iap.billing.helper.BillingData.allProducts
import com.funsol.iap.billing.helper.BillingData.billingClient
import com.funsol.iap.billing.helper.BillingData.billingClientListener
import com.funsol.iap.billing.helper.BillingData.billingEventListener
import com.funsol.iap.billing.helper.BillingData.consumeAbleProductIds
import com.funsol.iap.billing.helper.BillingData.enableLog
import com.funsol.iap.billing.helper.BillingData.inAppProductIds
import com.funsol.iap.billing.helper.BillingData.isClientReady
import com.funsol.iap.billing.helper.BillingData.lastPurchasedProduct
import com.funsol.iap.billing.helper.BillingData.purchasedInAppProductList
import com.funsol.iap.billing.helper.BillingData.purchasedSubsProductList
import com.funsol.iap.billing.helper.BillingData.purchasesUpdatedListener
import com.funsol.iap.billing.helper.BillingData.subProductIds
import com.funsol.iap.billing.helper.BuyProducts
import com.funsol.iap.billing.helper.ProductDetail
import com.funsol.iap.billing.helper.ProductPrices
import com.funsol.iap.billing.helper.billingPrefernces.BillingSharedPrefsManager
import com.funsol.iap.billing.helper.billingPrefernces.PurchasedHistoryUtils
import com.funsol.iap.billing.helper.billingPrefernces.PurchasedProduct
import com.funsol.iap.billing.helper.logFunsolBilling
import com.funsol.iap.billing.helper.toFunsolPurchases
import com.funsol.iap.billing.listeners.BillingClientListener
import com.funsol.iap.billing.listeners.BillingEventListener
import com.funsol.iap.billing.model.ErrorType
import com.funsol.iap.billing.model.ProductPriceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class FunSolBillingHelper(private val context: Context) {
	
	private val productPrices = ProductPrices()
	private val productDetail = ProductDetail()
	private val buyProducts = BuyProducts()
	private val purchasedHistoryUtils = PurchasedHistoryUtils(context)
	private val billingSharedPrefsManager = BillingSharedPrefsManager(context)
	
	private fun startConnection() {
		logFunsolBilling("Connect start with Google Play")
		billingClient?.startConnection(object : BillingClientStateListener {
			override fun onBillingSetupFinished(billingResult: BillingResult) {
				if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
					logFunsolBilling("Connected to Google Play")
					isClientReady = true
					
					CoroutineScope(Main).launch {
						// Define CompletableDeferred for each async task
						val subsDeferred = CompletableDeferred<Unit>()
						val inAppDeferred = CompletableDeferred<Unit>()
						// Fetch subscriptions and in-app products concurrently
						withContext(IO) {
							if (subProductIds.isNotEmpty()) {
								fetchAvailableAllSubsProducts(subProductIds, subsDeferred)
							} else {
								subsDeferred.complete(Unit)
							}
							
							if (inAppProductIds.isNotEmpty()) {
								fetchAvailableAllInAppProducts(inAppProductIds, inAppDeferred)
							} else {
								inAppDeferred.complete(Unit)
							}
						}
						// Await completion of both subsDeferred and inAppDeferred
						awaitAll(subsDeferred, inAppDeferred)
						// Define a CompletableDeferred for the third task
						val purchasesDeferred = CompletableDeferred<Unit>()
						// Only proceed to fetch active purchases after the previous tasks are done
						withContext(IO) {
							fetchAndUpdateActivePurchases(purchasesDeferred)
						}
						// Await the third task to complete
						purchasesDeferred.await()
						// Notify the listener on the Main thread
						logFunsolBilling("Billing client is ready")
						updatePremiumStatus(context = context)
						billingClientListener?.onClientReady()
					}
					
				} else if (billingResult.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
					billingClientListener?.onPurchasesUpdated()
				}
			}
			
			override fun onBillingServiceDisconnected() {
				logFunsolBilling("Fail to connect with Google Play")
				isClientReady = false
				// callback with Main thread because billing throw it in IO thread
				CoroutineScope(Main).launch {
					billingClientListener?.onClientInitError()
				}
			}
		})
	}
	
	private fun fetchAvailableAllSubsProducts(productIds: MutableList<String>, subsDeferred: CompletableDeferred<Unit>) {
		// Early return if billing client is null
		val client = billingClient ?: run {
			logFunsolBilling("Billing client null while fetching All Subscription Products")
			billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
			subsDeferred.complete(Unit)
			return
		}
		// Create a list of QueryProductDetailsParams.Product from the productIds
		val productList = productIds.map {
			logFunsolBilling("Subscription ProductId: $it")
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
					if (!allProducts.contains(productDetails)) {
						logFunsolBilling("Subscription product details: $productDetails")
						allProducts.add(productDetails)
					}
				}
			} else {
				logFunsolBilling("Failed to retrieve SUBS prices: ${billingResult.debugMessage}")
			}
			
			subsDeferred.complete(Unit)
		}
	}
	
	private fun fetchAvailableAllInAppProducts(productIds: MutableList<String>, inAppDeferred: CompletableDeferred<Unit>) {
		// Early return if billing client is null
		val client = billingClient ?: run {
			logFunsolBilling("Billing client null while fetching All In-App Products")
			billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
			inAppDeferred.complete(Unit)
			return
		}
		val productList = productIds.map {
			logFunsolBilling("In-App Product Id: $it")
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
					if (!allProducts.contains(productDetails)) {
						logFunsolBilling("In-app product details: $productDetails")
						allProducts.add(productDetails)
					}
				}
			} else {
				logFunsolBilling("Failed to retrieve In-APP prices: ${billingResult.debugMessage}")
			}
			inAppDeferred.complete(Unit)
		}
	}
	
	private fun fetchAndUpdateActivePurchases(purchasesDeferred: CompletableDeferred<Unit>) {
		val billingClient = billingClient ?: run {
			logFunsolBilling("Billing client is null while fetching active purchases")
			billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
			purchasesDeferred.complete(Unit)
			return
		}
		// Atomic counter to track the completion of both purchase queries
		val pendingQueries = AtomicInteger(2)
		
		// Helper function to query purchases and handle results
		fun queryAndHandlePurchases(productType: String) {
			val params = QueryPurchasesParams.newBuilder().setProductType(productType).build()
			billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
				if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
					val activePurchases = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
					logFunsolBilling("$productType purchases found: ${activePurchases.size}")
					
					activePurchases.forEach { purchase ->
						logFunsolBilling("$productType purchase: ${purchase.products.first()}")
						buyProducts.handlePurchase(purchase)
					}
				} else {
					logFunsolBilling("No $productType purchases found")
				}
				// Complete the deferred once both queries are done
				if (pendingQueries.decrementAndGet() == 0) {
					purchasesDeferred.complete(Unit)
				}
			}
		}
		// Start both queries
		queryAndHandlePurchases(BillingClient.ProductType.SUBS)
		queryAndHandlePurchases(BillingClient.ProductType.INAPP)
	}
	
	fun setSubProductIds(productIds: MutableList<String>): FunSolBillingHelper {
		subProductIds.addAll(productIds)
		return this
	}
	
	fun initialize() {
		if (billingClient == null) {
			isClientReady = false
			logFunsolBilling("Setup new billing client")
			purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
				when (billingResult.responseCode) {
					BillingClient.BillingResponseCode.OK                                                                      -> {
						purchases?.let {
							for (purchase in it) {
								CoroutineScope(IO).launch {
									lastPurchasedProduct?.let { originalProduct ->
										val updatedProduct = originalProduct.copy(orderId = purchase.orderId)
										purchasedHistoryUtils.recordPurchase(purchase = updatedProduct)
									}
									buyProducts.handlePurchase(purchase = purchase)
								}
							}
							billingEventListener?.onProductsPurchased(purchasedSubsProductList.toFunsolPurchases())
						}
					}
					
					BillingClient.BillingResponseCode.USER_CANCELED                                                           -> {
						logFunsolBilling("User pressed back or canceled a dialog." + " Response code: " + billingResult.responseCode)
						billingEventListener?.onBillingError(ErrorType.USER_CANCELED)
					}
					
					BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE                                                     -> {
						logFunsolBilling("Network connection is down." + " Response code: " + billingResult.responseCode)
						billingEventListener?.onBillingError(ErrorType.SERVICE_UNAVAILABLE)
						
					}
					
					BillingClient.BillingResponseCode.BILLING_UNAVAILABLE                                                     -> {
						logFunsolBilling("Billing API version is not supported for the type requested." + " Response code: " + billingResult.responseCode)
						billingEventListener?.onBillingError(ErrorType.BILLING_UNAVAILABLE)
						
					}
					
					BillingClient.BillingResponseCode.ITEM_UNAVAILABLE                                                        -> {
						logFunsolBilling("Requested product is not available for purchase." + " Response code: " + billingResult.responseCode)
						billingEventListener?.onBillingError(ErrorType.ITEM_UNAVAILABLE)
						
					}
					
					BillingClient.BillingResponseCode.DEVELOPER_ERROR                                                         -> {
						logFunsolBilling("Invalid arguments provided to the API." + " Response code: " + billingResult.responseCode)
						billingEventListener?.onBillingError(ErrorType.DEVELOPER_ERROR)
						
					}
					
					BillingClient.BillingResponseCode.ERROR                                                                   -> {
						logFunsolBilling("Fatal error during the API action." + " Response code: " + billingResult.responseCode)
						billingEventListener?.onBillingError(ErrorType.ERROR)
					}
					
					BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED                                                      -> {
						logFunsolBilling("Failure to purchase since item is already owned." + " Response code: " + billingResult.responseCode)
						billingEventListener?.onBillingError(ErrorType.ITEM_ALREADY_OWNED)
					}
					
					BillingClient.BillingResponseCode.ITEM_NOT_OWNED                                                          -> {
						logFunsolBilling("Failure to consume since item is not owned." + " Response code: " + billingResult.responseCode)
						billingEventListener?.onBillingError(ErrorType.ITEM_NOT_OWNED)
					}
					
					BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> {
						logFunsolBilling("Initialization error: service disconnected/timeout. Trying to reconnect...")
						billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
					}
					
					else                                                                                                      -> {
						logFunsolBilling("Initialization error: ")
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
//            logFunsolBilling("Client already connected")
		}
	}
	
	fun subscribe(activity: Activity, basePlanId: String, offerId: String? = null) {
		buyProducts.subscribe(activity = activity, basePlanId = basePlanId, offerId = offerId)
	}
	
	private fun upgradeOrDowngradeSubscription(
		activity: Activity,
		updateProductId: String,
		updateOfferId: String,
		oldProductID: String,
		policy: Int
	) {
		if (billingClient != null) {
			val productInfo = productDetail.getProductDetail(updateProductId, updateOfferId, BillingClient.ProductType.SUBS)
			if (productInfo != null) {
				val oldToken = getOldPurchaseToken(oldProductID)
				if (oldToken.trim().isNotEmpty()) {
					val productDetailsParamsList =
						ArrayList<BillingFlowParams.ProductDetailsParams>()
					if (productInfo.productType == BillingClient.ProductType.SUBS && productInfo.subscriptionOfferDetails != null) {
						val offerToken = productDetail.getOfferToken(productInfo.subscriptionOfferDetails, updateProductId, updateOfferId)
						if (offerToken.trim { it <= ' ' } != "") {
							productDetailsParamsList.add(
								BillingFlowParams.ProductDetailsParams.newBuilder()
									.setProductDetails(productInfo).setOfferToken(offerToken)
									.build()
							)
						} else {
							billingEventListener?.onBillingError(ErrorType.OFFER_NOT_EXIST)
							logFunsolBilling("The offer id: $updateProductId doesn't seem to exist on Play Console")
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
					logFunsolBilling("old purchase token not found")
					billingEventListener?.onBillingError(ErrorType.OLD_PURCHASE_TOKEN_NOT_FOUND)
					
				}
			} else {
				logFunsolBilling("Billing client can not launch billing flow because product details are missing while update")
				billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
			}
		} else {
			logFunsolBilling("Billing client null while Update subs")
			billingEventListener?.onBillingError(ErrorType.SERVICE_DISCONNECTED)
		}
	}
	
	private fun getOldPurchaseToken(basePlanId: String): String {
		// Find the product that matches the subscription and base plan Id
		val matchingProduct = allProducts.firstOrNull { product ->
			product.productType == BillingClient.ProductType.SUBS && product.subscriptionOfferDetails?.any { it.basePlanId == basePlanId } == true
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
	
	fun isSubsPremiumUser(): Boolean {
		return purchasedSubsProductList.isNotEmpty()
	}
	
	fun isSubsPremiumUserByBasePlanId(basePlanId: String): Boolean {
		val isPremiumUser = allProducts.any { product ->
			product.productType == BillingClient.ProductType.SUBS &&
					product.subscriptionOfferDetails?.any { it.basePlanId == basePlanId } == true &&
					purchasedSubsProductList.any { it.products.firstOrNull() == product.productId }
		}
		
		if (!isPremiumUser) {
			billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
		}
		
		return isPremiumUser
	}
	
	fun isSubsPremiumUserBySubProductID(subId: String): Boolean {
		return purchasedSubsProductList.any { it.products.first() == subId }
	}
	
	fun areSubscriptionsSupported(): Boolean {
		return if (billingClient != null) {
			val responseCode =
				billingClient!!.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
			responseCode.responseCode == BillingClient.BillingResponseCode.OK
		} else {
			logFunsolBilling("billing client null while check subscription support ")
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
			logFunsolBilling("Handling subscription cancellation: error while trying to unsubscribe")
			e.printStackTrace()
		}
	}
	
	//////////////////////////////////////////////////// In-App /////////////////////////////////////////////////////////////
	fun buyInApp(activity: Activity, productId: String, isPersonalizedOffer: Boolean = false) {
		buyProducts.buyInApp(activity = activity, productId = productId, isPersonalizedOffer = isPersonalizedOffer)
	}
	
	fun isInAppPremiumUser(): Boolean {
		return purchasedInAppProductList.isNotEmpty()
	}
	
	private fun updatePremiumStatus(context: Context) {
		val isPremium = FunSolBillingHelper(context).isInAppPremiumUser() || FunSolBillingHelper(context).isSubsPremiumUser()
		billingSharedPrefsManager.setPremiumStatus(isPremium = isPremium)
//		return isPremium
	}
	
	val isPremiumUser get() = billingSharedPrefsManager.isUserPremium()
	
	fun isInAppPremiumUserByProductId(productId: String): Boolean {
		return purchasedInAppProductList.any { purchase ->
			purchase.products.any { it == productId }
		}
	}
	
	fun setInAppProductIds(productIds: MutableList<String>): FunSolBillingHelper {
		inAppProductIds.addAll(productIds)
		return this
	}
	
	fun setConsumableProductIds(productIds: MutableList<String>): FunSolBillingHelper {
		consumeAbleProductIds.addAll(productIds)
		return this
	}
	
	fun getAllProductPrices(): MutableList<ProductPriceInfo> {
		return productPrices.getAllProductPrices()
	}
	
	fun getSubscriptionProductPriceById(basePlanId: String, offerId: String? = null): ProductPriceInfo? {
		return productPrices.getSubscriptionProductPriceById(basePlanId = basePlanId, offerId = offerId)
	}
	
	fun getInAppProductPriceById(inAppProductId: String): ProductPriceInfo? {
		return productPrices.getInAppProductPriceById(inAppProductId = inAppProductId)
	}
	
	fun isBillingClientReady(): Boolean {
		return isClientReady
	}
	
	fun enableLogging(isEnableLog: Boolean = true): FunSolBillingHelper {
		enableLog = isEnableLog
		return this
	}
	
	fun release() {
		billingClient?.takeIf { it.isReady }?.apply {
			logFunsolBilling("BillingHelper instance release: ending connection...")
			endConnection()
		}
		consumeAbleProductIds.clear()
		purchasedSubsProductList.clear()
		purchasedInAppProductList.clear()
		allProducts.clear()
		billingClient = null
	}
	
	fun setBillingEventListener(billingEventListeners: BillingEventListener?): FunSolBillingHelper {
		billingEventListener = billingEventListeners
		return this
	}
	
	fun setBillingClientListener(billingClientListeners: BillingClientListener?): FunSolBillingHelper {
		billingClientListener = billingClientListeners
		return this
	}
	
	fun isOfferAvailable(basePlanId: String, offerId: String): Boolean {
		val offerPrice = productPrices.getSubscriptionProductPriceById(basePlanId = basePlanId, offerId = offerId)
		return offerPrice != null
	}
	
	fun wasPremiumUser(): Boolean = runBlocking {
		purchasedHistoryUtils.hasUserEverPurchased()
	}
	
	fun getPurchasedPlansHistory(): List<PurchasedProduct> = runBlocking {
		purchasedHistoryUtils.getPurchasedPlansHistory()
	}
	
	fun getInAppProductDetail(productId: String, productType: String): ProductDetails? {
		return productDetail.getProductDetail(productId = productId, offerId = null, productType = productType)
	}
	
	fun getSubscriptionProductDetail(productId: String, offerId: String? = null, productType: String): ProductDetails? {
		return productDetail.getProductDetail(productId = productId, offerId = offerId, productType = productType)
	}
	
}