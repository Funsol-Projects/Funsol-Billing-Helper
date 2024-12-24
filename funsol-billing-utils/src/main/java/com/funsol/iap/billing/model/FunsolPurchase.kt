package com.funsol.iap.billing.model

data class FunsolPurchase(val products: MutableList<String>,
	val purchaseState: Int,
	val purchaseToken: String,
	val isAcknowledged: Boolean,
	val packageName: String,
	val developerPayload: String,
	val isAutoRenewing: Boolean,
	val orderId: String?,
	val originalJson: String,
	val purchaseTime: Long,
	val quantity: Int,
	val signature: String,
	val skus: ArrayList<String>
)