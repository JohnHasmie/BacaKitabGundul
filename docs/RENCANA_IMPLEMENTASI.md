# Rencana Implementasi — Baca Kitab Gundul

Acuan visual: canvas mockup 15 layar di `design/mockups/` (gaya terkunci:
indigo modern-fun, `#6C5CE7` + `#FDCB6E`, Plus Jakarta Sans + Amiri).

## 1. Ringkasan

Aplikasi Android (Flutter) untuk membaca ebook/PDF kitab Arab gundul dengan
dua mode:

1. **Mode dalam aplikasi** — reader PDF sendiri: lingkari kata → popup
   analisis (cara baca, i'rob, shorof, arti) + terjemah interlinear per kata.
2. **Mode global** — bubble mengambang di atas aplikasi lain (Qur'an
   digital, pembaca PDF lain): bekukan layar → lingkari → analisis, dengan
   deteksi otomatis judul kitab/halaman dan pencocokan mushaf untuk ayat.

Prinsip arsitektur inti: **satu pipeline "gambar → AI multimodal"** dipakai
kedua mode. Tidak ada OCR terpisah — potongan gambar (seleksi + margin
konteks) dikirim ke Claude yang sekaligus membaca, memberi harakat,
menganalisis nahwu-shorof, dan menerjemahkan.

## 2. Arsitektur Tingkat Tinggi

```
┌─────────────────────────── Perangkat Android ───────────────────────────┐
│  Flutter (Dart)                          Modul native Kotlin            │
│  ├─ UI 15 layar (acuan mockup)           ├─ Foreground service          │
│  ├─ Reader PDF (pdfrx)                   ├─ Bubble overlay              │
│  ├─ Gesture lingkari (CustomPainter)     │  (SYSTEM_ALERT_WINDOW)       │
│  ├─ State: Riverpod                      ├─ MediaProjection             │
│  └─ DB lokal: Drift (SQLite)             │  (tangkap layar per-permintaan)│
│            └────────── MethodChannel ────┘                              │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ HTTPS (token pengguna)
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
  backend proxy kecil. Ini juga tempat kuota, cache bersama, dan telemetri.
- **Modul native minimal.** Hanya 3 hal yang butuh Kotlin: bubble overlay,
  MediaProjection, dan foreground service. Sisanya Flutter murni sehingga
  reader in-app bisa dirilis lebih dulu tanpa modul native.

## 3. Pilihan Teknologi

| Lapisan | Pilihan | Alasan |
| ------- | ------- | ------ |
| UI mobile | Flutter (Dart 3), Android-first | Keahlian tim (KamilEdu); iOS menyusul terbatas (tanpa mode global) |
| Render PDF | `pdfrx` (berbasis pdfium) | Render halaman → gambar (input AI) + zoom/scroll performa baik |
| State | `flutter_riverpod` | Ringan, testable, pola sama dengan controller di proyek tim |
| Navigasi | `go_router` | Deklaratif, deep-link siap |
| DB lokal | `drift` (SQLite) | Perpustakaan kitab, progres baca, kata tersimpan, cache analisis |
| Bubble overlay | Kotlin + `flutter_overlay_window` sebagai rujukan pola | Bubble + jendela lingkari di atas app lain |
| Tangkap layar | MediaProjection API (Kotlin) | Satu-satunya jalur resmi; persetujuan pengguna per sesi |
| Backend proxy | Node.js (Fastify) + `@anthropic-ai/sdk`, atau Laravel bila ingin satu bahasa dengan infra tim | Endpoint tipis; mudah di-deploy di VPS yang sudah ada |
| Model AI | `claude-opus-5` (bawaan; kualitas i'rob terbaik) | Multimodal, structured outputs, streaming. Lihat §7 untuk biaya & opsi hemat |
| Data mushaf | Dataset teks Qur'an kanonik (mis. Tanzil/QUL) di backend | Pencocokan ayat → harakat & terjemah resmi, bukan tebakan AI |

## 4. Fase Implementasi

### Fase 0 — Fondasi proyek (±3 hari)

- Scaffold Flutter (`com.bacakitabgundul.app`), struktur folder
  `lib/features/<fitur>/{presentation,data,domain}` meniru konvensi tim.
- Design token dari mockup: `AppColors` (`#6C5CE7`, `#FDCB6E`, skala slate),
  `AppTypography` (Plus Jakarta Sans UI, Amiri untuk Arab — bundel font di
  assets), spacing xs–xl, radius 14/18/24.
- Komponen bersama: `AppBottomSheet` (grabber + radius 28), `TabPills`,
  `PrimaryButton`, `AsyncView` (loading/error/empty/content).
- CI GitHub Actions: `flutter analyze` + `flutter test` + build APK debug.

### Fase 1 — Reader inti (layar 1–4, 10 sebagian) (±1,5 minggu)

- Import PDF dari file picker → simpan ke direktori app + baris Drift
  (judul, cover render halaman 1, jumlah halaman, progres).
- Perpustakaan (layar 2), onboarding (1), tambah kitab (3).
- Reader `pdfrx` (layar 4): halaman, bookmark, lanjut-baca; floating AI
  bubble in-app (widget `Stack`, belum fungsi).
- **Verifikasi:** buka 3 PDF kitab nyata (Jurumiyah, Fathul Qorib, kitab
  scan), scroll mulus di perangkat kelas menengah.

### Fase 2 — Pipeline AI dalam aplikasi (layar 5–8, 10) (±2 minggu)

- Mode AI (layar 5): tekan bubble → overlay glow + redup (Flutter, tanpa
  native — halaman reader milik sendiri, tidak perlu tangkap layar).
- Gesture lingkari (layar 6): `GestureDetector` + `CustomPainter`; hitung
  bounding box seleksi di koordinat halaman.
- Crop gambar: render halaman via pdfrx pada ~200 dpi → potong bbox +
  margin konteks (±15% lebar halaman di sekeliling) → JPEG ≤ ~300 KB.
- Backend `POST /analyze` (lihat §5) → bottom sheet bertab (layar 7–8),
  streaming supaya harakat muncul duluan sebelum i'rob lengkap.
- Simpan kata (layar 10) + **cache analisis** di Drift dengan kunci
  `(kitabId, halaman, hashBbox)` — lingkaran yang sama tidak bayar dua kali.
- **Verifikasi:** 20 frasa uji dari Jurumiyah dicek guru/santri — target
  ≥90% harakat benar, i'rob masuk akal; latensi first-token < 4 dtk.

### Fase 3 — Terjemah per kata interlinear (layar 9) (±1 minggu)

- Backend `POST /page-translate`: kirim gambar satu halaman → hasil daftar
  kata (urutan RTL per baris) + gloss Indonesia + bbox normalisasi.
- Render interlinear: layer di bawah tiap baris Arab (acuan layar 9),
  toggle on/off, slider ukuran.
- Cache per halaman (`hash gambar halaman`); tombol "terjemahkan halaman"
  eksplisit — bukan otomatis — agar biaya terkendali.

### Fase 4 — Mode global Android (layar 12–15) (±2,5 minggu)

- Modul Kotlin: foreground service + bubble `SYSTEM_ALERT_WINDOW`
  (izin runtime, layar 12), MediaProjection dengan persetujuan per sesi.
- Alur: ketuk bubble → tangkap 1 frame → Activity transparan menampilkan
  frame beku + glow + kanvas lingkari (layar 14) → crop → pipeline
  `/analyze` yang sama.
- Deteksi konteks: pada tangkapan pertama per aplikasi/sesi, kirim frame
  penuh ke `POST /detect` → `{judulKitab, bab, halaman, keyakinan}` →
  kartu "kitab terdeteksi"; hasil di-cache dan dikirim sebagai konteks
  tambahan pada analisis berikutnya.
- Jalur Qur'an (layar 15): bila `/detect` mengenali teks Qur'an, backend
  mencocokkan ke dataset mushaf → surah:ayat + teks berharakat kanonik +
  terjemah Kemenag; UI menampilkan banner "dicocokkan dengan mushaf resmi".
- Kepatuhan Play Store: deklarasi izin overlay & MediaProjection dengan
  alur dalam-app yang jelas; **tidak memakai AccessibilityService**;
  indikator jelas saat menangkap; tangkapan tidak disimpan.
- **Verifikasi:** uji di atas 3 aplikasi pihak ketiga (app Qur'an, pembaca
  PDF, browser) pada Android 10–15; bubble bertahan setelah app di-kill
  oleh battery saver (whitelist prompt).

### Fase 5 — Poles & rilis (±1,5 minggu)

- Pengaturan (layar 11): ukuran font, transliterasi on/off, panjang
  konteks, bahasa terjemahan.
- Tema gelap (acuan arsip "Gaya Malam" di canvas), onboarding final,
  kuota harian gratis + langganan (opsional, keputusan produk terpisah).
- Uji lapangan santri/guru, perbaikan, listing Play Store, rilis internal
  → produksi bertahap.

## 5. Kontrak API Backend

Semua endpoint menerima `Authorization: Bearer <token pengguna>` (anonim
dulu, akun menyusul), menerapkan rate-limit per perangkat, dan memakai
Anthropic SDK resmi di sisi server (streaming + `output_config.format`
untuk JSON terjamin skema).

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

Strategi prompt: sistem prompt tetap (di-cache dengan prompt caching —
konten stabil di depan), instruksi menyebut eksplisit "analisis kata yang
dilingkari DENGAN mempertimbangkan ±5 kata sebelum dan sesudah pada gambar";
untuk `jenis: "quran"` teks kanonik dari dataset ikut dikirim sehingga model
tinggal menyelaraskan, bukan menebak.

## 6. Skema Data Lokal (Drift)

- `kitab(id, judul, pathFile, jmlHalaman, halamanTerakhir, coverPath, dibuat)`
- `kataTersimpan(id, kitabId?, sumberGlobal?, arab, harakat, translit, arti,
  irobJson, shorofJson, halaman, dibuat, statusHafal)`
- `cacheAnalisis(kunciHash, responsJson, dibuat)` — kunci =
  sha256(kitabId|halaman|bboxNormalisasi) atau sha256(gambar) di mode global
- `cacheHalamanTerjemah(kitabId, halaman, responsJson, dibuat)`

## 7. Model AI & Estimasi Biaya

Bawaan: **`claude-opus-5`** — kualitas penalaran nahwu/i'rob terbaik
(input $5 / output $25 per juta token). Perkiraan kasar per permintaan:

| Operasi | Perkiraan token | Perkiraan biaya (opus-5) |
| ------- | --------------- | ------------------------ |
| Analisis lingkaran (crop kecil + JSON) | ~1.500 masuk / ~700 keluar | ± $0,025 |
| Terjemah 1 halaman penuh | ~2.000 masuk / ~2.500 keluar | ± $0,07 |
| Deteksi kitab (1× per sesi/app) | ~1.800 masuk / ~150 keluar | ± $0,013 |

Cache analisis lokal + cache backend menekan pemakaian ulang menjadi $0.
Bila biaya per pengguna perlu ditekan, opsi yang tersedia (keputusan
pemilik produk, bukan default): `claude-sonnet-5` ($2/$10, ±40% biaya) atau
`claude-haiku-4-5` ($1/$5) khusus `/detect` dan `/page-translate`, dengan
opus-5 tetap untuk analisis i'rob. Prompt caching di backend memangkas
biaya sistem prompt yang berulang.

## 8. Risiko & Mitigasi

| Risiko | Mitigasi |
| ------ | -------- |
| Kualitas baca AI pada kitab scan buram | Uji Fase 2 pakai kitab scan nyata; pra-proses (kontras, upscale ringan); tampilkan `tingkatKeyakinan` + tombol "foto ulang lebih dekat" |
| Latensi analisis terasa lambat | Streaming (harakat tampil duluan), crop kecil, cache, indikator progres bermakna |
| Kebijakan Play Store (overlay + MediaProjection) | Alur izin eksplisit dalam app (layar 12), tanpa AccessibilityService, deklarasi di listing; rilis internal dulu |
| Biaya AI membengkak | Kuota harian, cache dua lapis, terjemah halaman eksplisit (bukan otomatis), pilihan model per endpoint |
| iOS tidak bisa mode global | Rilis iOS belakangan: reader in-app + Share Extension + import screenshot |
| Bubble dimatikan battery saver (OEM tertentu) | Foreground service + panduan whitelist per merek di layar 12 |

## 9. Urutan Pengerjaan & Perkiraan Waktu

Total ± 8–9 minggu efektif (1 pengembang + peninjau):

1. Fase 0 (3 hari) → 2. Fase 1 (1,5 mgg) → 3. Fase 2 (2 mgg) →
4. Fase 3 (1 mgg) → 5. Fase 4 (2,5 mgg) → 6. Fase 5 (1,5 mgg)

Rilis bertahap: **v0.1 internal** setelah Fase 2 (reader + analisis
lingkaran — nilai inti sudah terasa), **v0.2** setelah Fase 3, **v1.0**
setelah Fase 4–5.

## 10. Verifikasi Menyeluruh

- Setiap fase punya kriteria uji di atas; tambahan lintas-fase:
  `flutter analyze` + `flutter test` hijau di CI, uji manual di 3 perangkat
  (low/mid/flagship), dan uji akurasi berkala: 50 frasa emas (Jurumiyah +
  Fathul Qorib + 5 ayat) dengan jawaban kunci dari guru — dijalankan tiap
  ganti prompt/model di backend.
