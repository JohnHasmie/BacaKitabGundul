package com.classicbookreader.app.data.import_

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.classicbookreader.app.core.util.TitleFormatter
import com.classicbookreader.app.data.db.BookDao
import com.classicbookreader.app.data.db.BookEntity
import com.classicbookreader.app.data.pdf.PdfPageSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies a SAF-picked PDF into app storage, renders its first page as the
 * cover, and inserts the book row. The copy happens immediately while the
 * transient URI grant is valid, so no persistable permission is taken.
 */
@Singleton
class PdfImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val sourceFactory: PdfPageSourceFactory,
) {
    sealed interface Result {
        data class Success(val bookId: Long) : Result
        data class Failure(val reason: FailureReason) : Result
    }

    enum class FailureReason { COPY_FAILED, INVALID_PDF, PROTECTED_PDF }

    suspend fun import(uri: Uri, fallbackTitle: String): Result = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val pdfFile = File(booksDir(), "$id.pdf")
        val coverFile = File(coversDir(), "$id.png")

        val title = TitleFormatter.deriveTitle(queryDisplayName(uri), fallbackTitle)

        try {
            copyToFile(uri, pdfFile)
        } catch (_: Exception) {
            cleanUp(pdfFile, coverFile)
            return@withContext Result.Failure(FailureReason.COPY_FAILED)
        }

        var pageCount = 0
        try {
            sourceFactory.open(pdfFile).use { source ->
                pageCount = source.pageCount
                val cover = source.renderPage(pageIndex = 0, targetWidthPx = COVER_WIDTH_PX)
                coverFile.outputStream().use { stream ->
                    cover.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            }
        } catch (_: SecurityException) {
            cleanUp(pdfFile, coverFile)
            return@withContext Result.Failure(FailureReason.PROTECTED_PDF)
        } catch (_: Exception) {
            cleanUp(pdfFile, coverFile)
            return@withContext Result.Failure(FailureReason.INVALID_PDF)
        }

        val bookId = bookDao.insert(
            BookEntity(
                title = title,
                filePath = pdfFile.absolutePath,
                pageCount = pageCount,
                coverPath = coverFile.absolutePath,
                createdAt = System.currentTimeMillis(),
            ),
        )
        Result.Success(bookId)
    }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()

    private fun copyToFile(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open input stream for $uri")
        input.use { stream ->
            target.outputStream().use { output -> stream.copyTo(output) }
        }
    }

    private fun booksDir(): File = File(context.filesDir, "books").apply { mkdirs() }

    private fun coversDir(): File = File(context.filesDir, "covers").apply { mkdirs() }

    private fun cleanUp(vararg files: File) {
        files.forEach { runCatching { it.delete() } }
    }

    private companion object {
        const val COVER_WIDTH_PX = 600
    }
}
