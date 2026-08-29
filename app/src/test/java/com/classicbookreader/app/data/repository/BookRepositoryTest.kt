package com.classicbookreader.app.data.repository

import com.classicbookreader.app.data.db.BookDao
import com.classicbookreader.app.data.db.BookEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock

private class FakeBookDao : BookDao {
    val books = MutableStateFlow<List<BookEntity>>(emptyList())

    override fun observeAll(): Flow<List<BookEntity>> =
        books.map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun getById(id: Long): BookEntity? = books.value.firstOrNull { it.id == id }

    override fun observeMostRecentlyRead(): Flow<BookEntity?> =
        books.map { list -> list.filter { it.lastReadAt > 0 }.maxByOrNull { it.lastReadAt } }

    override suspend fun insert(book: BookEntity): Long {
        val id = (books.value.maxOfOrNull { it.id } ?: 0L) + 1
        books.value = books.value + book.copy(id = id)
        return id
    }

    override suspend fun updateProgress(id: Long, page: Int, readAt: Long) {
        books.value = books.value.map { book ->
            if (book.id == id) book.copy(lastReadPage = page, lastReadAt = readAt) else book
        }
    }
}

class BookRepositoryTest {

    private fun book(id: Long, createdAt: Long = id, lastReadAt: Long = 0) = BookEntity(
        id = id,
        title = "Book $id",
        filePath = "/books/$id.pdf",
        pageCount = 40,
        coverPath = null,
        createdAt = createdAt,
        lastReadAt = lastReadAt,
    )

    @Test
    fun saveReadingProgressUpdatesPageAndRecency() = runTest {
        val dao = FakeBookDao()
        val repository = DefaultBookRepository(dao, importer = mock())
        dao.books.value = listOf(book(1))

        repository.saveReadingProgress(bookId = 1, page = 11, now = 999L)

        val saved = dao.getById(1)!!
        assertEquals(11, saved.lastReadPage)
        assertEquals(999L, saved.lastReadAt)
    }

    @Test
    fun continueReadingPicksMostRecentlyReadAndIgnoresUnread() = runTest {
        val dao = FakeBookDao()
        val repository = DefaultBookRepository(dao, importer = mock())
        dao.books.value = listOf(
            book(1, lastReadAt = 100),
            book(2, lastReadAt = 300),
            book(3, lastReadAt = 0), // never opened
        )

        assertEquals(2L, repository.observeContinueReading().first()?.id)
    }

    @Test
    fun continueReadingIsNullWhenNothingRead() = runTest {
        val dao = FakeBookDao()
        val repository = DefaultBookRepository(dao, importer = mock())
        dao.books.value = listOf(book(1), book(2))

        assertNull(repository.observeContinueReading().first())
    }

    @Test
    fun observeBooksOrdersByNewestFirst() = runTest {
        val dao = FakeBookDao()
        val repository = DefaultBookRepository(dao, importer = mock())
        dao.books.value = listOf(book(1, createdAt = 10), book(2, createdAt = 30), book(3, createdAt = 20))

        assertEquals(listOf(2L, 3L, 1L), repository.observeBooks().first().map { it.id })
    }
}
