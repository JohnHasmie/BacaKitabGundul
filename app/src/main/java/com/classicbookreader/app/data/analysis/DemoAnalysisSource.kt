package com.classicbookreader.app.data.analysis

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline stand-in used while no backend URL is configured: streams a fixed
 * Jurumiyah sample so the whole circle→sheet experience can be exercised on a
 * device without any server. The fixed text is demo *data*, not UI copy.
 */
@Singleton
class DemoAnalysisSource @Inject constructor() : AnalysisSource {

    override fun analyze(request: AnalysisRequest): Flow<AnalysisEvent> = flow {
        delay(600)
        emit(AnalysisEvent.Partial(DEMO_RESULT.vocalizedText))
        delay(900)
        emit(AnalysisEvent.Complete(DEMO_RESULT))
    }

    private companion object {
        val DEMO_RESULT = AnalysisResult(
            selectedText = "الكلام هو اللفظ المركب",
            vocalizedText = "الْكَلَامُ هُوَ اللَّفْظُ الْمُرَكَّبُ",
            transliteration = "al-kalāmu huwa al-lafẓu al-murakkabu",
            contextBefore = "",
            contextAfter = "المفيد بالوضع",
            words = listOf(
                WordAnalysis(
                    arabic = "الكلام",
                    vocalized = "الْكَلَامُ",
                    transliteration = "al-kalāmu",
                    gloss = "kalam (ucapan)",
                    irab = WordIrab(
                        role = "Mubtada'",
                        reasoning = "Isim yang berada di awal kalimat sebagai pokok pembicaraan, marfu'.",
                        caseMarker = "Dhammah di akhir kata",
                    ),
                    sarf = WordSarf(root = "ك ل م", pattern = "فَعَال", form = "Isim mashdar"),
                ),
                WordAnalysis(
                    arabic = "هو",
                    vocalized = "هُوَ",
                    transliteration = "huwa",
                    gloss = "dia / adalah",
                    irab = WordIrab(
                        role = "Dhamir fashl",
                        reasoning = "Kata ganti pemisah antara mubtada' dan khabar, mabni.",
                        caseMarker = "Mabni atas fathah",
                    ),
                    sarf = WordSarf(root = "-", pattern = "-", form = "Dhamir"),
                ),
                WordAnalysis(
                    arabic = "اللفظ",
                    vocalized = "اللَّفْظُ",
                    transliteration = "al-lafẓu",
                    gloss = "lafaz",
                    irab = WordIrab(
                        role = "Khabar",
                        reasoning = "Memberitakan tentang mubtada', marfu'.",
                        caseMarker = "Dhammah di akhir kata",
                    ),
                    sarf = WordSarf(root = "ل ف ظ", pattern = "فَعْل", form = "Isim mashdar"),
                ),
                WordAnalysis(
                    arabic = "المركب",
                    vocalized = "الْمُرَكَّبُ",
                    transliteration = "al-murakkabu",
                    gloss = "yang tersusun",
                    irab = WordIrab(
                        role = "Na'at",
                        reasoning = "Sifat bagi \"اللفظ\", mengikuti dalam rafa'-nya.",
                        caseMarker = "Dhammah di akhir kata",
                    ),
                    sarf = WordSarf(root = "ر ك ب", pattern = "مُفَعَّل", form = "Isim maf'ul dari fi'il tsulatsi mazid"),
                ),
            ),
            phraseGloss = "Kalam adalah lafaz yang tersusun",
            confidence = 0.97f,
        )
    }
}
