package com.billing.funsolbillinghelper

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.funsol.iap.billing.FunSolBillingHelper
import com.funsol.iap.billing.listeners.BillingClientListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        CoroutineScope(Dispatchers.IO).launch {



            FunSolBillingHelper(this@MainActivity).setInAppProductIds(mutableListOf("android.test.purchase")).setSubProductIds(mutableListOf("basic")).enableLogging(false)
                .setBillingClientListener(object : BillingClientListener {
                    override fun onPurchasesUpdated() {
                        Log.i("billing", "onPurchasesUpdated: called when user latest premium status fetched ")
                    }

                    override fun onClientReady() {
                        Log.i("billing", "onClientReady: Called when client ready after fetch products details and active product against user")
                    }

                    override fun onClientInitError() {
                        Log.i("billing", "onClientInitError: Called when client fail to init")
                    }

                }).initialize()
        }

        CoroutineScope(Dispatchers.IO).launch {


            FunSolBillingHelper(this@MainActivity).setInAppProductIds(mutableListOf("android.test.purchase")).setSubProductIds(mutableListOf("basic")).enableLogging(false)
                .setBillingClientListener(object : BillingClientListener {
                    override fun onPurchasesUpdated() {
                        Log.i("billing", "onPurchasesUpdated: called when user latest premium status fetched ")
                    }

                    override fun onClientReady() {
                        Log.i("billing", "onClientReady: Called when client ready after fetch products details and active product against user")
                    }

                    override fun onClientInitError() {
                        Log.i("billing", "onClientInitError: Called when client fail to init")
                    }

                }).initialize()
        }


    }
}