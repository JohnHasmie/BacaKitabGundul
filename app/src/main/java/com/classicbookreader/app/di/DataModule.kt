package com.classicbookreader.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.classicbookreader.app.data.db.AppDatabase
import com.classicbookreader.app.data.db.BookDao
import com.classicbookreader.app.data.pdf.DefaultPdfPageSourceFactory
import com.classicbookreader.app.data.pdf.PdfPageSourceFactory
import com.classicbookreader.app.data.repository.BookRepository
import com.classicbookreader.app.data.repository.DefaultBookRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "classic_book_reader.db").build()

    @Provides
    fun provideBookDao(database: AppDatabase): BookDao = database.bookDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("user_prefs")
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds
    abstract fun bindBookRepository(implementation: DefaultBookRepository): BookRepository

    @Binds
    abstract fun bindPdfPageSourceFactory(implementation: DefaultPdfPageSourceFactory): PdfPageSourceFactory
}
