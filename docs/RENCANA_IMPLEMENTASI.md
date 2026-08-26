# Rencana Implementasi — Baca Kitab Gundul

**Revisi v2 (Kotlin-only).** Keputusan 26 Agu 2026: aplikasi dibangun
**native Android penuh dengan Kotlin + Jetpack Compose** — tanpa Flutter,
tanpa jembatan lintas-framework. iOS ditunda sampai v1 Android terbukti
(nantinya app Swift kecil terbatas: reader + Share Extension — mode global
memang tidak mungkin di iOS).

Acuan visual: canvas mockup 18 layar di `design/mockups/` — gaya terkunci
**"Tegas Glass"**: krem `#F4F3EE`, tinta `#1C1B16`, hijau `#1E5C44`,
amber `#F0A63A`, Figtree + Amiri, panel kaca buram (blur) dengan bayangan
lembut, rail tab vertikal RTL, dock navigasi kaca, gamifikasi istiqomah
(detail di `design/mockups/README.md`).

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
- **Backend Ktor (Kotlin) dengan lapisan AI model-agnostic.** Klien AI
  memakai antarmuka kompatibel-OpenAI + adaptor per penyedia; model untuk
  tiap endpoint ditentukan **konfigurasi backend** — ganti Gemini ↔ Qwen ↔
  Claude tanpa rilis aplikasi. Strategi pemilihan model di §7.

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
| Backend proxy | Ktor (Kotlin); lapisan AI model-agnostic (klien kompatibel-OpenAI + adaptor per penyedia) | Endpoint tipis; model per endpoint via konfigurasi |
| Model AI | **Gemini 2.5 Flash (bawaan hemat)**; Qwen-VL kandidat A/B; Claude Sonnet 5 pembanding & jalur eskalasi | Multimodal, JSON terjamin skema, streaming. Detail & gerbang kualitas di §7 |
| Data mushaf | Dataset teks Qur'an kanonik (mis. Tanzil/QUL) di backend | Pencocokan ayat → harakat & terjemah resmi, bukan tebakan AI |
| Font | Amiri (Arab) + Plus Jakarta Sans (UI) dibundel di `res/font` | Sesuai design token mockup |
| Target OS | minSdk 26 (Android 8), target terbaru | MediaProjection & overlay stabil; cakupan perangkat pesantren luas |

## 4. Fase Implementasi

### Fase 0 — Fondasi proyek (±3 hari)

- Scaffold proyek (`com.bacakitabgundul.app`), Gradle version catalog,
  Hilt, Navigation Compose.
- Design token "Tegas Glass" sebagai tema Compose: `AppColors`
  (`#F4F3EE`, `#1C1B16`, `#1E5C44`, `#F0A63A`), `AppTypography`
  (Figtree + Amiri di `res/font`), komponen bersama (`GlassCard` —
  permukaan translusen + haze/blur via `Modifier.graphicsLayer`/
  RenderEffect di API 31+, fallback permukaan solid semi-transparan di
  bawahnya, `PillButton` hijau, `RailTab` vertikal kaca, `GlassDock`
  navigasi, `StreakBadge`), radius 18–30; `darkColorScheme` disiapkan
  sejak awal.
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

### Fase 3b — Wawasan & referensi silang (layar 16) (±3 hari)

- Tab kelima **"Wawasan"** di bottom sheet analisis, berisi kartu:
  - **Keluarga kata** — derivasi populer dari akar yang sama (memperluas
    kosakata; melengkapi tab Shorof).
  - **Di Al-Qur'an** — jumlah kemunculan akar + contoh ayat berharakat.
    **Setiap kutipan divalidasi backend ke dataset mushaf kanonik**
    sebelum tampil — AI mengusulkan, backend memverifikasi; kutipan yang
    gagal validasi dibuang.
  - **Tahukah kamu** — fun fact, termasuk kata serapan Indonesia
    (كتاب → "kitab", لفظ → "lafal").
  - **Faidah** — catatan nahwu/balaghah kontekstual, berlabel
    "penjelasan AI".
  - **Referensi silang kitab** — "topik ini dibahas juga di …" menunjuk
    kitab lain yang membahas bab yang sama (mis. dari Jurumiyah →
    Mutammimah, Qatrun Nada, Alfiyah). Hanya boleh menunjuk kitab dari
    daftar rujukan terkurasi (§5b). Kelak: bila kitab rujukan ada di
    perpustakaan pengguna, tautan langsung membuka halamannya.
