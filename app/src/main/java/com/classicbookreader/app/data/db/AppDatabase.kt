package com.classicbookreader.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        SavedWordEntity::class,
        AnalysisCacheEntity::class,
        PageTranslationCacheEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun savedWordDao(): SavedWordDao
    abstract fun analysisCacheDao(): AnalysisCacheDao
    abstract fun pageTranslationCacheDao(): PageTranslationCacheDao
}
