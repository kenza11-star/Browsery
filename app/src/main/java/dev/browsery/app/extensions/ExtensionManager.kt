package dev.browsery.app.extensions

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import dev.browsery.app.engine.GeckoRuntimeProvider
import dev.browsery.app.settings.AppSettings
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

data class ExtInfo(
    val status: ExtStatus,
    val version: String? = null,
    val permissions: List<String> = emptyList(),
    val origins: List<String> = emptyList(),
    val allowedPrivate: Boolean = false,
    val error: String? = null,
)

/**
 * Pengelola ekstensi di atas WebExtensionController GeckoView. Hanya ekstensi bawaan (aset aplikasi) yang dapat
 * dipasang. Semua kegagalan ditangkap per ekstensi dan ditampilkan sebagai status; tidak ada yang boleh
 * menghentikan aplikasi.
 */
class ExtensionManager(private val context: Context, private val settings: AppSettings) {
    val infos = mutableStateMapOf<String, ExtInfo>()

    private var delegatesSet = false

    private fun controller(): WebExtensionController {
        val c = GeckoRuntimeProvider.get(context).webExtensionController
        if (!delegatesSet) {
            delegatesSet = true
            // Tidak ada pemasangan pihak ketiga: semua permintaan izin dari engine ditolak.
            c.promptDelegate = object : WebExtensionController.PromptDelegate {
                override fun onInstallPromptRequest(
                    extension: WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>,
                    dataCollectionPermissions: Array<String>,
                ): GeckoResult<WebExtension.PermissionPromptResponse>? =
                    GeckoResult.fromValue(WebExtension.PermissionPromptResponse(false, false, false))

                override fun onUpdatePrompt(
                    extension: WebExtension,
                    newPermissions: Array<String>,
                    newOrigins: Array<String>,
                    newDataCollectionPermissions: Array<String>,
                ): GeckoResult<AllowOrDeny>? = GeckoResult.fromValue(AllowOrDeny.DENY)

                override fun onOptionalPrompt(
                    extension: WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>,
                    dataCollectionPermissions: Array<String>,
                ): GeckoResult<AllowOrDeny>? = GeckoResult.fromValue(AllowOrDeny.DENY)
            }
        }
        return c
    }

    private fun infoOf(we: WebExtension): ExtInfo {
        val m = we.metaData
        return ExtInfo(
            status = ExtensionCatalog.statusOf(installed = true, enabled = m.enabled, error = null),
            version = m.version,
            permissions = m.requiredPermissions.toList(),
            origins = m.requiredOrigins.toList(),
            allowedPrivate = m.allowedInPrivateBrowsing,
        )
    }

    private fun fail(id: String, e: Throwable?) {
        infos[id] = ExtInfo(ExtStatus.ERROR, error = e?.message ?: "Kesalahan tidak diketahui")
    }

    /** Pasang ulang ekstensi yang sebelumnya disetujui pengguna. Dipanggil saat aplikasi mulai. */
    fun restore() {
        val ids = settings.state.value.extensionsEnabled
        if (ids.isEmpty()) return
        ids.mapNotNull { ExtensionCatalog.byId(it) }.forEach { install(it, persist = false) }
    }

    /** Memuat status terkini seluruh ekstensi yang terpasang di engine. */
    fun refresh() {
        try {
            controller().list().then<Void>(
                { list ->
                    val installed = list.orEmpty().associateBy { it.id }
                    ExtensionCatalog.all.forEach { item ->
                        val we = installed[item.id]
                        val old = infos[item.id]
                        if (we != null) {
                            infos[item.id] = infoOf(we)
                        } else if (old?.status != ExtStatus.ERROR) {
                            infos[item.id] = ExtInfo(ExtStatus.NOT_INSTALLED)
                        }
                    }
                    null
                },
                { e -> ExtensionCatalog.all.forEach { fail(it.id, e) }; null },
            )
        } catch (e: Exception) {
            ExtensionCatalog.all.forEach { fail(it.id, e) }
        }
    }

    /** Memasang dan mengaktifkan ekstensi bawaan. Pemanggil WAJIB sudah mendapat persetujuan pengguna. */
    fun install(item: BundledExtension, persist: Boolean = true) {
        try {
            controller().ensureBuiltIn(item.uri, item.id).then<Void>(
                { we ->
                    if (we != null) {
                        if (persist) settings.update { it.withExtension(item.id, enabled = true) }
                        infos[item.id] = infoOf(we)
                        if (item.id in settings.state.value.extensionsPrivate) {
                            setPrivate(item, true, persist = false)
                        }
                    }
                    null
                },
                { e -> fail(item.id, e); null },
            )
        } catch (e: Exception) {
            fail(item.id, e)
        }
    }

    private fun withExtension(id: String, block: (WebExtension) -> Unit) {
        try {
            controller().list().then<Void>(
                { list ->
                    val we = list.orEmpty().firstOrNull { it.id == id }
                    if (we != null) block(we) else infos[id] = ExtInfo(ExtStatus.NOT_INSTALLED)
                    null
                },
                { e -> fail(id, e); null },
            )
        } catch (e: Exception) {
            fail(id, e)
        }
    }

    fun setEnabled(item: BundledExtension, on: Boolean) = withExtension(item.id) { we ->
        val c = controller()
        val r = if (on) {
            c.enable(we, WebExtensionController.EnableSource.USER)
        } else {
            c.disable(we, WebExtensionController.EnableSource.USER)
        }
        r.then<Void>(
            { updated -> if (updated != null) infos[item.id] = infoOf(updated); null },
            { e -> fail(item.id, e); null },
        )
    }

    /** Mengizinkan ekstensi berjalan di tab privat. Hanya setelah persetujuan eksplisit pengguna. */
    fun setPrivate(item: BundledExtension, allowed: Boolean, persist: Boolean = true) = withExtension(item.id) { we ->
        controller().setAllowedInPrivateBrowsing(we, allowed).then<Void>(
            { updated ->
                if (persist) settings.update { it.withExtensionPrivate(item.id, allowed) }
                if (updated != null) infos[item.id] = infoOf(updated)
                null
            },
            { e -> fail(item.id, e); null },
        )
    }

    fun remove(item: BundledExtension) = withExtension(item.id) { we ->
        controller().uninstall(we).then<Void>(
            {
                settings.update { it.withExtension(item.id, enabled = false).withExtensionPrivate(item.id, false) }
                infos[item.id] = ExtInfo(ExtStatus.NOT_INSTALLED)
                null
            },
            { e -> fail(item.id, e); null },
        )
    }
}