- **Lazy-load**: konten diminta via `POST /enrich` hanya saat tab dibuka —
  latensi & biaya popup utama tidak bertambah.
- **Cache per akar kata, global lintas pengguna** di backend: wawasan akar
  ك-ت-ب dihasilkan sekali, semua pengguna berikutnya membaca dari cache.
- **Verifikasi:** 15 kata uji — 100% kutipan Qur'an lolos validasi mushaf,
  referensi silang hanya dari daftar kurasi, fun fact dicek manual.

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

- Gamifikasi istiqomah (layar 17–18): streak harian, sesi muroja'ah
  flashcard dari kata tersimpan (Ingat / Masih Lupa), rekap perayaan;
  kata yang lupa dijadwalkan muncul lagi besok (penjadwalan ulang
  sederhana — bukan algoritma SRS penuh dulu).
- Pengaturan (layar 11): ukuran font, transliterasi on/off, panjang
  konteks, bahasa terjemahan; tema gelap penuh.
- Kuota harian gratis + langganan (opsional, keputusan produk terpisah).
- Uji lapangan santri/guru, perbaikan, listing Play Store, rilis internal
  → produksi bertahap.

## 5. Kontrak API Backend

*(Tidak berubah dari v1 — netral terhadap bahasa backend.)*

Semua endpoint menerima `Authorization: Bearer <token pengguna>` (anonim
dulu, akun menyusul), rate-limit per perangkat, dan lapisan AI model-agnostic: model per
endpoint dari konfigurasi, respons dipaksa JSON sesuai skema (fitur
structured-output penyedia masing-masing) dan streaming.

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

POST /v1/enrich                       // dipanggil lazy saat tab Wawasan dibuka
  body : { kata, akarKata?, konteksKalimat?, kitabContext? }
  hasil: { keluargaKata: [ { arab, harakat, arti } ],
           quran: { jumlahKemunculanAkar,
                    contoh: [ { surah, ayat, potongan, terjemah } ] },
                    // ↑ divalidasi ke dataset mushaf sebelum dikirim
           tahukahKamu?, faidah?,
           referensiSilang: [ { kitab, bagian, keterangan } ],
                    // ↑ hanya kitab dari daftar rujukan terkurasi (§5b)
           }
  cache : per akarKata — global lintas pengguna (dihasilkan sekali)

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

### 5b. Kebijakan konten keagamaan (manhaj rujukan)

Seluruh kandungan keagamaan yang dihasilkan aplikasi mengikuti **Ahlus
Sunnah wal Jama'ah dengan pemahaman salafush-shalih**. Ini diterapkan
secara teknis, bukan sekadar niat:

1. **Sistem prompt** semua endpoint menegaskan manhaj rujukan dan melarang
   mengutip pendapat di luar rujukan mu'tabar Ahlus Sunnah.
2. **Daftar putih kitab rujukan terkurasi** per bidang, disimpan sebagai
   konfigurasi backend yang dikelola pemilik produk (bisa diperbarui tanpa
   rilis aplikasi). Contoh awal — final ditetapkan pemilik produk bersama
   ustadz pembina:
   - Nahwu/shorof: Al-Ajurumiyyah, Mutammimah, Qatrun Nada, Alfiyah Ibnu
     Malik beserta syarah mu'tabar-nya.
   - Tafsir: Tafsir Ibnu Katsir, Tafsir As-Sa'di.
   - Hadits: kutubus sittah dengan syarah mu'tabar.
   - Aqidah & fiqih: kitab-kitab rujukan salaf yang ditetapkan pembina.
   Kartu **referensi silang** dan **faidah** hanya boleh menunjuk/mengutip
   dari daftar ini.
