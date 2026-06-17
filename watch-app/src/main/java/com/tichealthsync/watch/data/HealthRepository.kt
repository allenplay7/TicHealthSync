package com.tichealthsync.watch.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper over the Room DAOs. Keeps BLE/manager code free of Room types and
 * gives one place to assign sequence numbers atomically.
 */
class HealthRepository(context: Context) {

    private val db = TicHealthDatabase.get(context)
    private val recordDao = db.healthRecordDao()
    private val sessionDao = db.syncSessionDao()

    fun observeUnsyncedCount(): Flow<Int> = recordDao.observeUnsyncedCount()

    suspend fun countUnsynced(): Int = recordDao.countUnsynced()

    /** Insert [record] after assigning it the next sequence number. Returns the stored row. */
    suspend fun insertWithNextSequence(record: HealthRecordEntity): HealthRecordEntity {
        val next = recordDao.maxSequence() + 1
        val stored = record.copy(sequence = next)
        recordDao.insert(stored)
        return stored
    }

    suspend fun nextPendingRecord(): HealthRecordEntity? = recordDao.nextPendingRecord()

    suspend fun markSentUnconfirmed(recordId: String, batchId: String) =
        recordDao.markSentUnconfirmed(recordId, batchId)

    suspend fun markBatchSynced(batchId: String): Int = recordDao.markBatchSynced(batchId)

    suspend fun markAllSentSynced(): Int = recordDao.markAllSentSynced()

    suspend fun resetNonSynced(): Int = recordDao.resetNonSynced()

    suspend fun clearAll() = recordDao.clearAll()

    suspend fun startSession(session: SyncSessionEntity) = sessionDao.upsert(session)

    suspend fun completeSession(
        syncSessionId: String,
        completedAt: String,
        recordsSent: Int,
        result: String,
        errorMessage: String?
    ) = sessionDao.complete(syncSessionId, completedAt, recordsSent, result, errorMessage)
}
