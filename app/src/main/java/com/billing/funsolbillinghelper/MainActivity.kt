package com.billing.funsolbillinghelper

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.funsol.iap.billing.FunSolBillingHelper
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
        CoroutineScope(Dispatchers.IO).launch {



            FunSolBillingHelper(this@MainActivity).setInAppProductIds(mutableListOf("android.test.purchase")).setSubProductIds(mutableListOf("basic")).enableLogging(false)
                .setBillingListener(object : BillingListener {

                    override fun onClientReady() {
                        Log.i("billing", "onClientReady: Called when client ready after fetch products details and active product against user")
                    }

                    override fun onClientInitError() {
                        Log.i("billing", "onClientInitError: Called when client fail to init")
                    }

                    override fun onProductsPurchased(purchases: List<FunsolPurchase?>) {

                    }

                    override fun onPurchaseAcknowledged(purchase: FunsolPurchase) {
                    }

                    override fun onPurchaseConsumed(purchase: FunsolPurchase) {
                    }

                    override fun onBillingError(error: ErrorType) {
                    }

                }).initialize()
        }


    }
}