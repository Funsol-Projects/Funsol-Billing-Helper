package com.funsol.iap.billing.model

import androidx.annotation.Keep
import com.android.billingclient.api.ProductDetails

@Keep
data class ProductPriceInfo(
    var subsKey:String="",
    var productBasePlanKey: String="",
    var productOfferKey: String="",
    var title: String="",
    var type: String="",
    var duration: String = "",
    var price: String = "",
    var productCompleteInfo: ProductDetails? = null
)
