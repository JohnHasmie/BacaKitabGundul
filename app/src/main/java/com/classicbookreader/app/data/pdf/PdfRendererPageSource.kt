package com.classicbookreader.app.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * PdfRenderer-backed implementation. PdfRenderer and its pages are not
 * thread-safe, so every open/render/close runs on one shared single-thread
 * dispatcher and is additionally serialized per document with a mutex.
 */
internal class PdfRendererPageSource(
    private val fileDescriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val dispatcher: CoroutineDispatcher,
) : PdfPageSource {

    private val mutex = Mutex()

    override val pageCount: Int = renderer.pageCount

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap =
        withContext(dispatcher) {
            mutex.withLock {
                renderer.openPage(pageIndex).use { page ->
                    val width = targetWidthPx.coerceAtLeast(1)
                    val height = (width.toLong() * page.height / page.width)
                        .toInt()
                        .coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { fileDescriptor.close() }
    }

    companion object {
        /** One shared thread for all PDF work in the process. */
        val pdfDispatcher: CoroutineDispatcher =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "pdf-renderer")
            }.asCoroutineDispatcher()
    }
}

class DefaultPdfPageSourceFactory @Inject constructor() : PdfPageSourceFactory {

    override suspend fun open(file: File): PdfPageSource =
        withContext(PdfRendererPageSource.pdfDispatcher) {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                PdfRendererPageSource(
                    fileDescriptor = descriptor,
                    renderer = PdfRenderer(descriptor),
                    dispatcher = PdfRendererPageSource.pdfDispatcher,
                )
            } catch (error: Throwable) {
                runCatching { descriptor.close() }
                throw error
            }
        }
}
