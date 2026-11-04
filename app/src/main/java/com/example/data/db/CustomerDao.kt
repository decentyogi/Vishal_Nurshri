package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CustomerBalanceHistoryEntity
import com.example.data.model.CustomerProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customer_profiles ORDER BY outstandingBalance DESC, lastTransactionAt DESC")
    fun getAllCustomers(): Flow<List<CustomerProfileEntity>>

    @Query("SELECT * FROM customer_profiles ORDER BY outstandingBalance DESC, lastTransactionAt DESC")
    suspend fun getAllCustomersList(): List<CustomerProfileEntity>

    @Query("SELECT COUNT(*) FROM customer_profiles")
    suspend fun getCustomerCount(): Int

    @Query(
        """
        SELECT * FROM customer_profiles 
        WHERE name LIKE '%' || :query || '%' 
           OR phoneNumber LIKE '%' || :query || '%' 
           OR address LIKE '%' || :query || '%'
        ORDER BY outstandingBalance DESC, lastTransactionAt DESC
        """
    )
    fun searchCustomers(query: String): Flow<List<CustomerProfileEntity>>

    @Query("SELECT * FROM customer_profiles WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerProfileEntity?

    @Query("SELECT * FROM customer_profiles WHERE phoneNumber = :phone AND phoneNumber != '' LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): CustomerProfileEntity?

    @Query("SELECT * FROM customer_profiles WHERE LOWER(name) = LOWER(:name) AND name != '' LIMIT 1")
    suspend fun getCustomerByName(name: String): CustomerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerProfileEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerProfileEntity)

    @Query("DELETE FROM customer_profiles WHERE id = :id")
    suspend fun deleteCustomer(id: Long)

    // Customer Balance History queries
    @Query("SELECT * FROM customer_balance_history WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getBalanceHistoryForCustomer(customerId: Long): Flow<List<CustomerBalanceHistoryEntity>>

    @Query("SELECT * FROM customer_balance_history WHERE customerId = :customerId ORDER BY timestamp DESC")
    suspend fun getBalanceHistoryForCustomerList(customerId: Long): List<CustomerBalanceHistoryEntity>

    @Query("SELECT * FROM customer_balance_history ORDER BY timestamp DESC")
    fun getAllBalanceHistory(): Flow<List<CustomerBalanceHistoryEntity>>

    @Query("SELECT * FROM customer_balance_history ORDER BY timestamp DESC")
    suspend fun getAllBalanceHistoryList(): List<CustomerBalanceHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalanceHistory(entry: CustomerBalanceHistoryEntity): Long

    @Query("DELETE FROM customer_balance_history WHERE customerId = :customerId")
    suspend fun deleteHistoryForCustomer(customerId: Long)
}
