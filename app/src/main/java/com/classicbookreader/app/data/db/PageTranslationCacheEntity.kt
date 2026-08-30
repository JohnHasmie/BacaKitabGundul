package com.classicbookreader.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Cached /v1/page-translate responses, keyed per (book, page). An imported
 * PDF never changes, so the composite key is as stable as an image hash
 * without the hashing cost (plan §6; decision recorded in the plan doc).
 */
@Entity(tableName = "page_translation_cache", primaryKeys = ["book_id", "page_number"])
data class PageTranslationCacheEntity(
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "page_number") val pageNumber: Int,
    @ColumnInfo(name = "response_json") val responseJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
