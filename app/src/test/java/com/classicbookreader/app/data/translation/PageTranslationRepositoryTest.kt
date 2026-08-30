package com.classicbookreader.app.data.translation

import com.classicbookreader.app.data.db.PageTranslationCacheDao
import com.classicbookreader.app.data.db.PageTranslationCacheEntity
import com.classicbookreader.app.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private class FakePageTranslationCacheDao : PageTranslationCacheDao {
    val entries = mutableMapOf<Pair<Long, Int>, PageTranslationCacheEntity>()

    override suspend fun get(bookId: Long, pageNumber: Int): PageTranslationCacheEntity? =
        entries[bookId to pageNumber]

    override suspend fun upsert(entry: PageTranslationCacheEntity) {
        entries[entry.bookId to entry.pageNumber] = entry
    }

    override suspend fun deleteForPage(bookId: Long, pageNumber: Int) {
        entries.remove(bookId to pageNumber)
    }

    override suspend fun deleteForBook(bookId: Long) {
        entries.keys.removeAll { (id, _) -> id == bookId }
    }
}

class PageTranslationRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun request() = PageTranslationRequest(
        jpegImage = byteArrayOf(1, 2, 3),
        bookTitle = "Jurumiyah",
        pageNumber = 12,
    )

    private fun repository(
        cacheDao: PageTranslationCacheDao,
        remote: RemotePageTranslationSource = mock(),
        baseUrl: String = "",
    ): DefaultPageTranslationRepository {
        val preferences = mock<UserPreferencesRepository>()
        whenever(preferences.backendBaseUrl).thenReturn(flowOf(baseUrl))
        return DefaultPageTranslationRepository(
            cacheDao = cacheDao,
            remote = remote,
            demo = DemoPageTranslationSource(),
            preferences = preferences,
            json = json,
        )
    }

    @Test
    fun cacheHitAnswersInstantlyWithoutAnySource() = runTest {
        val cached = PageTranslation(
            lines = listOf(TranslationLine(listOf(TranslatedWord(arabic = "الكلام", gloss = "perkataan")))),
            confidence = 0.9f,
        )
        val dao = FakePageTranslationCacheDao().apply {
            entries[1L to 12] = PageTranslationCacheEntity(
                bookId = 1L,
                pageNumber = 12,
                responseJson = json.encodeToString(PageTranslation.serializer(), cached),
                createdAt = 1L,
            )
        }
        val remote = mock<RemotePageTranslationSource>()

        val events = repository(dao, remote).translate(1L, 12, request()).toList()

        assertEquals(
            listOf<PageTranslationEvent>(PageTranslationEvent.Complete(cached, fromCache = true)),
            events,
        )
        verify(remote, never()).translate(any())
    }

    @Test
    fun blankBackendUrlFallsBackToDemoWithoutCaching() = runTest {
        val dao = FakePageTranslationCacheDao()
        val remote = mock<RemotePageTranslationSource>()

        val events = repository(dao, remote, baseUrl = "").translate(1L, 12, request()).toList()

        assertTrue(events.first() is PageTranslationEvent.Progress)
        val complete = events.last() as PageTranslationEvent.Complete
        assertTrue(complete.result.lines.isNotEmpty())
        verify(remote, never()).translate(any())
        // Demo output is a placeholder: it must never poison the cache,
        // or a later real backend would keep answering with demo text.
        assertTrue(dao.entries.isEmpty())
    }

    @Test
    fun corruptCacheEntryIsIgnored() = runTest {
        val dao = FakePageTranslationCacheDao().apply {
            entries[1L to 12] = PageTranslationCacheEntity(1L, 12, "not-json{", 1L)
        }

        val events = repository(dao).translate(1L, 12, request()).toList()

        assertTrue(events.last() is PageTranslationEvent.Complete)
    }

    @Test
    fun invalidateDropsOnlyThatPage() = runTest {
        val dao = FakePageTranslationCacheDao().apply {
            entries[1L to 12] = PageTranslationCacheEntity(1L, 12, "{}", 1L)
            entries[1L to 13] = PageTranslationCacheEntity(1L, 13, "{}", 1L)
        }

        repository(dao).invalidate(1L, 12)

        assertNull(dao.entries[1L to 12])
        assertTrue(dao.entries.containsKey(1L to 13))
    }
}
