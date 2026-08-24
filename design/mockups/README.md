# Mockup — Baca Kitab Gundul

Mockup high-fidelity 11 layar untuk aplikasi mobile pembaca ebook/PDF kitab
Arab gundul dengan fitur analisis AI "lingkari untuk memahami"
(circle-to-select ala Galaxy AI).

Setiap file `*.dc.html` adalah satu artboard mobile 390×844;
`canvas.json` mengatur tata letaknya di canvas desain.

## Alur layar

| # | File | Layar |
| - | ---- | ----- |
| 1 | `Onboarding.dc.html` | Perkenalan fitur lingkari-untuk-analisis |
| 2 | `Main.dc.html` | Beranda / perpustakaan kitab |
| 3 | `ImportKitab.dc.html` | Sheet tambah kitab (PDF, EPUB, pindai) |
| 4 | `ReaderNormal.dc.html` | Reader dengan tombol AI mengambang |
| 5 | `ModeAI.dc.html` | Mode AI aktif — glow tepi layar |
| 6 | `Melingkari.dc.html` | Gesture melingkari + highlight konteks ±5 kata |
| 7 | `AnalisisCaraBaca.dc.html` | Bottom sheet analisis, tab Cara Baca |
| 8 | `AnalisisIrob.dc.html` | Tab I'rob + peta kalimat per kata |
| 9 | `TerjemahPerKata.dc.html` | Terjemah interlinear per kata satu halaman |
| 10 | `KataTersimpan.dc.html` | Riwayat kata yang pernah dianalisis |
| 11 | `Pengaturan.dc.html` | Ukuran teks, transliterasi, panjang konteks AI |

## Design tokens

- Primer `#6C5CE7` (indigo), aksen `#FDCB6E` (kuning hangat),
  sukses `#00B894`, tinta `#241E38`, latar `#F7F5FB`,
  kertas reader `#FDFBF5`.
- Elemen AI memakai gradien `#6C5CE7 → #A55EEA`; glow mode lingkari
  memakai conic-gradient `#6C5CE7 → #C56CF0 → #FDCB6E → #56C7E8`.
- Tipografi: UI **Plus Jakarta Sans**, teks Arab **Amiri**
  (fallback Traditional Arabic, serif).
- Seluruh string UI Bahasa Indonesia; contoh teks memakai
  Matan Al-Ajurumiyyah (bab Kalam).

## Keputusan interaksi penting

- AI tidak hanya membaca kata yang dilingkari: **±5 kata sebelum dan
  sesudah** ikut dikirim sebagai konteks (di-highlight kuning pada
  layar 6, jumlahnya dapat diatur di Pengaturan).
- Hasil analisis berupa bottom sheet bertab:
  Cara Baca (harakat + transliterasi + audio) / I'rob / Shorof / Arti.
- Mode terjemah per kata menumpuk arti Indonesia langsung di bawah
  setiap kata Arab (interlinear), di-toggle dari toolbar reader.
