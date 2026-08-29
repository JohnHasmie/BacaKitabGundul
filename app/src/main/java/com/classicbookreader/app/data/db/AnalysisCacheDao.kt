package com.classicbookreader.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnalysisCacheDao {

    @Query("SELECT * FROM analysis_cache WHERE cache_key = :key")
    suspend fun getByKey(key: String): AnalysisCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AnalysisCacheEntity)
}
