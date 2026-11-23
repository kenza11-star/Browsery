# Status Stage

Status jujur per stage. "VERIFIED" hanya bila ada bukti (run Actions, commit SHA, artifact, atau hasil query). Belum ada satu pun stage yang diuji di perangkat/emulator.

| Stage | Topik | Status |
|---|---|---|
| 1 | Repo, arsitektur, bootstrap, build pertama | VERIFIED build: run hijau pada commit `ce7cd72`. |
| 2 | GeckoView dan navigasi | IMPLEMENTED, build hijau, 17 unit test URL lulus. |
| 3 | UI Chrome-like, beranda | IMPLEMENTED. Build hijau pada `79d3dce` (run 45084717946). |
| 4 | Multi-tab, history, bookmarks, private | IMPLEMENTED. Logika tab diuji unit. Room/restore/isolasi privat belum diuji di perangkat. |
| 5 | Download manager + paralel | IMPLEMENTED. Build hijau pada `20216ff` (run 45145145026). Engine diuji unit terhadap server HTTP lokal. |
| 6 | Settings center | PARTIAL. Build hijau pada `c6bd20a` (run 45146754668). Ada: mesin pencari (preset + kustom), tema, posisi toolbar, koneksi unduhan, hapus riwayat/cookie/cache, reset. BELUM ada dari spesifikasi: pencarian pengaturan, ringkasan per item untuk semua kategori, Wi-Fi only, folder unduhan, batas kecepatan, ukuran teks/zoom, kategori Tabs, Permissions, Accessibility, Data/Account, export/import bookmark, halaman Advanced/About lengkap. |
| 7 | Privasi, tracker blocking | IMPLEMENTED (lihat batasan). Build hijau pada `89eb220` (job 137953548884: unit test, assembleDebug, lint sukses). Belum diuji di perangkat/situs nyata. |
| 8 | WebExtensions | Belum. |
| 9 | Akun Supabase + Google Sign-In | Backend: tabel profil + RLS dibuat dan diuji. Klien Android: belum. |
| 10 | Cloud sync | Backend: skema + RLS dibuat dan diuji. Klien sync: belum. |
| 11-14 | Fitur lanjutan, performa, rilis, regresi | Belum. |

## Batasan Stage 3-4

- Grup tab BELUM ada. Tab dapat dibuka, ditutup, digeser, dibuka lagi (10 terakhir).
- Menu "Cari di halaman" dan "Situs desktop" tampil nonaktif.
- Maksimal 6 session tab normal dimuat sekaligus; sisanya dimuat ulang dari URL terakhir.
- Restore: hanya tab normal (URL + judul + tab aktif); hanya tab aktif yang dimuat saat start.
- Mode privat memakai `usePrivateMode`; riwayat tidak dicatat; tidak disimpan/disinkronkan. Isolasi cookie/storage Gecko BELUM diaudit.
- Belum ada favicon, UI test Compose, atau uji perangkat/emulator.

## Stage 5 - Unduhan

- `HttpURLConnection`; Range paralel bila server mendukung (1/2/4/8 koneksi atau otomatis), selain itu satu aliran (tidak bisa dilanjutkan).
- Resume hanya bila ukuran + ETag/Last-Modified sama; `If-Range` dan validasi `Content-Range`.
- Retry dengan backoff; deteksi data terpotong; validasi ukuran akhir.
- Android 10+ ke `Download/Browsery` (MediaStore); Android 9- ke folder aplikasi.
- Service latar depan `dataSync`; izin notifikasi diminta sekali. Android 15+ menjeda unduhan saat batas waktu service.
- Tab privat TIDAK mengunduh.
- Batasan: cookie/otorisasi sesi tidak ikut ke mesin unduhan; belum ada batas kecepatan; lanjut setelah proses mati hanya saat app dibuka lagi; migrasi Room 1 ke 2 belum diuji di perangkat; skema Room belum diekspor.

## Stage 6 - Pengaturan

- DataStore Preferences (lokal, tanpa data sensitif, tidak disinkronkan). Koneksi unduhan masih di SharedPreferences.
- Mesin pencari diteruskan lewat `UrlUtils.currentTemplate` (status global sederhana).
- Ikon status bar tidak menyesuaikan bila tema dipaksa berbeda dari sistem.

## Stage 7 - Privasi dan keamanan

- Perlindungan pelacak: Enhanced Tracking Protection milik Gecko (Mati/Standar/Ketat) lewat `GeckoRuntimeSettings.contentBlocking`; kebijakan cookie (semua / non-pelacak / isolasi pihak ketiga / pihak pertama); mode HTTPS-only; Global Privacy Control.
- Pengecualian per situs: daftar host disimpan di DataStore; saat lokasi berubah, `session.settings.useTrackingProtection` disamakan dan halaman dimuat ulang sekali bila berubah. Perilaku ini belum diuji di perangkat.
- Panel Info situs: host, status HTTPS menurut engine, jumlah sumber daya yang diblokir engine (dari `ContentBlocking.Delegate`, bukan perkiraan), saklar per situs, hapus cookie/data situs untuk domain.
- Halaman Privasi dan keamanan dipisah dari Pengaturan (belum digabung).
- Sertifikat TLS tidak valid tidak pernah dilewati; halaman error menjelaskan dan tidak menawarkan bypass.
- BELUM: pemblokir iklan penuh seperti Brave Shields (hanya daftar bawaan Gecko), UI izin per situs (kamera/mikrofon/lokasi/notifikasi), pembaruan daftar filter kustom, DNS aman. Tidak ada analitik dan tidak ada pengiriman riwayat ke server.
