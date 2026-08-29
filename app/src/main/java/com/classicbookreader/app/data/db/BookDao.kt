package com.classicbookreader.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE last_read_at > 0 ORDER BY last_read_at DESC LIMIT 1")
    fun observeMostRecentlyRead(): Flow<BookEntity?>

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Query("UPDATE books SET last_read_page = :page, last_read_at = :readAt WHERE id = :id")
    suspend fun updateProgress(id: Long, page: Int, readAt: Long)
}
