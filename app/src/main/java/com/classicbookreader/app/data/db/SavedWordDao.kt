package com.classicbookreader.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedWordDao {

    @Query("SELECT * FROM saved_words ORDER BY created_at DESC")
    fun observeAll(): Flow<List<SavedWordEntity>>

    @Query("SELECT COUNT(*) FROM saved_words")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(word: SavedWordEntity): Long

    @Query("DELETE FROM saved_words WHERE id = :id")
    suspend fun deleteById(id: Long)
}
