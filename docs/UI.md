# Desain UI (Stage 17)

Status: IMPLEMENTED, lolos CI (unit test, assembleDebug, lint; commit 38cc265, job 140266138822). **Belum dilihat/diukur di perangkat**: kehalusan animasi, FPS, memori, dan ada/tidaknya lag belum terverifikasi.

## Ada
- `ui/Glass.kt`: latar gradien, `glassSurface()`, `pressScale()`, `GlassAppRoot` (transisi halaman non-web; halaman web tidak dianimasikan).
- Wallpaper lokal (Pengaturan > Tampilan dan wallpaper): foto, GIF (animasi di Android 9+), video senyap berulang. Salinan privat di filesDir, picker bawaan tanpa izin penyimpanan luas, tidak diunggah. Ukuran asli dan ukuran pakai ditampilkan.
- Cairan: satu pita organik di beranda, cakupan 25-50% (uji unit memeriksa luas pita = cakupan di banyak fase). Foto: isi pita = salinan buram (downsample + upscale, tanpa RenderEffect), di luar pita tajam. GIF/video: isi pita kaca tembus pandang TANPA blur (degradasi eksplisit).
- Kualitas Otomatis/Hemat/Tinggi; Hemat tidak menggerakkan GIF/video. Animasi berhenti saat tidak terlihat, di luar beranda, saat aplikasi tidak RESUMED, atau animasi sistem dimatikan.
- Preferensi refresh rate 60/90/120 (hanya permintaan ke sistem; tidak ada jaminan).
- Gambar mini tab dari piksel halaman nyata (`GeckoView.capturePixels`), hanya di memori, diambil saat meninggalkan halaman menuju daftar tab.
- Chip Pengaturan/Privasi membungkus baris (tidak terpotong). Debug build tidak lagi debuggable.

## Belum / batas
- Blur latar sungguhan di atas halaman web TIDAK mungkin (surface native Gecko).
- Bar alamat dan bar navigasi browser belum bergaya kaca penuh (pil alamat masih buram abu-abu).
- Halaman Unduhan, Ekstensi, Akun, Beranda, dialog situs belum diberi kartu kaca; baru memakai latar dan transisi.
- Rotasi: wallpaper di-crop ke rasio potret saat dimuat; landscape memakai crop tengah dari itu.
- EXIF foto di Android 8 (API 26-27) belum diperhatikan; API 28+ ditangani decoder sistem.
- Tidak ada baseline profile/R8; penyebab lag lain belum diukur. Tidak ada metrik FPS yang diklaim.