3. **Validasi kutipan**: ayat dicek ke dataset mushaf; kutipan hadits
   (bila kelak ditampilkan) dicek ke korpus hadits sebelum tampil —
   yang tidak terverifikasi dibuang, bukan "dikira-kira".
4. **Kejujuran ke pengguna**: konten hasil AI berlabel "penjelasan AI",
   dan ada tombol **"laporkan koreksi"** di tiap kartu wawasan — laporan
   masuk ke antrean tinjauan.
5. **Tinjauan berkala ustadz**: sampel keluaran wawasan & faidah ditinjau
   ustadz pembina tiap siklus rilis (digabung dengan uji 50 frasa emas).

Catatan (26 Agu 2026): kebijakan ini **diterapkan sepenuhnya di backend**
dan tidak ditampilkan sebagai keterangan di UI aplikasi — di UI hanya ada
label "Penjelasan AI" dan tombol laporkan.

## 6. Skema Data Lokal (Room)

- `kitab(id, judul, pathFile, jmlHalaman, halamanTerakhir, coverPath, dibuat)`
- `kata_tersimpan(id, kitabId?, sumberGlobal?, arab, harakat, translit,
  arti, irobJson, shorofJson, halaman, dibuat, statusHafal)`
- `cache_analisis(kunciHash, responsJson, dibuat)` — kunci =
  sha256(kitabId|halaman|bboxNormalisasi) atau sha256(gambar) di mode global
- `cache_halaman_terjemah(kitabId, halaman, responsJson, dibuat)`
- `cache_wawasan(akarKata, responsJson, dibuat)` — salinan lokal dari
  cache global backend
- Preferensi (ukuran font, transliterasi, panjang konteks, tema) di
  DataStore.

## 7. Strategi Model AI & Estimasi Biaya (hemat dulu)

**Keputusan (26 Agu 2026): mulai dari model termurah yang lolos gerbang
kualitas.** Model dipilih **per endpoint lewat konfigurasi backend**
(lapisan model-agnostic, §5) — mengganti penyedia/model tidak butuh rilis
aplikasi.

- **Bawaan awal: Gemini 2.5 Flash** ($0,30 masuk / $2,50 keluar per juta
  token) untuk semua endpoint — vision & multibahasa kuat di kelas harga
  ini, satu penyedia, free tier untuk masa pengembangan.
- **Kandidat A/B: Qwen-VL** (DashScope — cek harga vision terkini) bila
  lolos uji dan lebih murah.
- **Pembanding atas & jalur eskalasi: Claude Sonnet 5** ($2/$10) — dipakai
  di uji banding, dan sebagai tombol "analisis mendalam" / fitur premium
  bila model hemat gagal di kasus i'rob rumit.
- **`/enrich` (Wawasan) tetap pakai model kuat** (Sonnet 5): dihasilkan
  sekali per akar kata untuk semua pengguna, jadi biayanya amortisasi ke
  nyaris nol — tidak ada alasan berhemat di sana.

| Operasi | Perkiraan token | Biaya (Gemini 2.5 Flash) |
| ------- | --------------- | ------------------------ |
| Analisis lingkaran (crop kecil + JSON) | ~1.500 masuk / ~700 keluar | ± $0,002 |
| Terjemah 1 halaman penuh | ~2.000 masuk / ~2.500 keluar | ± $0,007 |
| Deteksi kitab (1× per sesi/app) | ~1.800 masuk / ~150 keluar | ± $0,001 |
| Wawasan per akar (1× seumur hidup, Sonnet 5) | ~1.200 masuk / ~900 keluar | ± $0,012 sekali → gratis untuk semua pengguna berikutnya |

Skenario pengguna aktif (10 lingkaran + 2 halaman/hari): **± $0,8/bulan**
(vs ± $3,9 dengan campuran Sonnet) — belum termasuk potongan cache dua
lapis, yang membuat permintaan berulang jadi $0.

