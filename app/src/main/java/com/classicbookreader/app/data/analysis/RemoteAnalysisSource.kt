package com.classicbookreader.app.data.analysis

import com.classicbookreader.app.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams POST /v1/analyze over SSE. Each SSE data line is one
 * [StreamEnvelope]; "partial" carries early harakat, "complete" the full
 * schema result, "error" a server-side failure.
 */
@Singleton
class RemoteAnalysisSource @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val preferences: UserPreferencesRepository,
) : AnalysisSource {

    @Serializable
    private data class StreamEnvelope(
        val type: String = "",
        val vocalizedText: String = "",
        val result: AnalysisResult? = null,
        val message: String = "",
    )

    /** Wire format of POST /v1/analyze, per the plan §5 contract. */
    @Serializable
    private data class AnalyzeBody(
        val image: String,
        val selectionBbox: Bbox,
        val bookContext: BookContext,
        val options: Options,
    ) {
        @Serializable
        data class Bbox(val x: Int, val y: Int, val w: Int, val h: Int)

        @Serializable
        data class BookContext(val title: String, val page: Int)

        @Serializable
        data class Options(val transliteration: Boolean, val glossLanguage: String)
    }

    override fun analyze(request: AnalysisRequest): Flow<AnalysisEvent> = callbackFlow {
        val baseUrl = preferences.backendBaseUrl.first().trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            trySendBlocking(AnalysisEvent.Failed(AnalysisEvent.FailureReason.NETWORK))
            close()
            return@callbackFlow
        }

        val body = json.encodeToString(
            AnalyzeBody.serializer(),
            AnalyzeBody(
                image = Base64.getEncoder().encodeToString(request.jpegImage),
                selectionBbox = AnalyzeBody.Bbox(
                    x = request.selectionBbox.left,
                    y = request.selectionBbox.top,
                    w = request.selectionBbox.width,
                    h = request.selectionBbox.height,
                ),
                bookContext = AnalyzeBody.BookContext(
                    title = request.bookTitle,
                    page = request.pageNumber,
                ),
                options = AnalyzeBody.Options(transliteration = true, glossLanguage = "id"),
            ),
        )

        val httpRequest = Request.Builder()
            .url("$baseUrl/v1/analyze")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val listener = object : EventSourceListener() {
            private var completed = false

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val envelope = runCatching {
                    json.decodeFromString(StreamEnvelope.serializer(), data)
                }.getOrNull() ?: return
                when (envelope.type) {
                    "partial" -> trySendBlocking(AnalysisEvent.Partial(envelope.vocalizedText))
                    "complete" -> envelope.result?.let {
                        completed = true
                        trySendBlocking(AnalysisEvent.Complete(it))
                        close()
                    }
                    "error" -> {
                        completed = true
                        trySendBlocking(AnalysisEvent.Failed(AnalysisEvent.FailureReason.SERVER))
                        close()
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!completed) {
                    trySendBlocking(AnalysisEvent.Failed(AnalysisEvent.FailureReason.SERVER))
                }
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (!completed) {
                    trySendBlocking(AnalysisEvent.Failed(AnalysisEvent.FailureReason.NETWORK))
                }
                close()
            }
        }

        val source = EventSources.createFactory(client).newEventSource(httpRequest, listener)
        awaitClose { source.cancel() }
    }
}
