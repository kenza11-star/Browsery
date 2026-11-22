# Setup Supabase

Project yang dipakai: `gbasnmipemxxsmvqmxjo` (nama project: Bot-gitele, region ap-southeast-2).

> Project ini juga dipakai aplikasi lain (skema `app`: bot GitHub Manager). Tabel Browsery memakai prefix `browsery_` di skema `public` dan tidak menyentuh tabel aplikasi lain. Dibuat satu project saja sesuai kontrak; bila ingin terpisah, buat project baru dan jalankan ulang migration yang sama.

## Tabel (migration `browsery_0001_sync_schema`)

| Tabel | Isi |
|---|---|
| `browsery_profiles` | profil dasar per pengguna (dibuat klien saat login pertama) |
| `browsery_devices` | perangkat + waktu sync terakhir |
| `browsery_bookmarks` | bookmark (soft delete lewat `deleted_at`) |
| `browsery_history` | riwayat (opt-in; tab privat TIDAK pernah diunggah) |
| `browsery_open_tabs` | tab terbuka (opt-in) |
| `browsery_settings` | pengaturan relevan (JSON, maks 64 KB) |

Semua tabel: RLS aktif, hanya role `authenticated` (anon dicabut), kepemilikan `auth.uid() = user_id` untuk SELECT/INSERT/UPDATE/DELETE, `updated_at` otomatis (UTC) untuk delta sync, indeks `(user_id, updated_at)`.

Tidak ada password situs, cookie, atau isi halaman yang disimpan.

## Bukti pengujian (dijalankan 22 November 2026)

Uji RLS dua pengguna sementara di dalam transaksi yang di-rollback:

- pengguna B melihat 0 bookmark milik A
- pengguna B mengubah 0 baris milik A
- pengguna B gagal menyisipkan baris atas nama A
- pengguna A melihat 1 bookmark miliknya

Security advisor: tidak ada temuan pada tabel `browsery_*` (temuan INFO yang muncul berasal dari skema `app` aplikasi lain).

## Kunci klien

Aplikasi hanya boleh memakai URL project dan publishable key (`sb_publishable_...`). Ambil dari Dashboard Supabase > Project Settings > API. JANGAN pernah memasukkan `service_role` ke repo atau APK. Kunci publishable tidak di-commit di repo ini; akan disuntik lewat konfigurasi build di Stage 9.

## Yang perlu Anda lakukan untuk Google Sign-In (Stage 9)

1. Google Cloud Console: buat OAuth Client ID tipe Android (package `dev.browsery.app` + SHA-1 debug/release) dan tipe Web.
2. Supabase Dashboard > Authentication > Providers > Google: isi Client ID/secret Web, aktifkan opsi nonce-check sesuai panduan Supabase.
3. Simpan SHA-1 keystore release yang stabil (Stage 13) agar login tidak rusak saat update APK.
