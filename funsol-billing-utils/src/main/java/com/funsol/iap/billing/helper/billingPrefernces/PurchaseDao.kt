package com.funsol.iap.billing.helper.billingPrefernces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PurchaseDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchasedProduct(purchase: PurchasedProduct)
    
    @Query("SELECT * FROM purchased_products")
    suspend fun getAllPurchasedProducts(): List<PurchasedProduct>
    
}