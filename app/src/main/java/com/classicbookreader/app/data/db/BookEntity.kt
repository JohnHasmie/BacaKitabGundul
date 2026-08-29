package com.classicbookreader.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "page_count") val pageCount: Int,
    @ColumnInfo(name = "last_read_page") val lastReadPage: Int = 0,
    @ColumnInfo(name = "cover_path") val coverPath: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    // Recency signal for the home screen's "continue reading" tile.
    @ColumnInfo(name = "last_read_at") val lastReadAt: Long = 0L,
)
