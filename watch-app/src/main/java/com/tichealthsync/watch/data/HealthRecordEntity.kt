package com.tichealthsync.watch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single health record persisted in Room. Sync state is tracked here so records
 * survive disconnects and app restarts.
 *
 * syncStatus is always one of: "pending", "sent_unconfirmed", "synced", "failed".
 */
@Entity(tableName = "health_records")
data class HealthRecordEntity(
    @PrimaryKey val recordId: String,
    val deviceId: String,
    val type: String,
    val value: Double,
    val unit: String,
    val startTime: String,
    val endTime: String,
    val createdAt: String,
    val sequence: Long,
    val syncStatus: String = "pending",
    val batchId: String? = null
)
