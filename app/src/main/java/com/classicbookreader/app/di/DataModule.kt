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
import com.classicbookreader.app.data.analysis.AnalysisRepository
import com.classicbookreader.app.data.analysis.DefaultAnalysisRepository
import com.classicbookreader.app.data.db.AnalysisCacheDao
import com.classicbookreader.app.data.db.MIGRATION_1_2
import com.classicbookreader.app.data.db.MIGRATION_2_3
import com.classicbookreader.app.data.db.PageTranslationCacheDao
import com.classicbookreader.app.data.db.SavedWordDao
import com.classicbookreader.app.data.repository.BookRepository
import com.classicbookreader.app.data.repository.DefaultBookRepository
import com.classicbookreader.app.data.repository.DefaultSavedWordRepository
import com.classicbookreader.app.data.repository.SavedWordRepository
import com.classicbookreader.app.data.translation.DefaultPageTranslationRepository
import com.classicbookreader.app.data.translation.PageTranslationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "classic_book_reader.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideBookDao(database: AppDatabase): BookDao = database.bookDao()

    @Provides
    fun provideSavedWordDao(database: AppDatabase): SavedWordDao = database.savedWordDao()

    @Provides
    fun provideAnalysisCacheDao(database: AppDatabase): AnalysisCacheDao = database.analysisCacheDao()

    @Provides
    fun providePageTranslationCacheDao(database: AppDatabase): PageTranslationCacheDao =
        database.pageTranslationCacheDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("user_prefs")
        }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // SSE responses stay open while the analysis streams; cap at 90s total.
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds
    abstract fun bindBookRepository(implementation: DefaultBookRepository): BookRepository

    @Binds
    abstract fun bindPdfPageSourceFactory(implementation: DefaultPdfPageSourceFactory): PdfPageSourceFactory

    @Binds
    abstract fun bindAnalysisRepository(implementation: DefaultAnalysisRepository): AnalysisRepository

    @Binds
    abstract fun bindSavedWordRepository(implementation: DefaultSavedWordRepository): SavedWordRepository

    @Binds
    abstract fun bindPageTranslationRepository(
        implementation: DefaultPageTranslationRepository,
    ): PageTranslationRepository
}
