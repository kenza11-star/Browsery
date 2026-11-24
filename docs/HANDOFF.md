# Serah terima Browsery

## Yang perlu Anda lakukan

1. **Gabungkan cabang.** Cabang saling bertingkat; satu PR dari `stage-13-release` ke `main` (atau gabungkan berurutan) sudah memuat semuanya: stage-1-bootstrap, stage-2-navigation, stage-3-4-tabs-data, stage-5-downloads, stage-6-settings, stage-7-privacy, stage-8-extensions, stage-9-auth, stage-10-sync, stage-11-tools, stage-13-release. (`stage-9-account` adalah cabang kosong salah buat; abaikan/hapus.)
2. **Workflow rilis.** Salin `docs/release.yml.example` ke `.github/workflows/release.yml`.
3. **GitHub Secrets (nama saja):** `BROWSERY_KEYSTORE_BASE64`, `BROWSERY_KEYSTORE_PASSWORD`, `BROWSERY_KEY_ALIAS`, `BROWSERY_KEY_PASSWORD`. Buat keystore sendiri, simpan cadangannya; kehilangan keystore berarti pembaruan tidak bisa dipasang di atas versi lama.
4. **Login Google (opsional).** Aktifkan penyedia Google di dasbor Supabase (Authentication > Providers, butuh Client ID/Secret dari Google Cloud milik Anda) dan tambahkan `browsery://auth-callback` ke Redirect URLs. Tanpa ini tombol Google menampilkan galat dari server.
5. **Uji di HP.** Belum ada satu pun stage yang diuji di perangkat. Ini langkah terpenting berikutnya.

## Backend Supabase

Proyek `gbasnmipemxxsmvqmxjo`; tabel `browsery_*` dengan RLS (diuji lewat query). Klien hanya memakai URL dan kunci publishable. Tidak ada service_role di repo atau APK.

## Build

GitHub Actions `Android CI` (unit test, assembleDebug, lint) hijau pada setiap cabang di tabel `STAGES.md`. Artefak: `browsery-debug-apk`.
