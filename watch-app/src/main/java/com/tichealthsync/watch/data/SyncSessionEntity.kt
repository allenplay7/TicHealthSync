package com.tichealthsync.watch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One record of a sync attempt. Lets us keep a history of what was sent and the
 * outcome, independent of the records themselves.
 */
@Entity(tableName = "sync_sessions")
data class SyncSessionEntity(
    @PrimaryKey val syncSessionId: String,
    val startedAt: String,
    val completedAt: String? = null,
    val recordsSent: Int = 0,
    val result: String = "in_progress",
    val errorMessage: String? = null
)
