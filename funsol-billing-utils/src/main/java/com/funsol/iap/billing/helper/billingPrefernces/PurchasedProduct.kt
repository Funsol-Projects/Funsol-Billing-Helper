package com.funsol.iap.billing.helper.billingPrefernces

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Entity representing product pricing information and purchase details,
 * used to store purchase history in the local database.
 *
 * @property productId Unique key identifying the subscription.
 * @property basePlanId Key for the base plan associated with the subscription.
 * @property offerId Key for the specific offer tied to the subscription plan.
 * @property title Display title of the product, visible to users.
 * @property type Type of the product (e.g., subscription, in-app purchase).
 * @property duration Duration of the subscription (e.g., "1 month").
 * @property price Formatted price string displayed to the user (e.g., "$4.99").
 * @property priceMicro Unformatted price in micro-units for precise calculations.
 * @property currencyCode Currency code for the price (e.g., "USD").
 * @property purchaseTime Timestamp in milliseconds of when the product was purchased.
 */
@Entity(tableName = "purchased_products")
data class PurchasedProduct(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "orderId") var orderId: String? = null,
    @ColumnInfo(name = "productId") var productId: String? = null,
    @ColumnInfo(name = "base_plan_id") var basePlanId: String? = null,
    @ColumnInfo(name = "offer_id") var offerId: String? = null,
    @ColumnInfo(name = "title") var title: String? = null,
    @ColumnInfo(name = "type") var type: String? = null,
    @ColumnInfo(name = "duration") var duration: String? = null,
    @ColumnInfo(name = "price") var price: String? = null,
    @ColumnInfo(name = "price_micro") var priceMicro: Long? = null,
    @ColumnInfo(name = "currency_code") var currencyCode: String? = null,
    @ColumnInfo(name = "purchase_time") var purchaseTime: Long = System.currentTimeMillis(),
)