# Rencana Implementasi — Baca Kitab Gundul

**Revisi v2 (Kotlin-only).** Keputusan 26 Agu 2026: aplikasi dibangun
**native Android penuh dengan Kotlin + Jetpack Compose** — tanpa Flutter,
tanpa jembatan lintas-framework. iOS ditunda sampai v1 Android terbukti
(nantinya app Swift kecil terbatas: reader + Share Extension — mode global
memang tidak mungkin di iOS).

Acuan visual: canvas mockup 15 layar di `design/mockups/` (gaya terkunci:
indigo modern-fun, `#6C5CE7` + `#FDCB6E`, Plus Jakarta Sans + Amiri).

## 1. Ringkasan

Aplikasi Android untuk membaca ebook/PDF kitab Arab gundul dengan dua mode:

1. **Mode dalam aplikasi** — reader PDF sendiri: lingkari kata → popup
   analisis (cara baca, i'rob, shorof, arti) + terjemah interlinear per kata.
2. **Mode global** — bubble mengambang di atas aplikasi lain (Qur'an
   digital, pembaca PDF lain): bekukan layar → lingkari → analisis, dengan
   deteksi otomatis judul kitab/halaman dan pencocokan mushaf untuk ayat.

Prinsip arsitektur inti: **satu pipeline "gambar → AI multimodal"** dipakai
kedua mode. Tidak ada OCR terpisah — potongan gambar (seleksi + margin
konteks) dikirim ke Claude yang sekaligus membaca, memberi harakat,
menganalisis nahwu-shorof, dan menerjemahkan.

Keuntungan utama Kotlin-only untuk aplikasi ini: fitur pembeda (mode
global — overlay, foreground service, MediaProjection) memang wilayah
native Android; satu bahasa dari UI sampai service menghapus seluruh
lapisan jembatan dan titik rapuhnya.

## 2. Arsitektur Tingkat Tinggi

```
┌────────────────────── Perangkat Android (Kotlin) ──────────────────────┐
│  Jetpack Compose UI                     Service & sistem                │
│  ├─ 15 layar (acuan mockup)             ├─ Foreground service           │
│  ├─ Reader PDF (PdfRenderer/Pdfium)     ├─ Bubble overlay               │
│  ├─ Kanvas lingkari (Canvas Compose)    │  (SYSTEM_ALERT_WINDOW)        │
│  ├─ MVVM: ViewModel + StateFlow         ├─ MediaProjection              │
│  └─ Room (SQLite) + DataStore           │  (tangkap layar per-permintaan)│
│        — satu proses, tanpa jembatan lintas-framework —                 │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ HTTPS (token pengguna, OkHttp + SSE)
                   ┌────────────▼────────────┐
                   │  Backend proxy (ringan)  │  ← ANTHROPIC_API_KEY hanya
                   │  /analyze /page /detect  │    di sini, tidak pernah
                   │  auth + kuota + cache    │    ada di APK
                   └────────────┬────────────┘
                                │ Anthropic SDK (streaming, structured output)
                        ┌───────▼────────┐
                        │   Claude API    │
                        └────────────────┘
```

Keputusan penting:

- **API key tidak pernah tertanam di aplikasi.** Semua panggilan AI lewat
  backend proxy kecil — tempat kuota, cache bersama, dan telemetri.
- **Satu bahasa ujung-ke-ujung (opsional).** Backend direkomendasikan
  **Ktor (Kotlin) + Anthropic Java SDK** agar seluruh proyek satu bahasa;
  alternatif setara: Node.js (Fastify) + `@anthropic-ai/sdk` bila infra
  tim lebih siap untuk itu. Kontrak API (§5) sama untuk keduanya.

## 3. Pilihan Teknologi

| Lapisan | Pilihan | Alasan |
| ------- | ------- | ------ |
| Bahasa & UI | Kotlin 2.x + Jetpack Compose (Material 3, tema kustom) | Native penuh; mode global tanpa jembatan; ekosistem Android resmi |
| Arsitektur | MVVM — ViewModel + StateFlow/Flow, satu module dulu, paket per fitur (`feature/reader`, `feature/analysis`, `feature/global`, `core/ui`, `core/data`) | Sederhana untuk 1 pengembang; dipecah multi-module bila membesar |
| DI | Hilt | Standar Android, ringan dipakai di service + ViewModel |
| Navigasi | Navigation Compose | Deklaratif, deep-link siap |
| Render PDF | `android.graphics.pdf.PdfRenderer` (bawaan) → render halaman ke `Bitmap`; fallback `pdfium-android` bila kualitas/perf kurang | Bitmap halaman dipakai ganda: tampilan reader & crop input AI |
| Zoom/scroll reader | Compose custom (`Pager` + transformable) | Kontrol penuh gesture — penting karena kanvas lingkari menumpang di atasnya |
| DB lokal | Room (SQLite) + DataStore untuk preferensi | Perpustakaan kitab, progres, kata tersimpan, cache analisis |
| Jaringan | OkHttp + Retrofit + kotlinx.serialization; SSE (OkHttp EventSource) untuk streaming hasil analisis | Streaming = harakat tampil duluan sebelum i'rob lengkap |
| Bubble overlay | `WindowManager` + `SYSTEM_ALERT_WINDOW`, Compose di `ComposeView` overlay | Bubble + jendela lingkari di atas app lain |
| Tangkap layar | MediaProjection API | Satu-satunya jalur resmi; persetujuan pengguna per sesi |
| Backend proxy | Ktor + Anthropic Java SDK (rekomendasi) / Node.js + SDK TS (alternatif) | Endpoint tipis; mudah di VPS yang ada |
| Model AI | `claude-opus-5` (bawaan; kualitas i'rob terbaik) | Multimodal, structured outputs, streaming. §7 untuk biaya & opsi hemat |
| Data mushaf | Dataset teks Qur'an kanonik (mis. Tanzil/QUL) di backend | Pencocokan ayat → harakat & terjemah resmi, bukan tebakan AI |
| Font | Amiri (Arab) + Plus Jakarta Sans (UI) dibundel di `res/font` | Sesuai design token mockup |
| Target OS | minSdk 26 (Android 8), target terbaru | MediaProjection & overlay stabil; cakupan perangkat pesantren luas |

## 4. Fase Implementasi

### Fase 0 — Fondasi proyek (±3 hari)

- Scaffold proyek (`com.bacakitabgundul.app`), Gradle version catalog,
  Hilt, Navigation Compose.
- Design token dari mockup sebagai tema Compose: `AppColors` (`#6C5CE7`,
  `#FDCB6E`, skala slate), `AppTypography` (Plus Jakarta Sans + Amiri di
  `res/font`), spacing xs–xl, radius 14/18/24; dukungan `darkColorScheme`
  disiapkan sejak awal (acuan arsip "Gaya Malam").
- Komponen bersama: `AppBottomSheet` (grabber + radius 28), `TabPills`,
  `PrimaryButton`, `AsyncView` (loading/error/empty/content).
- CI GitHub Actions: `lint` + unit test + `assembleDebug`.

### Fase 1 — Reader inti (layar 1–4, 10 sebagian) (±1,5 minggu)

- Import PDF via SAF (`OpenDocument`) → salin ke direktori app + baris
  Room (judul, cover render halaman 1, jumlah halaman, progres).
- Perpustakaan (layar 2), onboarding (1), tambah kitab (3).
- Reader (layar 4): render `PdfRenderer` per halaman dengan cache bitmap
  ±2 halaman, zoom/scroll, bookmark, lanjut-baca; bubble AI in-app
  (composable `Box` overlay, belum berfungsi).
- **Verifikasi:** buka 3 PDF kitab nyata (Jurumiyah, Fathul Qorib, kitab
  scan), scroll mulus di perangkat kelas menengah, memori stabil.

### Fase 2 — Pipeline AI dalam aplikasi (layar 5–8, 10) (±2 minggu)

- Mode AI (layar 5): tekan bubble → overlay glow + redup (Compose murni —
  halaman milik sendiri, tidak perlu tangkap layar).
- Gesture lingkari (layar 6): `pointerInput` + `Canvas`; bounding box
  seleksi dalam koordinat halaman.
- Crop gambar: render ulang area pada ~200 dpi → potong bbox + margin
  konteks (±15% lebar halaman) → JPEG ≤ ~300 KB.
- `POST /analyze` (SSE) → bottom sheet bertab (layar 7–8), harakat
  ditampilkan begitu chunk pertama tiba.
- Simpan kata (layar 10) + **cache analisis** di Room dengan kunci
  `(kitabId, halaman, hashBbox)` — lingkaran sama tidak bayar dua kali.
- **Verifikasi:** 20 frasa uji dari Jurumiyah dicek guru/santri — target
  ≥90% harakat benar, i'rob masuk akal; latensi first-token < 4 dtk.

### Fase 3 — Terjemah per kata interlinear (layar 9) (±1 minggu)

- `POST /page-translate`: gambar satu halaman → daftar kata (urutan RTL
  per baris) + gloss Indonesia + bbox normalisasi.
- Render interlinear: gloss digambar di bawah tiap baris pada kanvas
  reader (acuan layar 9), toggle on/off, slider ukuran.
- Cache per halaman (hash gambar); tombol "terjemahkan halaman" eksplisit
  — bukan otomatis — agar biaya terkendali.

### Fase 4 — Mode global (layar 12–15) (±2 minggu)

*(Lebih singkat dari rencana v1 — tidak ada lagi jembatan Flutter↔native.)*

- Foreground service + bubble `SYSTEM_ALERT_WINDOW` (izin runtime,
  layar 12), MediaProjection dengan persetujuan per sesi.
- Alur: ketuk bubble → tangkap 1 frame → Activity transparan menampilkan
  frame beku + glow + kanvas lingkari (layar 14) — komponen kanvas yang
  sama dengan Fase 2 — → crop → pipeline `/analyze` yang sama.
- Deteksi konteks: tangkapan pertama per aplikasi/sesi → `POST /detect` →
  `{judulKitab, bab, halaman, keyakinan}` → kartu "kitab terdeteksi";
  di-cache dan dikirim sebagai konteks analisis berikutnya.
- Jalur Qur'an (layar 15): bila `/detect` mengenali teks Qur'an, backend
  mencocokkan ke dataset mushaf → surah:ayat + teks berharakat kanonik +
  terjemah Kemenag; banner "dicocokkan dengan mushaf resmi".
- Kepatuhan Play Store: deklarasi izin overlay & MediaProjection dengan
  alur dalam-app jelas; **tanpa AccessibilityService**; indikator saat
  menangkap; tangkapan tidak disimpan.
- **Verifikasi:** uji di atas 3 aplikasi pihak ketiga (app Qur'an, pembaca
  PDF, browser) pada Android 10–15; bubble bertahan dari battery saver
  (prompt whitelist per OEM).

### Fase 5 — Poles & rilis (±1,5 minggu)

- Pengaturan (layar 11): ukuran font, transliterasi on/off, panjang
  konteks, bahasa terjemahan; tema gelap penuh.
- Kuota harian gratis + langganan (opsional, keputusan produk terpisah).
- Uji lapangan santri/guru, perbaikan, listing Play Store, rilis internal
  → produksi bertahap.

## 5. Kontrak API Backend

*(Tidak berubah dari v1 — netral terhadap bahasa backend.)*

Semua endpoint menerima `Authorization: Bearer <token pengguna>` (anonim
dulu, akun menyusul), rate-limit per perangkat, dan memakai Anthropic SDK
resmi di sisi server (streaming + `output_config.format` untuk JSON
terjamin skema).

```
POST /v1/analyze
  body : { image: <jpeg base64>,        // seleksi + margin konteks
           selectionBbox: {x,y,w,h},    // relatif terhadap image
           kitabContext?: { judul, bab, halaman },  // dari /detect atau metadata kitab
           opsi: { transliterasi: bool, bahasaArti: "id" } }
  hasil (streaming, JSON sesuai skema):
         { teksTerpilih, teksBerharakat, transliterasi,
           konteksSebelum, konteksSesudah,            // teks yang ikut dibaca
           kata: [ { arab, harakat, translit, arti,
                     irob: { kedudukan, alasan, tanda },
                     shorof: { akarKata, wazan, bentuk } } ],
           artiFrasa, tingkatKeyakinan }

POST /v1/page-translate
  body : { image: <jpeg halaman penuh>, kitabContext? }
  hasil: { baris: [ { kata: [ { arab, gloss, bbox } ] } ] }

POST /v1/detect
  body : { image: <jpeg layar/halaman penuh> }
  hasil: { jenis: "kitab" | "quran" | "lainnya",
           kitab?: { judul, bab, halaman, keyakinan },
           quran?: { surah, ayatMulai, ayatSelesai } }
           // jenis "quran" → backend lampirkan teks mushaf kanonik + terjemah
```

Strategi prompt: sistem prompt tetap (dimanfaatkan prompt caching — konten
stabil di depan), instruksi eksplisit "analisis kata yang dilingkari DENGAN
mempertimbangkan ±5 kata sebelum dan sesudah pada gambar"; untuk
`jenis: "quran"` teks kanonik dari dataset ikut dikirim sehingga model
menyelaraskan, bukan menebak.

## 6. Skema Data Lokal (Room)

- `kitab(id, judul, pathFile, jmlHalaman, halamanTerakhir, coverPath, dibuat)`
- `kata_tersimpan(id, kitabId?, sumberGlobal?, arab, harakat, translit,
  arti, irobJson, shorofJson, halaman, dibuat, statusHafal)`
- `cache_analisis(kunciHash, responsJson, dibuat)` — kunci =
  sha256(kitabId|halaman|bboxNormalisasi) atau sha256(gambar) di mode global
- `cache_halaman_terjemah(kitabId, halaman, responsJson, dibuat)`
- Preferensi (ukuran font, transliterasi, panjang konteks, tema) di
  DataStore.

## 7. Model AI & Estimasi Biaya

Bawaan: **`claude-opus-5`** — kualitas penalaran nahwu/i'rob terbaik
(input $5 / output $25 per juta token). Perkiraan kasar per permintaan:

| Operasi | Perkiraan token | Perkiraan biaya (opus-5) |
| ------- | --------------- | ------------------------ |
| Analisis lingkaran (crop kecil + JSON) | ~1.500 masuk / ~700 keluar | ± $0,025 |
| Terjemah 1 halaman penuh | ~2.000 masuk / ~2.500 keluar | ± $0,07 |
| Deteksi kitab (1× per sesi/app) | ~1.800 masuk / ~150 keluar | ± $0,013 |

Cache analisis lokal + cache backend menekan pemakaian ulang menjadi $0.
Bila biaya per pengguna perlu ditekan (keputusan pemilik produk, bukan
default): `claude-sonnet-5` ($2/$10, ±40% biaya) atau `claude-haiku-4-5`
($1/$5) khusus `/detect` dan `/page-translate`, dengan opus-5 tetap untuk
analisis i'rob. Prompt caching memangkas biaya sistem prompt berulang.

## 8. Risiko & Mitigasi

| Risiko | Mitigasi |
| ------ | -------- |
| Tim lebih terbiasa Flutter daripada Compose | Fase 0–1 sekaligus jadi masa adaptasi (fitur CRUD standar); pola MVVM+Flow mirip controller yang sudah dikenal; code review ketat di 2 fase awal |
| Kualitas baca AI pada kitab scan buram | Uji Fase 2 pakai scan nyata; pra-proses kontras; tampilkan `tingkatKeyakinan` + ajakan foto ulang |
| Latensi analisis terasa lambat | Streaming SSE (harakat duluan), crop kecil, cache, indikator progres bermakna |
| Kebijakan Play Store (overlay + MediaProjection) | Alur izin eksplisit (layar 12), tanpa AccessibilityService, deklarasi listing; rilis internal dulu |
| Biaya AI membengkak | Kuota harian, cache dua lapis, terjemah halaman eksplisit, pilihan model per endpoint |
| iOS belum tergarap | Disengaja: v1 fokus Android; iOS menyusul sebagai app Swift kecil (reader + Share Extension) setelah produk terbukti |
| Bubble dimatikan battery saver (OEM tertentu) | Foreground service + panduan whitelist per merek di layar 12 |

## 9. Urutan Pengerjaan & Perkiraan Waktu

Total ± 8–8,5 minggu efektif (1 pengembang + peninjau):

1. Fase 0 (3 hari) → 2. Fase 1 (1,5 mgg) → 3. Fase 2 (2 mgg) →
4. Fase 3 (1 mgg) → 5. Fase 4 (2 mgg) → 6. Fase 5 (1,5 mgg)

Rilis bertahap: **v0.1 internal** setelah Fase 2 (reader + analisis
lingkaran — nilai inti sudah terasa), **v0.2** setelah Fase 3, **v1.0**
setelah Fase 4–5.

## 10. Verifikasi Menyeluruh

- Setiap fase punya kriteria uji di atas; tambahan lintas-fase: lint +
  unit test hijau di CI, uji manual di 3 perangkat (low/mid/flagship),
  dan uji akurasi berkala: 50 frasa emas (Jurumiyah + Fathul Qorib +
  5 ayat) dengan jawaban kunci dari guru — dijalankan tiap ganti
  prompt/model di backend.

## Riwayat keputusan

- **v2 (26 Agu 2026):** Kotlin-only Android (Jetpack Compose) — dipilih
  karena fitur pembeda (mode global) sepenuhnya wilayah native dan
  menghapus jembatan lintas-framework; iOS ditunda pasca-v1.
- **v1:** Flutter + modul native Kotlin (diarsipkan oleh revisi ini).
