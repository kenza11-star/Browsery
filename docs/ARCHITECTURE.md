# Arsitektur

## Prinsip

- Android dulu. Desktop (Windows/macOS/Linux) dan iOS adalah target terpisah di peta jalan, bukan hasil otomatis dari APK ini.
- Satu modul Gradle (`app`) pada Stage 1. Dipecah bila ada alasan nyata (download, sync).
- Browsing harus tetap jalan tanpa akun dan tanpa Supabase.

## Alur aplikasi (Stage 1)

```
MainActivity (Compose)
  -> BrowseryTheme (light/dark)
  -> BrowseryRoot
       -> teks status
       -> GeckoHost (AndroidView)
            -> GeckoSession.open(GeckoRuntimeProvider.get())
            -> loadUri("about:blank")
```

`GeckoRuntime` dibuat sekali per proses (`GeckoRuntimeProvider`). `GeckoSession` dibuat per layar dan ditutup saat composable dibuang.

## Struktur

- `app/src/main/java/dev/browsery/app/MainActivity.kt`: entry point.
- `.../engine/`: integrasi GeckoView.
- `.../ui/`: tema.

## Peta jalan

Stage 2 menambah navigasi sungguhan (address bar, tab, state). Lihat `docs/STAGES.md`.
