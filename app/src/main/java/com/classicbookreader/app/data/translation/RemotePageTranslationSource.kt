package com.classicbookreader.app.data.translation

import com.classicbookreader.app.data.analysis.AnalysisEvent
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
 * Streams POST /v1/page-translate over SSE. Each SSE data line is one
 * [StreamEnvelope]; "progress" carries the emitted word count, "complete"
 * the full page result, "error" a server-side failure.
 */
@Singleton
class RemotePageTranslationSource @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val preferences: UserPreferencesRepository,
) : PageTranslationSource {

    @Serializable
    private data class StreamEnvelope(
        val type: String = "",
        val wordCount: Int = 0,
        val result: PageTranslation? = null,
        val message: String = "",
    )

    /** Wire format of POST /v1/page-translate, per the plan §5 contract. */
    @Serializable
    private data class TranslateBody(
        val image: String,
        val bookContext: BookContext,
    ) {
        @Serializable
        data class BookContext(val title: String, val page: Int)
    }

    override fun translate(request: PageTranslationRequest): Flow<PageTranslationEvent> =
        callbackFlow {
            val baseUrl = preferences.backendBaseUrl.first().trim().trimEnd('/')
            if (baseUrl.isBlank()) {
                trySendBlocking(PageTranslationEvent.Failed(AnalysisEvent.FailureReason.NETWORK))
                close()
                return@callbackFlow
            }

            val body = json.encodeToString(
                TranslateBody.serializer(),
                TranslateBody(
                    image = Base64.getEncoder().encodeToString(request.jpegImage),
                    bookContext = TranslateBody.BookContext(
                        title = request.bookTitle,
                        page = request.pageNumber,
                    ),
                ),
            )

            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/page-translate")
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
                        "progress" -> trySendBlocking(PageTranslationEvent.Progress(envelope.wordCount))
                        "complete" -> envelope.result?.let {
                            completed = true
                            trySendBlocking(PageTranslationEvent.Complete(it))
                            close()
                        }
                        "error" -> {
                            completed = true
                            trySendBlocking(
                                PageTranslationEvent.Failed(AnalysisEvent.FailureReason.SERVER),
                            )
                            close()
                        }
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    if (!completed) {
                        trySendBlocking(PageTranslationEvent.Failed(AnalysisEvent.FailureReason.SERVER))
                    }
                    close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    if (!completed) {
                        // This endpoint is the costliest per call, so the shared
                        // limiter's 429 deserves its own user-facing message.
                        val reason = if (response?.code == HTTP_TOO_MANY_REQUESTS) {
                            AnalysisEvent.FailureReason.RATE_LIMITED
                        } else {
                            AnalysisEvent.FailureReason.NETWORK
                        }
                        trySendBlocking(PageTranslationEvent.Failed(reason))
                    }
                    close()
                }
            }

            val source = EventSources.createFactory(client).newEventSource(httpRequest, listener)
            awaitClose { source.cancel() }
        }

    private companion object {
        // Not in java.net.HttpURLConnection's constants.
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
