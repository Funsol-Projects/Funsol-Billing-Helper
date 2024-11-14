package com.funsol.iap.billing.helper

import android.util.Log
import com.funsol.iap.billing.helper.BillingData.enableLog

fun logFunsolBilling(message: String) {

    val TAG = "FunSolBillingHelper"

    if (enableLog) {
        Log.d(TAG, message)
    }
}