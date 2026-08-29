package com.classicbookreader.app.data.analysis

import com.classicbookreader.app.data.db.AnalysisCacheDao
import com.classicbookreader.app.data.db.AnalysisCacheEntity
import com.classicbookreader.app.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

interface AnalysisRepository {
    /** Cache-through analysis: a hit answers instantly and costs nothing. */
    fun analyze(cacheKey: String, request: AnalysisRequest): Flow<AnalysisEvent>
}

@Singleton
class DefaultAnalysisRepository @Inject constructor(
    private val cacheDao: AnalysisCacheDao,
    private val remote: RemoteAnalysisSource,
    private val demo: DemoAnalysisSource,
    private val preferences: UserPreferencesRepository,
    private val json: Json,
) : AnalysisRepository {

    override fun analyze(cacheKey: String, request: AnalysisRequest): Flow<AnalysisEvent> = flow {
        val cached = cacheDao.getByKey(cacheKey)
        if (cached != null) {
            val result = runCatching {
                json.decodeFromString(AnalysisResult.serializer(), cached.responseJson)
            }.getOrNull()
            if (result != null) {
                emit(AnalysisEvent.Complete(result, fromCache = true))
                return@flow
            }
        }

        // No backend configured → demo source, so the UX works fully offline.
        val useDemo = preferences.backendBaseUrl.first().isBlank()
        val source: AnalysisSource = if (useDemo) demo else remote

        source.analyze(request).collect { event ->
            // Demo results are placeholders — never cache them, or a later
            // real backend would keep answering with the demo text.
            if (event is AnalysisEvent.Complete && !useDemo) {
                cacheDao.upsert(
                    AnalysisCacheEntity(
                        cacheKey = cacheKey,
                        responseJson = json.encodeToString(AnalysisResult.serializer(), event.result),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            emit(event)
        }
    }
}
