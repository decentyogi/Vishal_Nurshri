package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.CashMemoEntity
import com.example.data.model.CustomerBalanceHistoryEntity
import com.example.data.model.CustomerProfileEntity
import com.example.data.model.PlantRateEntity

@Database(
    entities = [
        CashMemoEntity::class,
        PlantRateEntity::class,
        CustomerProfileEntity::class,
        CustomerBalanceHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cashMemoDao(): CashMemoDao
    abstract fun plantRateDao(): PlantRateDao
    abstract fun customerDao(): CustomerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cash_memos ADD COLUMN paidAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE cash_memos ADD COLUMN balanceDue REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE cash_memos ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'Cash'")
                db.execSQL("ALTER TABLE cash_memos ADD COLUMN paymentStatusNote TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customer_profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `phoneNumber` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `outstandingBalance` REAL NOT NULL,
                        `totalPurchased` REAL NOT NULL,
                        `totalPaid` REAL NOT NULL,
                        `lastPromiseNote` TEXT NOT NULL,
                        `lastTransactionAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_profiles_phoneNumber` ON `customer_profiles` (`phoneNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_profiles_name` ON `customer_profiles` (`name`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customer_balance_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customerId` INTEGER NOT NULL,
                        `customerName` TEXT NOT NULL,
                        `customerPhone` TEXT NOT NULL,
                        `memoId` INTEGER,
                        `memoNumber` TEXT NOT NULL,
                        `transactionType` TEXT NOT NULL,
                        `billAmount` REAL NOT NULL,
                        `paidAmount` REAL NOT NULL,
                        `balanceChange` REAL NOT NULL,
                        `resultingBalance` REAL NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_balance_history_customerId` ON `customer_balance_history` (`customerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_balance_history_memoId` ON `customer_balance_history` (`memoId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cashmemo_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
