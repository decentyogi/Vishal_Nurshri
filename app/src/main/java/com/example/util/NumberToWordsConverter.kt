package com.example.util

import java.util.Locale
import kotlin.math.roundToLong

object NumberToWordsConverter {

    private val units = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    )

    private val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    )

    fun convertToIndianRupees(amount: Double): String {
        if (amount == 0.0) return "Zero Rupees Only"

        val rounded = (amount * 100).roundToLong()
        val rupees = rounded / 100
        val paise = rounded % 100

        val words = StringBuilder()
        if (rupees > 0) {
            words.append(convertIndianNumber(rupees)).append(" Rupees")
        }
        if (paise > 0) {
            if (words.isNotEmpty()) words.append(" and ")
            words.append(convertLessThanThousand(paise)).append(" Paise")
        }
        words.append(" Only")
        return words.toString().trim()
    }

    private fun convertIndianNumber(n: Long): String {
        if (n == 0L) return ""

        val crore = n / 10000000L
        val lakh = (n % 10000000L) / 100000L
        val thousand = (n % 100000L) / 1000L
        val hundredAndBelow = n % 1000L

        val result = StringBuilder()

        if (crore > 0) {
            result.append(convertLessThanThousand(crore)).append(" Crore ")
        }
        if (lakh > 0) {
            result.append(convertLessThanThousand(lakh)).append(" Lakh ")
        }
        if (thousand > 0) {
            result.append(convertLessThanThousand(thousand)).append(" Thousand ")
        }
        if (hundredAndBelow > 0) {
            result.append(convertLessThanThousand(hundredAndBelow))
        }

        return result.toString().trim()
    }

    private fun convertLessThanThousand(n: Long): String {
        val num = n.toInt()
        val result = StringBuilder()

        if (num >= 100) {
            result.append(units[num / 100]).append(" Hundred ")
        }

        val rem = num % 100
        if (rem in 1..19) {
            result.append(units[rem])
        } else if (rem >= 20) {
            result.append(tens[rem / 10])
            if (rem % 10 > 0) {
                result.append(" ").append(units[rem % 10])
            }
        }

        return result.toString().trim()
    }

    fun formatCurrency(amount: Double): String {
        return String.format(Locale.ENGLISH, "₹ %.2f", amount)
    }

    fun splitRupeesAndPaise(amount: Double): Pair<String, String> {
        val totalPaise = (amount * 100).roundToLong()
        val rs = totalPaise / 100
        val ps = totalPaise % 100
        val psStr = if (ps == 0L) "--" else String.format(Locale.ENGLISH, "%02d", ps)
        return Pair(rs.toString(), psStr)
    }
}
