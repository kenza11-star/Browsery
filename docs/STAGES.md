# Status Stage

Status jujur per stage. "VERIFIED" hanya bila ada bukti (run Actions, commit SHA, artifact, atau hasil query).

| Stage | Topik | Status |
|---|---|---|
| 1 | Repo, arsitektur, bootstrap, build pertama | VERIFIED build: run Actions hijau pada commit `ce7cd72` (unit test, assembleDebug, lint, artifact `browsery-debug-apk`). Belum diuji di perangkat. |
| 2 | GeckoView dan navigasi | IMPLEMENTED, build hijau, 17 unit test URL lulus. Belum diuji di perangkat/situs nyata. |
| 3 | UI Chrome-like, beranda | IMPLEMENTED. Build hijau pada commit `79d3dce` (run 45084717946). Belum diuji di perangkat. |
| 4 | Multi-tab, history, bookmarks, private | IMPLEMENTED. Logika tab diuji unit (`TabOpsTest`). Room/restore/isolasi privat belum diuji instrumented/perangkat. |
| 5 | Download manager + paralel | IMPLEMENTED. Build hijau pada commit `20216ff` (run 45145145026: unit test, assembleDebug, lint sukses). Engine diuji unit terhadap server HTTP lokal. Belum diuji di perangkat/situs nyata. |
| 6 | Settings center | Belum. |
| 7 | Privasi, tracker blocking | Belum. |
| 8 | WebExtensions | Belum. |
| 9 | Akun Supabase + Google Sign-In | Backend: tabel profil + RLS dibuat dan diuji. Klien Android: belum. |
| 10 | Cloud sync | Backend: skema + RLS dibuat dan diuji. Klien sync: belum. |
| 11-14 | Fitur lanjutan, performa, rilis, regresi | Belum. |

## Batasan Stage 3-4

- Grup tab (nama/warna) BELUM ada. Tab dapat dibuka, ditutup, digeser, dibuka lagi (10 terakhir).
- Menu yang belum aktif (Cari di halaman, Situs desktop, Pengaturan) tampil nonaktif dengan keterangan stage-nya.
- Posisi toolbar atas/bawah belum ada (menunggu Settings, Stage 6). Toolbar di ponsel memindahkan maju/muat ulang/bookmark ke menu; layar lebar (>= 600dp) menampilkan strip tab dan semua tombol.
- Maksimal 6 session tab normal dimuat sekaligus; sisanya dilepas dan dimuat ulang dari URL terakhir (posisi scroll/isian form tidak dipertahankan). Tab privat tidak pernah dilepas otomatis.
- Restore: hanya tab normal (URL + judul + tab aktif) dan hanya tab aktif yang dimuat saat start.
- Mode privat: memakai `usePrivateMode` GeckoSession; riwayat tidak dicatat; tab privat tidak disimpan ke disk dan tidak pernah disinkronkan. Isolasi cookie/storage Gecko BELUM diaudit; jangan dianggap terbukti.
- Pintasan beranda dimulai kosong; tidak ada data contoh.
- Belum ada favicon, UI test Compose, atau uji perangkat/emulator.

## Stage 5 - Unduhan: cara kerja dan batasan

- Engine memakai `HttpURLConnection`. Bila server mendukung Range (206 + `Content-Range` valid + ukuran diketahui), berkas dibagi menjadi potongan dan tiap potongan diunduh oleh satu koneksi (1 / 2 / 4 / 8 atau otomatis menurut ukuran). Bila tidak, dipakai satu aliran biasa (tidak bisa dilanjutkan; mulai dari nol).
- Jumlah koneksi paralel per berkas terpisah dari batas berkas bersamaan (default 3).
- Resume hanya bila ukuran, ETag/Last-Modified sama dan berkas parsial utuh; selain itu mengunduh ulang dari awal. `If-Range` dan validasi `Content-Range` dipakai agar tidak tercampur dua versi berkas.
- Percobaan ulang dengan backoff pada kesalahan jaringan/5xx; deteksi data terpotong; ukuran akhir divalidasi.
- Penyimpanan: Android 10+ ke `Download/Browsery` lewat MediaStore; Android 9- ke folder khusus aplikasi (FileProvider untuk membuka).
- Notifikasi: service latar depan `dataSync` hanya selama ada unduhan; izin notifikasi diminta sekali saat unduhan pertama. Pada Android 15+ batas waktu service menjeda unduhan agar bisa dilanjutkan manual.
- Tab privat TIDAK mengunduh (ditolak dengan pesan), agar tidak ada jejak.
- Batasan: cookie/otorisasi sesi halaman TIDAK ikut ke mesin unduhan (situs yang butuh login bisa gagal); belum ada batas kecepatan; pelanjutan setelah proses dihentikan hanya saat aplikasi dibuka lagi; migrasi Room 1 ke 2 belum diuji di perangkat; skema Room belum diekspor; hanya http/https.
