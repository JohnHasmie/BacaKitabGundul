package com.classicbookreader.app.data.repository

import android.net.Uri
import com.classicbookreader.app.data.db.BookDao
import com.classicbookreader.app.data.db.BookEntity
import com.classicbookreader.app.data.import_.PdfImporter
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface BookRepository {
    fun observeBooks(): Flow<List<BookEntity>>
    fun observeContinueReading(): Flow<BookEntity?>
    suspend fun getBook(id: Long): BookEntity?
    suspend fun importPdf(uri: Uri, fallbackTitle: String): PdfImporter.Result
    suspend fun saveReadingProgress(bookId: Long, page: Int, now: Long = System.currentTimeMillis())
}

@Singleton
class DefaultBookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val importer: PdfImporter,
) : BookRepository {

    override fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeAll()

    override fun observeContinueReading(): Flow<BookEntity?> = bookDao.observeMostRecentlyRead()

    override suspend fun getBook(id: Long): BookEntity? = bookDao.getById(id)

    override suspend fun importPdf(uri: Uri, fallbackTitle: String): PdfImporter.Result =
        importer.import(uri, fallbackTitle)

    override suspend fun saveReadingProgress(bookId: Long, page: Int, now: Long) {
        bookDao.updateProgress(id = bookId, page = page, readAt = now)
    }
}
