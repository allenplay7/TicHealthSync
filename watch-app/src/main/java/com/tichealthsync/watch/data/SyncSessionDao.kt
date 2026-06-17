package com.tichealthsync.watch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SyncSessionEntity)

    @Query(
        "UPDATE sync_sessions SET completedAt = :completedAt, recordsSent = :recordsSent, " +
            "result = :result, errorMessage = :errorMessage WHERE syncSessionId = :syncSessionId"
    )
    suspend fun complete(
        syncSessionId: String,
        completedAt: String,
        recordsSent: Int,
        result: String,
        errorMessage: String?
    )

    @Query("SELECT COUNT(*) FROM sync_sessions")
    suspend fun count(): Int
}
