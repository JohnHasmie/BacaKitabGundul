package com.classicbookreader.app.data.pdf

import android.graphics.Bitmap
import java.io.Closeable
import java.io.File

/**
 * A renderable PDF document. Implementations must be safe to call from any
 * coroutine; PdfRenderer's single-thread requirement is an implementation
 * detail hidden behind this interface (which also keeps ViewModels
 * JVM-testable with fakes).
 */
interface PdfPageSource : Closeable {
    val pageCount: Int

    /** Renders [pageIndex] at [targetWidthPx]; height follows the page's aspect ratio. */
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap
}

interface PdfPageSourceFactory {
    /** Opens [file]; throws IOException for corrupt files, SecurityException for protected ones. */
    suspend fun open(file: File): PdfPageSource
}