**Gerbang kualitas (wajib sebelum kunci model):** uji 50 frasa emas +
beberapa kasus aqidah/fiqih dijalankan di Gemini Flash, Qwen-VL, dan
Sonnet 5 sebagai pembanding; pakai yang termurah yang masih memenuhi
ambang ustadz pembina (≥90% harakat benar, i'rob masuk akal, moderasi
konten keagamaan konsisten). Uji yang sama diulang tiap ganti model/prompt.

**Catatan kebijakan data:** pakai tier berbayar Google dengan jaminan data
tidak dipakai training; bila Qwen dipakai, tangkapan layar transit ke
server di China — wajib dicantumkan di kebijakan privasi aplikasi.

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
| Konten keagamaan keliru / di luar manhaj | Kebijakan §5b: daftar putih kitab, validasi kutipan ke korpus, label "penjelasan AI", tombol laporkan, tinjauan ustadz berkala |
| Kualitas model hemat di bawah ambang | Gerbang uji 50 frasa emas sebelum model dikunci; eskalasi per-kasus ke Sonnet 5 ("analisis mendalam"); ganti model hanya soal konfigurasi |

## 9. Urutan Pengerjaan & Perkiraan Waktu

Total ± 9 minggu efektif (1 pengembang + peninjau):

1. Fase 0 (3 hari) → 2. Fase 1 (1,5 mgg) → 3. Fase 2 (2 mgg) →
4. Fase 3 (1 mgg) → 5. Fase 3b (3 hari) → 6. Fase 4 (2 mgg) →
7. Fase 5 (1,5 mgg)

Rilis bertahap: **v0.1 internal** setelah Fase 2 (reader + analisis
lingkaran — nilai inti sudah terasa), **v0.2** setelah Fase 3–3b, **v1.0**
setelah Fase 4–5.

## 10. Verifikasi Menyeluruh

- Setiap fase punya kriteria uji di atas; tambahan lintas-fase: lint +
  unit test hijau di CI, uji manual di 3 perangkat (low/mid/flagship),
  dan uji akurasi berkala: 50 frasa emas (Jurumiyah + Fathul Qorib +
  5 ayat) dengan jawaban kunci dari guru — dijalankan tiap ganti
  prompt/model di backend.

## Riwayat keputusan

- **v2.4 (26 Agu 2026):** Gaya final "Tegas Glass" — identitas Tegas Ceria
  dihaluskan ala Apple (panel kaca buram, Figtree sentence case, bayangan
  lembut) demi keterbacaan & kesan profesional; kartu streak istiqomah
  di-redesign; keterangan manhaj dihapus dari UI (backend-only, §5b).
- **v2.3 (26 Agu 2026):** Gaya visual final "Tegas Ceria" (menggantikan
  indigo modern-fun) diterapkan ke seluruh mockup; tambah layar 17–18
  (muroja'ah + rekap streak istiqomah) melengkapi putaran gamifikasi.
- **v2.2 (26 Agu 2026):** Strategi model "hemat dulu" — Gemini 2.5 Flash
  bawaan semua endpoint, lapisan AI model-agnostic di backend, Qwen-VL
  kandidat A/B, Claude Sonnet 5 pembanding atas & jalur eskalasi;
  keputusan final model lewat gerbang uji 50 frasa emas.
- **v2.1 (26 Agu 2026):** Tambah fitur Wawasan (tab kelima: keluarga kata,
  kemunculan di Al-Qur'an tervalidasi, fun fact, faidah, referensi silang
  antar kitab) + kebijakan konten §5b: seluruh rujukan keagamaan mengikuti
  Ahlus Sunnah wal Jama'ah dengan pemahaman salaf, ditegakkan lewat daftar
  putih kitab terkurasi dan validasi kutipan.
- **v2 (26 Agu 2026):** Kotlin-only Android (Jetpack Compose) — dipilih
  karena fitur pembeda (mode global) sepenuhnya wilayah native dan
  menghapus jembatan lintas-framework; iOS ditunda pasca-v1.
- **v1:** Flutter + modul native Kotlin (diarsipkan oleh revisi ini).
