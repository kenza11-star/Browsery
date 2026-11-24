# Status Stage

Status jujur per stage. "VERIFIED build" = CI hijau (unit test, assembleDebug, lint) dengan bukti run/commit. **Belum ada satu pun stage yang diuji di perangkat atau emulator**, jadi tidak ada fitur yang berstatus "VERIFIED di perangkat".

| Stage | Topik | Status |
|---|---|---|
| 1 | Repo, arsitektur, bootstrap | VERIFIED build: `ce7cd72`. |
| 2 | GeckoView dan navigasi | IMPLEMENTED; build hijau, unit test URL. |
| 3 | UI Chrome-like, beranda | IMPLEMENTED; build hijau `79d3dce` (run 45084717946). |
| 4 | Multi-tab, history, bookmarks, private | IMPLEMENTED; logika tab diuji unit; Room/restore/isolasi privat belum diuji di perangkat. |
| 5 | Download manager + paralel | IMPLEMENTED; build hijau `20216ff` (run 45145145026); engine diuji terhadap server HTTP lokal. |
| 6 | Pengaturan | PARTIAL; build hijau `c6bd20a`. Banyak butir spesifikasi belum ada (lihat bawah). |
| 7 | Privasi, tracker blocking | IMPLEMENTED terbatas; build hijau `89eb220`. |
| 8 | WebExtensions | IMPLEMENTED terbatas (hanya ekstensi bawaan); build hijau `9ba1c32` (run 45250928664). |
| 9 | Akun Supabase + Google | IMPLEMENTED (email/kata sandi + Google lewat browser lain); build hijau `9c408ae` (job 138262036117). Login belum diuji terhadap server asli dari aplikasi. |
| 10 | Sinkronisasi cloud | IMPLEMENTED terbatas (hanya bookmark, aditif, opt-in); build hijau `aa16d1a` (job 138267476876); merge diuji unit. |
| 11 | Fitur lanjutan | PARTIAL: Cari di halaman + Situs desktop; build hijau `abfb188` (job 138270812587). |
| 12 | Performa lintas perangkat | PARTIAL: batas tab termuat menyesuaikan kelas memori perangkat (3/4/6/8, `TabLimits`, diuji unit); build hijau `c35dc8e` (job 138280088299). Tidak ada pengukuran performa, profil baseline, R8, atau uji tablet/foldable. |
| 13 | Pipeline rilis | PARTIAL: penandatanganan dari environment di `app/build.gradle.kts` (build hijau `462f9ee`); workflow rilis HANYA berupa `docs/release.yml.example` karena konektor menolak menulis `.github/workflows`. Belum pernah dijalankan; tidak ada APK bertanda tangan. |
| 14 | Regresi penuh + serah terima | PARTIAL: dokumen ini dan `HANDOFF.md`; regresi hanya berupa CI unit test/lint pada `c35dc8e`, tanpa uji perangkat. |

## Matriks fitur

| Fitur | Status |
|---|---|
| Navigasi, URL/pencarian, error page TLS tanpa bypass | IMPLEMENTED, belum diuji perangkat |
| Multi-tab, tab privat, restore tab, riwayat, bookmark | IMPLEMENTED, belum diuji perangkat |
| Batas tab termuat adaptif memori | IMPLEMENTED, rumus diuji unit; efek nyata belum diukur |
| Unduhan paralel/resume | IMPLEMENTED, diuji unit lokal |
| Perlindungan pelacak, HTTPS-only, GPC, pengecualian situs | IMPLEMENTED terbatas |
| Ekstensi bawaan Pembersih Tautan | IMPLEMENTED terbatas |
| Akun email/kata sandi; sesi terenkripsi Keystore | IMPLEMENTED, belum diuji terhadap server dari aplikasi |
| Login Google | IMPLEMENTED, perlu konfigurasi Anda; belum diuji |
| Sinkron bookmark | IMPLEMENTED terbatas; penghapusan tidak menyebar |
| Cari di halaman, Situs desktop (per tab, tidak disimpan) | IMPLEMENTED, belum diuji perangkat |
| Pemblokir iklan penuh, izin situs, DNS aman | TIDAK ADA |
| Ekstensi pihak ketiga, toolbar/popup ekstensi | TIDAK ADA |
| Sinkron riwayat/tab/pengaturan | TIDAK ADA |
| Grup tab, favicon, media/PiP, baca nanti, cetak/PDF | TIDAK ADA |
| Pengaturan: pencarian, Wi-Fi only, folder unduhan, batas kecepatan, zoom, kategori Tabs/Permissions/Accessibility/Data, export/import bookmark | TIDAK ADA |
| R8/minify, profil baseline, uji tablet/foldable | TIDAK ADA |
| Dukungan OS selain Android (arm64-v8a, minSdk 26) | TIDAK diklaim |

## Batasan teknis penting

- Mode privat memakai `usePrivateMode`; isolasi cookie/storage Gecko belum diaudit. Tab privat tidak mengunduh, tidak mencatat riwayat, tidak disinkronkan.
- Pengecualian pelacak per situs lewat `useTrackingProtection` per session + reload; belum diuji.
- Ekstensi: semua prompt izin ditolak otomatis; aktif di tab privat hanya bila Anda mengizinkan.
- Sinkron: kunci pencocokan URL; tidak ada enkripsi ujung ke ujung; data di server dilindungi RLS per pengguna.
- Login Google memakai browser lain di perangkat (bukan Credential Manager native).
- Room schema belum diekspor; migrasi 1 ke 2 belum diuji di perangkat.
- Tab normal latar belakang di atas batas dilepas dan dimuat ulang dari URL terakhir (scroll/form hilang).
