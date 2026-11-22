# Status Stage

Status jujur per stage. "Terverifikasi" hanya bila ada bukti (run Actions, commit SHA, artifact, atau hasil query).

| Stage | Topik | Status |
|---|---|---|
| 1 | Repo, arsitektur, bootstrap, build pertama | Kode + workflow ada. Build Actions BELUM terverifikasi (belum ada run yang dibaca). |
| 2 | GeckoView dan navigasi | Diimplementasikan (address bar, back/forward/reload/stop, progress, halaman error, tel/mailto, popup, intent VIEW http/https). Unit test URL ditulis. BELUM terverifikasi build/uji di perangkat. |
| 3 | UI Chrome-like, beranda | Belum (hanya toolbar dasar dari Stage 2). |
| 4 | Multi-tab, history, bookmarks, private | Belum. |
| 5 | Download manager + paralel | Belum. |
| 6 | Settings center | Belum. |
| 7 | Privasi, tracker blocking | Belum. |
| 8 | WebExtensions | Belum. |
| 9 | Akun Supabase + Google Sign-In | Backend: tabel profil + RLS dibuat dan diuji (lihat SETUP_SUPABASE.md). Klien Android: belum. |
| 10 | Cloud sync | Backend: skema bookmarks/history/tabs/settings/devices + RLS dibuat dan diuji. Klien sync: belum. |
| 11-14 | Fitur lanjutan, performa, rilis, regresi | Belum. |

## Batasan Stage 2 yang diketahui

- Belum ada: tab, beranda, favicon, file chooser, prompt izin situs (kamera/mikrofon/lokasi), penanganan unduhan (`onExternalResponse`), dialog JavaScript. Fitur ini tidak diaktifkan diam-diam; belum ada UI untuk itu.
- Fullscreen video: toolbar disembunyikan dan system bar disembunyikan, belum diuji di perangkat.
- Login situs memakai cookie/session Gecko; belum ada situs login yang diuji.
- Select-all saat fokus address bar dan semua menu Chrome-like menunggu Stage 3.
