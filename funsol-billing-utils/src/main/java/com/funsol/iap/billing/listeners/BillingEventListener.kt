package com.funsol.iap.billing.listeners

import com.android.billingclient.api.Purchase
import com.funsol.iap.billing.model.ErrorType


interface BillingEventListener {
    fun onProductsPurchased(purchases: List<Purchase?>)
    fun onPurchaseAcknowledged(purchase: Purchase)
    fun onPurchaseConsumed(purchase: Purchase)
    fun onBillingError(error: ErrorType)

}