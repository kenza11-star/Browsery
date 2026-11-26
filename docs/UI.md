# Desain UI (Stage 17)

Status: IMPLEMENTED, lolos CI (unit test, assembleDebug, lint). **Belum dilihat/diukur di perangkat**: kehalusan animasi dan ada/tidaknya lag belum terverifikasi.

## Yang ada
- `ui/Glass.kt`: `GlassBackground` (gradien + 2 bercak cahaya, tanpa blur), `Modifier.glassSurface()` (isi tembus pandang, kilau, tepi gradien 1dp), `Modifier.pressScale()` (skala pegas saat ditekan), `GlassAppRoot` (transisi antarhalaman fade + geser/skala).
- Tema: warna `background` transparan sehingga latar kaca terlihat di halaman non-web; mode gelap mengikuti pilihan tema aplikasi.
- Halaman Bookmark/Riwayat: kartu kaca, `animateItem()` saat item muncul/hilang/berpindah, header tebal.
- Teks menu tidak lagi berisi label "tersedia di Stage N".

## Batasan jujur
- Konten web (GeckoView) adalah surface native: blur latar sungguhan di atas halaman web TIDAK mungkin. "Kaca" hanya di atas latar Compose.
- Tidak memakai RenderEffect blur agar ringan; ini tiruan visual, bukan backdrop blur.
- Halaman Pengaturan, Privasi, Unduhan, Ekstensi, Akun, Tab, Beranda baru mendapat latar + transisi; kartu kaca penuh dan perbaikan chip terpotong (Pengaturan/Privasi) belum dikerjakan.
