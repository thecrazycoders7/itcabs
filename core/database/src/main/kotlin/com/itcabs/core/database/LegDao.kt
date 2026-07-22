package com.itcabs.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LegDao {
    @Query("SELECT * FROM legs ORDER BY id DESC")
    fun getLegsFlow(): Flow<List<LegEntity>>

    @Query("SELECT * FROM legs WHERE coordinatorId = :userId ORDER BY id DESC")
    fun getMyLegsFlow(userId: Long): Flow<List<LegEntity>>

    @Query("SELECT * FROM legs WHERE claimedBy = :userId ORDER BY id DESC")
    fun getMyClaimsFlow(userId: Long): Flow<List<LegEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLegs(legs: List<LegEntity>)

    @Query("DELETE FROM legs")
    suspend fun clearAll()

    /** Delete only the legs that match a specific "feed" or "my" view if needed. */
    @Query("DELETE FROM legs WHERE coordinatorId = :userId")
    suspend fun clearMyLegs(userId: Long)
}
