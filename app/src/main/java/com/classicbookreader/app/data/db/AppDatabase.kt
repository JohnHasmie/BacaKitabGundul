package com.classicbookreader.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, SavedWordEntity::class, AnalysisCacheEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun savedWordDao(): SavedWordDao
    abstract fun analysisCacheDao(): AnalysisCacheDao
}
