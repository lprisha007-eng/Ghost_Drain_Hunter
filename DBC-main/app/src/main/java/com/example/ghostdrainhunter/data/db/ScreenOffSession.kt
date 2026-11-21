package com.example.ghostdrainhunter.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screen_off_sessions")
data class ScreenOffSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val startPercent: Int,
    val endTime: Long? = null,
    val endPercent: Int? = null,
    val wasCharging: Boolean = false,
    val status: String
) {
    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_CLOSED = "CLOSED"
        const val STATUS_DISCARDED = "DISCARDED"
    }
}
