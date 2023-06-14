package com.billing.funsolbillinghelper

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.funsol.iap.billing.BillingClientListener
import com.funsol.iap.billing.FunSolBillingHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        FunSolBillingHelper(this).setSubKeys(mutableListOf("basic")).enableLogging(isEnableWhileRelease = true).setBillingClientListener(object : BillingClientListener {
            override fun onClientReady() {
                Log.i("billing", "onClientReady: ")
            }

            override fun onClientInitError() {
                Log.i("billing", "onClientInitError: ")
            }

        })

    }
}