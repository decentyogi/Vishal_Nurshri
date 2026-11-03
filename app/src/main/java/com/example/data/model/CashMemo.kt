package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

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
    val notes: String = "",
    val businessName: String = "विशाल नर्सरी",
    val businessPhone: String = "7631073743",
    val businessAddress: String = "ग्राम-बेदा, माँ ताराचण्डी रोड (माँ सीताराम मंदिर के निकट), पो०-सासाराम (रोहतास)",
    val signerName: String = "VISHAL NURSERY",
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalPayableWithDues: Double
        get() = grandTotal + previousDues
}

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
    val phone: String = "7631073743",
    val addressLine1: String = "ग्राम-बेदा, माँ ताराचण्डी रोड (माँ सीताराम मंदिर के निकट)",
    val addressLine2: String = "पो०-सासाराम (रोहतास)",
    val memoPrefix: String = "",
    val nextMemoNumber: Int = 502,
    val defaultTaxPercentage: Double = 0.0,
    val enableTax: Boolean = false,
    val customApiKey: String = "",
    val selectedAiProvider: String = "gemini", // "gemini", "openai", "claude"
    val selectedAiModel: String = "gemini-3.5-flash",
    val signerName: String = "VISHAL NURSERY"
)
