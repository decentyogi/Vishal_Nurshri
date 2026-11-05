package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlin.math.max

data class CashMemoItem(
    val id: String = UUID.randomUUID().toString(),
    val serialNo: Int = 1,
    val description: String = "",
    val quantity: Double = 1.0,
    val rate: Double = 0.0,
    val amount: Double = 0.0
)

@Entity(tableName = "cash_memos")
data class CashMemoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memoNumber: String,
    val date: String,
    val customerName: String,
    val customerMobile: String,
    val customerAddress: String,
    val itemsJson: String, // JSON serialized List<CashMemoItem>
    val subtotal: Double,
    val discount: Double = 0.0,
    val taxPercentage: Double = 0.0,
    val taxAmount: Double = 0.0,
    val previousDues: Double = 0.0,
    val grandTotal: Double,
    val paidAmount: Double = 0.0,
    val balanceDue: Double = 0.0,
    val paymentMethod: String = "Cash",
    val paymentStatusNote: String = "",
    val notes: String = "",
    val businessName: String = "विशाल नर्सरी",
    val businessPhone: String = "7631873743",
    val businessAddress: String = "ग्राम-कोटा, माँ ताराचण्डी रोड (माँ सीताराम मंदिर), पो०-सासाराम (रोहतास)",
    val signerName: String = "VISHAL NURSERY",
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalPayableWithDues: Double
        get() = grandTotal + previousDues

    val effectivePaidAmount: Double
        get() = if (paidAmount > 0.0 || balanceDue > 0.0) paidAmount else totalPayableWithDues

    val effectiveBalanceDue: Double
        get() = max(0.0, balanceDue)

    val hasPendingBalance: Boolean
        get() = effectiveBalanceDue > 0.009
}

/**
 * Room Entity storing customer profiles with name, phone number, address,
 * and aggregated outstanding balance metrics for instant auto-fill in the cash memo workflow.
 */
@Entity(
    tableName = "customer_profiles",
    indices = [
        Index(value = ["phoneNumber"]),
        Index(value = ["name"])
    ]
)
data class CustomerProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val address: String = "",
    val outstandingBalance: Double = 0.0,
    val totalPurchased: Double = 0.0,
    val totalPaid: Double = 0.0,
    val lastPromiseNote: String = "",
    val lastTransactionAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val hasOutstandingBalance: Boolean
        get() = outstandingBalance > 0.009

    val hasPendingBalance: Boolean
        get() = hasOutstandingBalance
}

/**
 * Room Entity storing the chronological outstanding balance history for each customer
 * (e.g., bill created with partial payment, next-day balance settlement, or manual adjustment).
 */
@Entity(
    tableName = "customer_balance_history",
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["memoId"])
    ]
)
data class CustomerBalanceHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerId: Long,
    val customerName: String,
    val customerPhone: String,
    val memoId: Long? = null,
    val memoNumber: String = "",
    val transactionType: String, // "BILL_CREATED", "PAYMENT_RECEIVED", "BALANCE_SETTLED", "MANUAL_ADJUSTMENT"
    val billAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val balanceChange: Double = 0.0,
    val resultingBalance: Double = 0.0,
    val paymentMethod: String = "Cash",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "plant_rates")
data class PlantRateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val retailPrice: Double,
    val wholesalePrice: Double,
    val category: String = "Plants",
    val updatedAt: Long = System.currentTimeMillis()
)

data class BusinessProfile(
    val shopNameHindi: String = "विशाल नर्सरी",
    val shopNameEnglish: String = "Vishal Nursery",
    val tagline: String = "हमारे यहाँ हर प्रकार के फूल, पेड़-पौधा जैसे-गुलाब, गुलदावदी, गेंदा, क्रोटन सब गमले में तैयार पौधा भी मिलता है।",
    val phone: String = "7631873743",
    val addressLine1: String = "ग्राम-कोटा, माँ ताराचण्डी रोड (माँ सीताराम मंदिर)",
    val addressLine2: String = "पो०-सासाराम (रोहतास)",
    val memoPrefix: String = "",
    val nextMemoNumber: Int = 1001,
    val defaultTaxPercentage: Double = 0.0,
    val enableTax: Boolean = false,
    val customApiKey: String = "",
    val selectedAiProvider: String = "gemini", // "gemini", "openai", "claude"
    val selectedAiModel: String = "gemini-2.5-flash",
    val signerName: String = "VISHAL NURSERY"
)
