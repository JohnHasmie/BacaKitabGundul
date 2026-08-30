# Classic Book Reader

Android app for reading bare (undiacritized) classical Arabic texts —
"kitab gundul" — with an AI assistant: circle any word or phrase and get
its vocalization, i'rab, sarf, and meaning, analyzed with the surrounding
context. A global overlay mode brings the same circle-to-analyze gesture
on top of any other app (Qur'an apps, PDF readers).

- **Design reference**: `design/mockups/` — 18-screen canvas, locked on
  the "Tegas Glass" system (see `design/mockups/README.md` for tokens).
- **Implementation plan**: `docs/RENCANA_IMPLEMENTASI.md` (v2.4).

## Tech stack

Kotlin 2.x · Jetpack Compose (Material 3, custom Tegas Glass theme) ·
MVVM + StateFlow · Hilt · Navigation Compose · minSdk 26 / target 35.
Conventions: code, identifiers, and database schema in English; UI copy
in Bahasa Indonesia; Islamic domain terms (i'rab, sarf, murajaah) used
as domain vocabulary.

## Building

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # lint
```

Requires JDK 17+ and the Android SDK (platform 35). CI runs the same
three tasks on every push (`.github/workflows/android.yml`).

## Project layout

```
app/src/main/java/com/classicbookreader/app/
├── MainActivity.kt / ClassicBookReaderApp.kt
├── navigation/          # NavHost + routes
├── core/                # pure-JVM logic (cache, selection geometry, …)
├── data/                # Room, DataStore, PDF, analysis pipeline
├── ui/theme/            # Tegas Glass tokens: Color, Type, Dimens, Theme
├── ui/components/       # GlassCard, PillButton, GlassDock, AsyncView, …
└── feature/<name>/      # one package per feature (home, library, reader, ...)
backend/                 # /v1/analyze SSE service (Node, zero-dependency)
```

Phase status: **Phase 3 (interlinear translation) complete** — on top of
the Phase 2 AI pipeline: a translate toggle in the reader renders the page
as flowing RTL rows of word chips (Arabic + Indonesian gloss), translated
per page on explicit request only (cost control), streamed with live word
counts, cached in Room per (book, page) with a "Terjemahkan ulang" escape
hatch, adjustable text size, and tap-any-word to open the Phase 2 analysis
sheet. `backend/` gained the matching `/v1/page-translate` SSE endpoint on
a shared model-streaming core. Earlier phases: circle-to-analyze mode with
streaming analysis sheet (Cara Baca / I'rob / Shorof / Arti), saved words,
PDF import/library/reader. Blank backend URL runs built-in demo responses
so the whole UX works without a server; the API key lives only in the
backend.
