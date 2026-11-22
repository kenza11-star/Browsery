# Status Stage

Status jujur per stage. "VERIFIED" hanya bila ada bukti (run Actions, commit SHA, artifact, atau hasil query).

| Stage | Topik | Status |
|---|---|---|
| 1 | Repo, arsitektur, bootstrap, build pertama | VERIFIED build: run Actions hijau pada commit `ce7cd72` (unit test, assembleDebug, lint, artifact `browsery-debug-apk`). Belum diuji di perangkat. |
| 2 | GeckoView dan navigasi | IMPLEMENTED, build hijau, 17 unit test URL lulus. Belum diuji di perangkat/situs nyata. |
| 3 | UI Chrome-like, beranda | IMPLEMENTED (lihat batasan). Build/tes lihat PR terbaru. |
| 4 | Multi-tab, history, bookmarks, private | IMPLEMENTED (lihat batasan). Logika tab diuji unit (`TabOpsTest`). Room/restore/isolasi privat belum diuji instrumented/perangkat. |
| 5 | Download manager + paralel | Belum. |
| 6 | Settings center | Belum. |
| 7 | Privasi, tracker blocking | Belum. |
| 8 | WebExtensions | Belum. |
| 9 | Akun Supabase + Google Sign-In | Backend: tabel profil + RLS dibuat dan diuji. Klien Android: belum. |
| 10 | Cloud sync | Backend: skema + RLS dibuat dan diuji. Klien sync: belum. |
| 11-14 | Fitur lanjutan, performa, rilis, regresi | Belum. |

## Batasan Stage 3-4

- Grup tab (nama/warna) BELUM ada. Tab dapat dibuka, ditutup, digeser, dibuka lagi (10 terakhir).
- Menu yang belum aktif (Unduhan, Cari di halaman, Situs desktop, Pengaturan) tampil nonaktif dengan keterangan stage-nya.
- Posisi toolbar atas/bawah belum ada (menunggu Settings, Stage 6). Toolbar di ponsel memindahkan maju/muat ulang/bookmark ke menu; layar lebar (>= 600dp) menampilkan strip tab dan semua tombol.
- Maksimal 6 session tab normal dimuat sekaligus; sisanya dilepas dan dimuat ulang dari URL terakhir (posisi scroll/isian form tidak dipertahankan). Tab privat tidak pernah dilepas otomatis.
- Restore: hanya tab normal (URL + judul + tab aktif) dan hanya tab aktif yang dimuat saat start.
- Mode privat: memakai `usePrivateMode` GeckoSession; riwayat tidak dicatat; tab privat tidak disimpan ke disk dan tidak pernah disinkronkan. Isolasi cookie/storage Gecko BELUM diaudit; jangan dianggap terbukti.
- Skema Room v1 tanpa ekspor skema JSON; migrasi pertama wajib menambah `exportSchema` dan tes migrasi.
- Pintasan beranda dimulai kosong; tidak ada data contoh.
- Belum ada favicon, UI test Compose, atau uji perangkat/emulator.
