package com.tichealthsync.watch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HealthRecordEntity::class, SyncSessionEntity::class],
    version = 1,
    exportSchema = true
)
abstract class TicHealthDatabase : RoomDatabase() {

    abstract fun healthRecordDao(): HealthRecordDao
    abstract fun syncSessionDao(): SyncSessionDao

    companion object {
        @Volatile
        private var INSTANCE: TicHealthDatabase? = null

        fun get(context: Context): TicHealthDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TicHealthDatabase::class.java,
                    "tichealthsync.db"
                ).build().also { INSTANCE = it }
            }
    }
}
