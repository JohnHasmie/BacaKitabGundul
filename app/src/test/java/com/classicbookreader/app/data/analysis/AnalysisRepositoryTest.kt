package com.classicbookreader.app.data.analysis

import com.classicbookreader.app.core.selection.PixelRect
import com.classicbookreader.app.data.db.AnalysisCacheDao
import com.classicbookreader.app.data.db.AnalysisCacheEntity
import com.classicbookreader.app.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private class FakeAnalysisCacheDao : AnalysisCacheDao {
    val entries = mutableMapOf<String, AnalysisCacheEntity>()

    override suspend fun getByKey(key: String): AnalysisCacheEntity? = entries[key]

    override suspend fun upsert(entry: AnalysisCacheEntity) {
        entries[entry.cacheKey] = entry
    }
}

class AnalysisRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun request() = AnalysisRequest(
        jpegImage = byteArrayOf(1, 2, 3),
        selectionBbox = PixelRect(0, 0, 10, 10),
        bookTitle = "Jurumiyah",
        pageNumber = 4,
    )

    private fun repository(
        cacheDao: AnalysisCacheDao,
        remote: RemoteAnalysisSource = mock(),
        baseUrl: String = "",
    ): DefaultAnalysisRepository {
        val preferences = mock<UserPreferencesRepository>()
        whenever(preferences.backendBaseUrl).thenReturn(flowOf(baseUrl))
        return DefaultAnalysisRepository(
            cacheDao = cacheDao,
            remote = remote,
            demo = DemoAnalysisSource(),
            preferences = preferences,
            json = json,
        )
    }

    @Test
    fun cacheHitAnswersInstantlyWithoutAnySource() = runTest {
        val cached = AnalysisResult(selectedText = "الكلام", vocalizedText = "الْكَلَامُ")
        val dao = FakeAnalysisCacheDao().apply {
            entries["key1"] = AnalysisCacheEntity(
                cacheKey = "key1",
                responseJson = json.encodeToString(AnalysisResult.serializer(), cached),
                createdAt = 1L,
            )
        }
        val remote = mock<RemoteAnalysisSource>()

        val events = repository(dao, remote).analyze("key1", request()).toList()

        assertEquals(listOf<AnalysisEvent>(AnalysisEvent.Complete(cached, fromCache = true)), events)
        verify(remote, never()).analyze(any())
    }

    @Test
    fun blankBackendUrlFallsBackToDemoWithoutCaching() = runTest {
        val dao = FakeAnalysisCacheDao()
        val remote = mock<RemoteAnalysisSource>()

        val events = repository(dao, remote, baseUrl = "").analyze("key2", request()).toList()

        assertTrue(events.first() is AnalysisEvent.Partial)
        val complete = events.last() as AnalysisEvent.Complete
        assertTrue(complete.result.words.isNotEmpty())
        verify(remote, never()).analyze(any())
        // Demo output is a placeholder: it must never poison the cache,
        // or a later real backend would keep answering with demo text.
        assertTrue(dao.entries.isEmpty())
    }

    @Test
    fun corruptCacheEntryIsIgnored() = runTest {
        val dao = FakeAnalysisCacheDao().apply {
            entries["key3"] = AnalysisCacheEntity("key3", "not-json{", 1L)
        }

        val events = repository(dao).analyze("key3", request()).toList()

        assertTrue(events.last() is AnalysisEvent.Complete)
    }
}
