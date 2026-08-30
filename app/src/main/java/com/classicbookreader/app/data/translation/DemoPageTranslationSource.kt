package com.classicbookreader.app.data.translation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline stand-in used while no backend URL is configured: streams the
 * Jurumiyah opening from mockup screen 9 so the whole interlinear flow can
 * be exercised on a device without any server. Fixed text is demo *data*,
 * not UI copy.
 */
@Singleton
class DemoPageTranslationSource @Inject constructor() : PageTranslationSource {

    override fun translate(request: PageTranslationRequest): Flow<PageTranslationEvent> = flow {
        var emitted = 0
        DEMO_RESULT.lines.forEach { line ->
            delay(350)
            emitted += line.words.size
            emit(PageTranslationEvent.Progress(emitted))
        }
        delay(400)
        emit(PageTranslationEvent.Complete(DEMO_RESULT))
    }

    private companion object {
        private fun line(vararg pairs: Pair<String, String>) = TranslationLine(
            words = pairs.map { (arabic, gloss) -> TranslatedWord(arabic = arabic, gloss = gloss) },
        )

        val DEMO_RESULT = PageTranslation(
            lines = listOf(
                line(
                    "الكلام" to "perkataan",
                    "هو" to "adalah",
                    "اللفظ" to "lafazh",
                    "المركب" to "tersusun",
                ),
                line(
                    "المفيد" to "berfaidah",
                    "بالوضع" to "dengan peletakan",
                ),
                line(
                    "وأقسامه" to "dan bagiannya",
                    "ثلاثة" to "tiga",
                ),
                line(
                    "اسم" to "isim",
                    "وفعل" to "dan fi'il",
                    "وحرف" to "dan huruf",
                ),
                line(
                    "جاء" to "yang datang",
                    "لمعنى" to "untuk suatu makna",
                ),
            ),
            confidence = 0.95f,
        )
    }
}
