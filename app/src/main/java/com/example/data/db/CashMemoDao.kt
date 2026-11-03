package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CashMemoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashMemoDao {
    @Query("SELECT * FROM cash_memos ORDER BY createdAt DESC")
    fun getAllCashMemos(): Flow<List<CashMemoEntity>>

    @Query("SELECT * FROM cash_memos ORDER BY createdAt DESC")
    suspend fun getAllCashMemosList(): List<CashMemoEntity>

    @Query("SELECT * FROM cash_memos WHERE id = :id LIMIT 1")
    suspend fun getCashMemoById(id: Long): CashMemoEntity?

    @Query("SELECT * FROM cash_memos WHERE customerName LIKE '%' || :query || '%' OR memoNumber LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchCashMemos(query: String): Flow<List<CashMemoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashMemo(cashMemo: CashMemoEntity): Long

    @Update
    suspend fun updateCashMemo(cashMemo: CashMemoEntity)

    @Query("DELETE FROM cash_memos WHERE id = :id")
    suspend fun deleteCashMemo(id: Long)
}
