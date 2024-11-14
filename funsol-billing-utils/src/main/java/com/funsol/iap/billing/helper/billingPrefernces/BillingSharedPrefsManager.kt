package com.funsol.iap.billing.helper.billingPrefernces

import android.content.Context
import android.os.Build

class BillingSharedPrefsManager(context: Context) {
    
    private val sharedPreferences = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    } else {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
	
	companion object {
        private const val PREF_NAME = "billing_preferences"
        private const val KEY_IS_PREMIUM = "is_premium_user"
    }
    
    /**
     * Saves the premium status of the user.
     * @param isPremium true if the user is premium, false otherwise
     */
    fun setPremiumStatus(isPremium: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_IS_PREMIUM, isPremium)
            .apply()
    }
    
    /**
     * Retrieves the premium status of the user.
     * @return true if the user is premium, false otherwise
     */
    fun isUserPremium(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_PREMIUM, false)
    }
}