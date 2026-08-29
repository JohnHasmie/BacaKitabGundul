package com.classicbookreader.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached /v1/analyze responses. The key is a stable hash of
 * (bookId | page | normalized selection bbox), so circling the same
 * region twice never pays for a second model call.
 */
@Entity(tableName = "analysis_cache")
data class AnalysisCacheEntity(
    @PrimaryKey @ColumnInfo(name = "cache_key") val cacheKey: String,
    @ColumnInfo(name = "response_json") val responseJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
