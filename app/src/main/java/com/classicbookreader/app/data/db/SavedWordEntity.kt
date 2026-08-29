package com.classicbookreader.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_words")
data class SavedWordEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    // Null for words saved from the global overlay mode (Phase 4).
    @ColumnInfo(name = "book_id") val bookId: Long?,
    @ColumnInfo(name = "global_source") val globalSource: String? = null,
    @ColumnInfo(name = "arabic_text") val arabicText: String,
    @ColumnInfo(name = "vocalized_text") val vocalizedText: String,
    @ColumnInfo(name = "transliteration") val transliteration: String?,
    @ColumnInfo(name = "gloss") val gloss: String,
    @ColumnInfo(name = "irab_json") val irabJson: String?,
    @ColumnInfo(name = "sarf_json") val sarfJson: String?,
    @ColumnInfo(name = "page_number") val pageNumber: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    // "new" until the murajaah loop (Phase 5) starts grading recall.
    @ColumnInfo(name = "memorization_status") val memorizationStatus: String = "new",
)
