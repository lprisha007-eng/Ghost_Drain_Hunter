package com.example.ghostdrainhunter.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenOffSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(session: ScreenOffSession): Long

    @Query("UPDATE screen_off_sessions SET endTime = :endTime, endPercent = :endPercent, wasCharging = :wasCharging, status = :status WHERE id = :id")
    fun closeSession(
        id: Long,
        endTime: Long,
        endPercent: Int,
        wasCharging: Boolean,
        status: String
    ): Int

    @Update
    fun update(session: ScreenOffSession): Int

    @Query("SELECT * FROM screen_off_sessions WHERE status = 'OPEN' ORDER BY startTime DESC LIMIT 1")
    fun getOpenSession(): ScreenOffSession?

    @Query("SELECT * FROM screen_off_sessions ORDER BY startTime DESC")
    fun observeAll(): Flow<List<ScreenOffSession>>

    @Query("SELECT * FROM screen_off_sessions WHERE startTime >= :sinceTimestamp ORDER BY startTime DESC")
    fun observeSince(sinceTimestamp: Long): Flow<List<ScreenOffSession>>

    @Query("DELETE FROM screen_off_sessions WHERE id = :id")
    fun deleteSessionById(id: Long): Int

    @Query("DELETE FROM screen_off_sessions WHERE startTime < :cutoffTimestamp")
    fun pruneSessionsOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM screen_off_sessions")
    fun clearAll(): Int
}
