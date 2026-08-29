package com.classicbookreader.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 (Phase 1: books) → v2 (Phase 2: saved_words + analysis_cache). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saved_words` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `book_id` INTEGER,
                `global_source` TEXT,
                `arabic_text` TEXT NOT NULL,
                `vocalized_text` TEXT NOT NULL,
                `transliteration` TEXT,
                `gloss` TEXT NOT NULL,
                `irab_json` TEXT,
                `sarf_json` TEXT,
                `page_number` INTEGER,
                `created_at` INTEGER NOT NULL,
                `memorization_status` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `analysis_cache` (
                `cache_key` TEXT NOT NULL,
                `response_json` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`cache_key`)
            )
            """.trimIndent(),
        )
    }
}
