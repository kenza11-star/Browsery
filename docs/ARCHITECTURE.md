# Arsitektur

## Prinsip

- Android dulu. Desktop (Windows/macOS/Linux) dan iOS adalah target terpisah di peta jalan, bukan hasil otomatis dari APK ini.
- Satu modul Gradle (`app`). Dipecah bila ada alasan nyata (download, sync).
- Browsing harus tetap jalan tanpa akun dan tanpa Supabase.

## Alur aplikasi (Stage 2)

```
MainActivity
  -> BrowserController (pemilik GeckoSession + state Compose)
  -> BrowseryTheme -> BrowserScreen
       -> AddressBar (back, forward, field alamat, reload/stop)
       -> progress bar, MessageBar
       -> GeckoHost (AndroidView -> GeckoView.setSession)
```

- `GeckoRuntimeProvider`: satu `GeckoRuntime` per proses.
- `BrowserController`: memiliki `GeckoSession`, mengimplementasikan Navigation/Progress/Content delegate, dan mengekspos `BrowserState` (url, judul, canGoBack/Forward, progress, secure, fullscreen, pesan). Session tidak ditutup oleh composable, jadi rotasi/recomposition tidak memuat ulang halaman. Session ditutup saat Activity dihancurkan.
- `UrlUtils`: logika murni (tanpa Android) untuk normalisasi input, URL pencarian ter-encode, penolakan skema berbahaya (`javascript:`, `file:`, `data:`, `intent:`, dll). Diuji di `UrlUtilsTest`.
- Halaman error: `onLoadError` mengembalikan data URI dengan pesan Indonesia. Kesalahan sertifikat TIDAK dilewati.
- Intent: `tel:`/`mailto:`/`sms:` dibuka lewat aplikasi lain; popup `window.open` dimuat di tab saat ini; intent VIEW http/https dari aplikasi lain diterima.

## Backend (Supabase)

Skema sinkronisasi ada di `supabase/migrations/` dan dijelaskan di `docs/SETUP_SUPABASE.md`. Klien Android belum memakainya.

## Peta jalan

Stage 3 menambah UI Chrome-like dan beranda; Stage 4 tab, history, bookmarks. Lihat `docs/STAGES.md`.
