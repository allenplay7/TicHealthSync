package com.tichealthsync.watch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthRecordDao {

    /** Insert one fake/health record. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: HealthRecordEntity)

    /** Reactive count of records that still need syncing (anything not "synced"). */
    @Query("SELECT COUNT(*) FROM health_records WHERE syncStatus != 'synced'")
    fun observeUnsyncedCount(): Flow<Int>

    /** One-shot count of records that still need syncing. */
    @Query("SELECT COUNT(*) FROM health_records WHERE syncStatus != 'synced'")
    suspend fun countUnsynced(): Int

    /**
     * Fetch the next record eligible for sync: oldest pending/failed by sequence.
     */
    @Query(
        "SELECT * FROM health_records " +
            "WHERE syncStatus = 'pending' OR syncStatus = 'failed' " +
            "ORDER BY sequence ASC LIMIT 1"
    )
    suspend fun nextPendingRecord(): HealthRecordEntity?

    /** Mark a single record as sent_unconfirmed and stamp it with a batchId. */
    @Query(
        "UPDATE health_records SET syncStatus = 'sent_unconfirmed', batchId = :batchId " +
            "WHERE recordId = :recordId"
    )
    suspend fun markSentUnconfirmed(recordId: String, batchId: String)

    /** Mark every sent_unconfirmed record in the given batch as synced. */
    @Query(
        "UPDATE health_records SET syncStatus = 'synced' " +
            "WHERE batchId = :batchId AND syncStatus = 'sent_unconfirmed'"
    )
    suspend fun markBatchSynced(batchId: String): Int

    /** Debug fallback: mark all sent_unconfirmed records as synced. */
    @Query("UPDATE health_records SET syncStatus = 'synced' WHERE syncStatus = 'sent_unconfirmed'")
    suspend fun markAllSentSynced(): Int

    /** Reset every non-synced record back to pending and drop its batchId. */
    @Query(
        "UPDATE health_records SET syncStatus = 'pending', batchId = NULL " +
            "WHERE syncStatus != 'synced'"
    )
    suspend fun resetNonSynced(): Int

    /** Clear all local records (debug button). */
    @Query("DELETE FROM health_records")
    suspend fun clearAll()

    /** Highest sequence number currently stored, or 0 if empty. */
    @Query("SELECT COALESCE(MAX(sequence), 0) FROM health_records")
    suspend fun maxSequence(): Long
}
