# Browsery

Browser Android pribadi berbasis GeckoView, Kotlin, dan Jetpack Compose. Dikerjakan bertahap (Stage 1-14), lihat `docs/STAGES.md` untuk status jujur tiap tahap.

**Status:** Stage 2 (navigasi dasar) diimplementasikan tetapi belum terverifikasi di perangkat/CI. Belum ada tab, beranda, download manager, pengaturan, atau sinkronisasi di sisi aplikasi.

## Build

Build resmi berjalan di GitHub Actions (`.github/workflows/android.yml`): lint, unit test, `assembleDebug`, lalu APK diunggah sebagai artifact `browsery-debug-apk`.

Repo ini belum menyertakan Gradle wrapper (file biner `gradle-wrapper.jar` tidak bisa di-commit lewat konektor). CI memakai Gradle 9.3.1 langsung. Untuk build lokal: pasang JDK 17 dan Gradle 9.3.1, lalu jalankan `gradle :app:assembleDebug`. Opsional: jalankan `gradle wrapper --gradle-version 9.3.1` sekali dan commit hasilnya.

## Catatan teknis

- minSdk 26, targetSdk/compileSdk 36. Pilihan minSdk belum diuji di perangkat nyata.
- Hanya arm64-v8a. Perangkat 32-bit dan emulator x86_64 belum didukung.
- GeckoView: `org.mozilla.geckoview:geckoview-arm64-v8a:159.0.20261102144711` dari `maven.mozilla.org`.
- Tidak ada secret di repo. Signing release baru dibuat di Stage 13.
- Backend Supabase (skema sync + RLS): `supabase/migrations/` dan `docs/SETUP_SUPABASE.md`.

Lihat `docs/ARCHITECTURE.md` untuk arsitektur dan peta jalan lintas platform.
