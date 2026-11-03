package com.example

import com.example.util.NumberToWordsConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun numberToWords_isCorrect() {
    val words1 = NumberToWordsConverter.convertToIndianRupees(500.0)
    assertEquals("Five Hundred Rupees Only", words1)

    val words2 = NumberToWordsConverter.convertToIndianRupees(1250.50)
    assertEquals("One Thousand Two Hundred Fifty Rupees and Fifty Paise Only", words2)
  }

  @Test
  fun splitRupeesAndPaise_isCorrect() {
    val (rs1, ps1) = NumberToWordsConverter.splitRupeesAndPaise(450.0)
    assertEquals("450", rs1)
    assertEquals("--", ps1)

    val (rs2, ps2) = NumberToWordsConverter.splitRupeesAndPaise(340.75)
    assertEquals("340", rs2)
    assertEquals("75", ps2)
  }
}
