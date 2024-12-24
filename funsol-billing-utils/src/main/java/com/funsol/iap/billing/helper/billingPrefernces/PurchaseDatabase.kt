package com.funsol.iap.billing.helper.billingPrefernces

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PurchasedProduct::class], version = 1, exportSchema = false)
abstract class PurchaseDatabase : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao

    companion object {
        @Volatile
        private var INSTANCE: PurchaseDatabase? = null

        fun getDatabase(context: Context): PurchaseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PurchaseDatabase::class.java,
                    "purchased_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}