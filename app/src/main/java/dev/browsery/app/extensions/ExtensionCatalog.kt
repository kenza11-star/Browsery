package dev.browsery.app.extensions

/** Ekstensi bawaan yang dikemas di dalam aplikasi (aset). Hanya ini yang dapat dipasang dari UI. */
data class BundledExtension(
    val id: String,
    val assetDir: String,
    val name: String,
    val description: String,
    /** Akses situs yang dideklarasikan manifest (ditampilkan sebelum persetujuan). */
    val hostAccess: List<String>,
    val privacyNote: String,
) {
    val uri: String get() = "resource://android/assets/$assetDir/"
}

enum class ExtStatus { NOT_INSTALLED, ENABLED, DISABLED, ERROR }

object ExtensionCatalog {
    val LINK_CLEAN = BundledExtension(
        id = "linkclean@browsery.dev",
        assetDir = "extensions/linkclean",
        name = "Pembersih Tautan",
        description = "Menghapus parameter pelacak (utm_*, fbclid, gclid, dan sejenisnya) dari tautan yang ada di halaman.",
        hostAccess = listOf("http://*/*", "https://*/*"),
        privacyNote = "Skrip berjalan di setiap halaman web yang Anda buka dan mengubah atribut tautan di halaman itu. " +
            "Skrip tidak membaca isi formulir, tidak mengirim data keluar, dan tidak menyimpan apa pun.",
    )

    val all: List<BundledExtension> = listOf(LINK_CLEAN)

    fun byId(id: String): BundledExtension? = all.firstOrNull { it.id == id }

    fun statusOf(installed: Boolean, enabled: Boolean, error: String?): ExtStatus = when {
        error != null -> ExtStatus.ERROR
        !installed -> ExtStatus.NOT_INSTALLED
        enabled -> ExtStatus.ENABLED
        else -> ExtStatus.DISABLED
    }
}
