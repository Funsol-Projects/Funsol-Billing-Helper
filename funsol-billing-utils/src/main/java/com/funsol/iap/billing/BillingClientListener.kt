package com.funsol.iap.billing

import com.android.billingclient.api.Purchase

interface BillingClientListener {
    fun onPurchasesUpdated()
    fun onClientReady()
    fun onClientAllReadyConnected(){}
    fun onClientInitError()
}