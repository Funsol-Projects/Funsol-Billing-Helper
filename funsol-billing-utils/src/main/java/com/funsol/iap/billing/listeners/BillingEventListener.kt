package com.funsol.iap.billing.listeners

import com.funsol.iap.billing.model.ErrorType
import com.funsol.iap.billing.model.FunsolPurchase

interface BillingEventListener {
    fun onProductsPurchased(purchases: List<FunsolPurchase?>)
    fun onPurchaseAcknowledged(purchase: FunsolPurchase)
    fun onPurchaseConsumed(purchase: FunsolPurchase)
    fun onBillingError(error: ErrorType)
}