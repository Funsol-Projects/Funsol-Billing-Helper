package com.funsol.iap.billing

import com.android.billingclient.api.Purchase
import com.funsol.iap.billing.model.ErrorType

interface BillingClientListener {
    fun onClientReady()
    fun onClientInitError()
}