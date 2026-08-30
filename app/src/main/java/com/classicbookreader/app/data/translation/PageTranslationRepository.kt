package com.classicbookreader.app.data.translation

import com.classicbookreader.app.data.db.PageTranslationCacheDao
import com.classicbookreader.app.data.db.PageTranslationCacheEntity
import com.classicbookreader.app.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

interface PageTranslationRepository {
    /** Instant Room lookup, used to show cached pages without a request. */
    suspend fun getCached(bookId: Long, pageNumber: Int): PageTranslation?

    /** Cache-through translation: a hit answers instantly and costs nothing. */
    fun translate(
        bookId: Long,
        pageNumber: Int,
        request: PageTranslationRequest,
    ): Flow<PageTranslationEvent>

    /** "Terjemahkan ulang": drop the cached page so the next request is fresh. */
    suspend fun invalidate(bookId: Long, pageNumber: Int)
}

@Singleton
class DefaultPageTranslationRepository @Inject constructor(
    private val cacheDao: PageTranslationCacheDao,
    private val remote: RemotePageTranslationSource,
    private val demo: DemoPageTranslationSource,
    private val preferences: UserPreferencesRepository,
    private val json: Json,
) : PageTranslationRepository {

    override suspend fun getCached(bookId: Long, pageNumber: Int): PageTranslation? {
        val cached = cacheDao.get(bookId, pageNumber) ?: return null
        return runCatching {
            json.decodeFromString(PageTranslation.serializer(), cached.responseJson)
        }.getOrNull()
    }

    override fun translate(
        bookId: Long,
        pageNumber: Int,
        request: PageTranslationRequest,
    ): Flow<PageTranslationEvent> = flow {
        getCached(bookId, pageNumber)?.let {
            emit(PageTranslationEvent.Complete(it, fromCache = true))
            return@flow
        }

        // No backend configured → demo source, so the UX works fully offline.
        val useDemo = preferences.backendBaseUrl.first().isBlank()
        val source: PageTranslationSource = if (useDemo) demo else remote

        source.translate(request).collect { event ->
            // Demo results are placeholders — never cache them, or a later
            // real backend would keep answering with the demo text.
            if (event is PageTranslationEvent.Complete && !useDemo) {
                cacheDao.upsert(
                    PageTranslationCacheEntity(
                        bookId = bookId,
                        pageNumber = pageNumber,
                        responseJson = json.encodeToString(PageTranslation.serializer(), event.result),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            emit(event)
        }
    }

    override suspend fun invalidate(bookId: Long, pageNumber: Int) {
        cacheDao.deleteForPage(bookId, pageNumber)
    }
}
