package com.classicbookreader.app.data.repository

import com.classicbookreader.app.data.analysis.WordAnalysis
import com.classicbookreader.app.data.analysis.WordIrab
import com.classicbookreader.app.data.analysis.WordSarf
import com.classicbookreader.app.data.db.SavedWordDao
import com.classicbookreader.app.data.db.SavedWordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

interface SavedWordRepository {
    fun observeAll(): Flow<List<SavedWordEntity>>
    fun observeCount(): Flow<Int>
    suspend fun save(word: WordAnalysis, bookId: Long?, pageNumber: Int?): Long
    suspend fun delete(id: Long)
}

@Singleton
class DefaultSavedWordRepository @Inject constructor(
    private val dao: SavedWordDao,
    private val json: Json,
) : SavedWordRepository {

    override fun observeAll(): Flow<List<SavedWordEntity>> = dao.observeAll()

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun save(word: WordAnalysis, bookId: Long?, pageNumber: Int?): Long =
        dao.insert(
            SavedWordEntity(
                bookId = bookId,
                arabicText = word.arabic,
                vocalizedText = word.vocalized,
                transliteration = word.transliteration.ifBlank { null },
                gloss = word.gloss,
                irabJson = json.encodeToString(WordIrab.serializer(), word.irab),
                sarfJson = json.encodeToString(WordSarf.serializer(), word.sarf),
                pageNumber = pageNumber,
                createdAt = System.currentTimeMillis(),
            ),
        )

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
