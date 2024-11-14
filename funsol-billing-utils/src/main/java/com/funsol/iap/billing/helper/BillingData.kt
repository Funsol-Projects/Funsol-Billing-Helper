package com.funsol.iap.billing.helper

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.funsol.iap.billing.helper.billingPrefernces.PurchasedProduct
import com.funsol.iap.billing.listeners.BillingClientListener
import com.funsol.iap.billing.listeners.BillingEventListener

object BillingData {
	
	var enableLog = false
	var isClientReady = false
	var purchasesUpdatedListener: PurchasesUpdatedListener? = null
	val subProductIds by lazy { mutableListOf<String>() }
	val inAppProductIds by lazy { mutableListOf<String>() }
	var billingClient: BillingClient? = null
	var billingEventListener: BillingEventListener? = null
	var billingClientListener: BillingClientListener? = null
	val allProducts by lazy { mutableListOf<ProductDetails>() }
	val consumeAbleProductIds by lazy { mutableListOf<String>() }
	val purchasedSubsProductList by lazy { mutableListOf<Purchase>() }
	val purchasedInAppProductList by lazy { mutableListOf<Purchase>() }
	var lastPurchasedProduct: PurchasedProduct? = null
}