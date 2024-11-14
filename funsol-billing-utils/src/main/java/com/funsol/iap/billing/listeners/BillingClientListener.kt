package com.funsol.iap.billing.listeners

interface BillingClientListener {
    fun onPurchasesUpdated()
    fun onClientReady()
    fun onClientAllReadyConnected(){}
    fun onClientInitError()
}