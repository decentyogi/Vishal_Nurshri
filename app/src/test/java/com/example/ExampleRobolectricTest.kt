package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CashMemoItem
import com.example.data.repository.CashMemoRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CashMemo AI", appName)
  }

  @Test
  fun itemsSerialization_isCorrect() {
    val items = listOf(
      CashMemoItem(serialNo = 1, description = "Rose Plant", quantity = 10.0, rate = 40.0, amount = 400.0),
      CashMemoItem(serialNo = 2, description = "Pot", quantity = 2.0, rate = 100.0, amount = 200.0)
    )
    val json = CashMemoRepository.serializeItems(items)
    val deserialized = CashMemoRepository.deserializeItems(json)
    assertEquals(2, deserialized.size)
    assertEquals("Rose Plant", deserialized[0].description)
    assertEquals(400.0, deserialized[0].amount, 0.01)
  }
}
