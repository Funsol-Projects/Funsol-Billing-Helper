package com.funsol.iap.billing.model

import androidx.annotation.Keep
import com.android.billingclient.api.ProductDetails

@Keep
data class ProductPriceInfo(
    var productId: String = "",
    var basePlanId: String = "",
    var offerId: String = "",
    var title: String = "",
    var type: String = "",
    var duration: String = "",
    var price: String = "",
    var priceMicro: Long = 0L,
    var currencyCode: String = "",
    var productCompleteInfo: ProductDetails? = null
)
