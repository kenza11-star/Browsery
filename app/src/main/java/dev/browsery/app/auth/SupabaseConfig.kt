package dev.browsery.app.auth

/**
 * Konfigurasi PUBLIK. Kunci publishable memang dirancang untuk ada di klien; akses data dibatasi RLS di server.
 * Jangan pernah menaruh service_role, kata sandi, atau keystore di sini.
 */
object SupabaseConfig {
    const val URL = "https://gbasnmipemxxsmvqmxjo.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable__cg7EnsdNPZN0UP-zQdmcA_sqjH69ig"
    const val REDIRECT = "browsery://auth-callback"
}
