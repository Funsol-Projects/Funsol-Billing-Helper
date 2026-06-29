package com.billing.funsolbillinghelper

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.funsol.iap.billing.FunSolBillingHelper
import com.funsol.iap.billing.helper.logFunsolBilling
import com.funsol.iap.billing.listeners.BillingListener
import com.funsol.iap.billing.model.ErrorType
import com.funsol.iap.billing.model.FunsolPurchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val TAG: String? = "BillingHelper"
        var instance = FunSolBillingHelper(this)
        instance.initialize(true)
        instance.enableLogging(true)

        instance.setBillingListener(object : BillingListener {

            override fun onClientReady() {

                Log.i(TAG, "onClientReady: ")
            }

            override fun onClientInitError() {
                Log.i(TAG, "onClientInitError: ")
            }

            override fun onProductsPurchased(purchases: List<FunsolPurchase?>) {
                Log.i(TAG, "onProductsPurchased: ")
            }

            override fun onPurchaseAcknowledged(purchase: FunsolPurchase) {
                Log.i(TAG, "onPurchaseAcknowledged: ")
            }

            override fun onPurchaseConsumed(purchase: FunsolPurchase) {
                Log.i(TAG, "onPurchaseConsumed: ")
            }

            override fun onBillingError(error: ErrorType) {
                Log.i(TAG, "onBillingError: ")
            }

        })

    }
}