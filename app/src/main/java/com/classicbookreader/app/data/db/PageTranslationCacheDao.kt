package com.classicbookreader.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PageTranslationCacheDao {

    @Query("SELECT * FROM page_translation_cache WHERE book_id = :bookId AND page_number = :pageNumber")
    suspend fun get(bookId: Long, pageNumber: Int): PageTranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PageTranslationCacheEntity)

    /** "Terjemahkan ulang" escape hatch — a bad translation must not be locked in. */
    @Query("DELETE FROM page_translation_cache WHERE book_id = :bookId AND page_number = :pageNumber")
    suspend fun deleteForPage(bookId: Long, pageNumber: Int)

    @Query("DELETE FROM page_translation_cache WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: Long)
}
