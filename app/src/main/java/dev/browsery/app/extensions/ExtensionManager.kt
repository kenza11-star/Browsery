package dev.browsery.app.extensions

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** Satu ekstensi terpasang (bawaan Browsery maupun dari Firefox Add-ons). */
data class ExtRow(
    val id: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
    val permissions: List<String>,
    val origins: List<String>,
    val allowedPrivate: Boolean,
    val builtIn: Boolean,
)

/** Permintaan izin dari engine yang menunggu keputusan pengguna. Tanpa persetujuan, tidak ada yang terpasang. */
class PendingInstall(
    val name: String,
    val permissions: List<String>,
    val origins: List<String>,
    val result: GeckoResult<WebExtension.PermissionPromptResponse>,
)

/**
 * Pengelola ekstensi di atas WebExtensionController GeckoView. Ekstensi bawaan (aset aplikasi) dan ekstensi dari
 * Firefox Add-ons dapat dipasang; keduanya hanya setelah pengguna menyetujui daftar izin. Semua kegagalan ditangkap
 * dan ditampilkan sebagai status; tidak ada yang boleh menghentikan aplikasi.
 */
class ExtensionManager(private val context: Context, private val settings: AppSettings) {
    val infos = mutableStateMapOf<String, ExtInfo>()
    val installed = mutableStateMapOf<String, ExtRow>()

    var pending by mutableStateOf<PendingInstall?>(null)
        private set
    var installing by mutableStateOf(false)
        private set
    var installMessage by mutableStateOf<String?>(null)
        private set

    private var delegatesSet = false

    private fun controller(): WebExtensionController {
        val c = GeckoRuntimeProvider.get(context).webExtensionController
        if (!delegatesSet) {
            delegatesSet = true
            c.promptDelegate = object : WebExtensionController.PromptDelegate {
                override fun onInstallPromptRequest(
                    extension: WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>,
                    dataCollectionPermissions: Array<String>,
                ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                    // Permintaan sebelumnya yang belum dijawab ditolak agar tidak menggantung.
                    pending?.result?.complete(WebExtension.PermissionPromptResponse(false, false, false))
                    val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                    pending = PendingInstall(
                        name = extension.metaData.name ?: extension.id,
                        permissions = permissions.toList(),
                        origins = origins.toList(),
                        result = result,
                    )
                    return result
                }

                // Pembaruan dengan izin baru dan izin opsional tidak pernah diterima diam-diam.
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

    /** Memastikan delegasi izin terpasang sejak awal (agar permintaan dari engine selalu punya dialog). */
    fun ensureDelegates() {
        try {
            controller()
        } catch (e: Exception) {
            // runtime belum siap; akan dicoba lagi saat dipakai
        }
    }

    /** Jawaban pengguna atas dialog izin. */
    fun answerPending(accept: Boolean, allowPrivate: Boolean) {
        val p = pending ?: return
        pending = null
        p.result.complete(WebExtension.PermissionPromptResponse(accept, accept && allowPrivate, false))
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

    private fun rowOf(we: WebExtension): ExtRow {
        val m = we.metaData
        return ExtRow(
            id = we.id,
            name = m.name ?: we.id,
            version = m.version.orEmpty(),
            enabled = m.enabled,
            permissions = m.requiredPermissions.toList(),
            origins = m.requiredOrigins.toList(),
            allowedPrivate = m.allowedInPrivateBrowsing,
            builtIn = ExtensionCatalog.byId(we.id) != null,
        )
    }

    private fun fail(id: String, e: Throwable?) {
        infos[id] = ExtInfo(ExtStatus.ERROR, error = e?.message ?: "Kesalahan tidak diketahui")
    }

    /** Pasang ulang ekstensi bawaan yang sebelumnya disetujui pengguna. Dipanggil saat aplikasi mulai. */
    fun restore() {
        ensureDelegates()
        val ids = settings.state.value.extensionsEnabled
        if (ids.isEmpty()) return
        ids.mapNotNull { ExtensionCatalog.byId(it) }.forEach { install(it, persist = false) }
    }

    /** Memuat status terkini seluruh ekstensi yang terpasang di engine. */
    fun refresh() {
        try {
            controller().list().then<Void>(
                { list ->
                    val all = list.orEmpty()
                    val byId = all.associateBy { it.id }
                    ExtensionCatalog.all.forEach { item ->
                        val we = byId[item.id]
                        val old = infos[item.id]
                        if (we != null) {
                            infos[item.id] = infoOf(we)
                        } else if (old?.status != ExtStatus.ERROR) {
                            infos[item.id] = ExtInfo(ExtStatus.NOT_INSTALLED)
                        }
                    }
                    installed.clear()
                    all.forEach { installed[it.id] = rowOf(it) }
                    null
                },
                { e -> ExtensionCatalog.all.forEach { fail(it.id, e) }; null },
            )
        } catch (e: Exception) {
            ExtensionCatalog.all.forEach { fail(it.id, e) }
        }
    }

    /** Memasang ekstensi bawaan. Pemanggil WAJIB sudah mendapat persetujuan pengguna. */
    fun install(item: BundledExtension, persist: Boolean = true) {
        try {
            controller().ensureBuiltIn(item.uri, item.id).then<Void>(
                { we ->
                    if (we != null) {
                        if (persist) settings.update { it.withExtension(item.id, enabled = true) }
                        infos[item.id] = infoOf(we)
                        installed[we.id] = rowOf(we)
                        if (item.id in settings.state.value.extensionsPrivate) {
                            setPrivateById(item.id, true, persist = false)
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

    /**
     * Memasang ekstensi dari tautan berkas .xpi (Firefox Add-ons). Engine meminta izin lewat dialog; tanpa persetujuan
     * pengguna pemasangan dibatalkan. Tanda tangan ekstensi diverifikasi engine.
     */
    fun installFromUri(uri: String) {
        if (!AmoClient.isAllowedFileUrl(uri)) {
            installMessage = "Sumber ekstensi tidak diizinkan."
            return
        }
        installing = true
        installMessage = null
        try {
            controller().install(uri).then<Void>(
                { we ->
                    installing = false
                    if (we != null) installed[we.id] = rowOf(we)
                    refresh()
                    null
                },
                { e ->
                    installing = false
                    installMessage = "Pemasangan dibatalkan atau gagal: " + (e?.message ?: "tidak diketahui")
                    null
                },
            )
        } catch (e: Exception) {
            installing = false
            installMessage = "Pemasangan gagal: " + (e.message ?: "tidak diketahui")
        }
    }

    fun clearInstallMessage() {
        installMessage = null
    }

    private fun withExtension(id: String, block: (WebExtension) -> Unit) {
        try {
            controller().list().then<Void>(
                { list ->
                    val we = list.orEmpty().firstOrNull { it.id == id }
                    if (we != null) block(we) else { infos[id] = ExtInfo(ExtStatus.NOT_INSTALLED); installed.remove(id) }
                    null
                },
                { e -> fail(id, e); null },
            )
        } catch (e: Exception) {
            fail(id, e)
        }
    }

    private fun applied(id: String, updated: WebExtension?) {
        if (updated == null) return
        installed[id] = rowOf(updated)
        if (ExtensionCatalog.byId(id) != null) infos[id] = infoOf(updated)
    }

    fun setEnabled(item: BundledExtension, on: Boolean) = setEnabledById(item.id, on)

    fun setEnabledById(id: String, on: Boolean) = withExtension(id) { we ->
        val c = controller()
        val r = if (on) {
            c.enable(we, WebExtensionController.EnableSource.USER)
        } else {
            c.disable(we, WebExtensionController.EnableSource.USER)
        }
        r.then<Void>(
            { updated -> applied(id, updated); null },
            { e -> fail(id, e); null },
        )
    }

    /** Mengizinkan ekstensi berjalan di tab privat. Hanya setelah persetujuan eksplisit pengguna. */
    fun setPrivate(item: BundledExtension, allowed: Boolean, persist: Boolean = true) =
        setPrivateById(item.id, allowed, persist)

    fun setPrivateById(id: String, allowed: Boolean, persist: Boolean = true) = withExtension(id) { we ->
        controller().setAllowedInPrivateBrowsing(we, allowed).then<Void>(
            { updated ->
                if (persist && ExtensionCatalog.byId(id) != null) settings.update { it.withExtensionPrivate(id, allowed) }
                applied(id, updated)
                null
            },
            { e -> fail(id, e); null },
        )
    }

    fun remove(item: BundledExtension) = removeById(item.id)

    fun removeById(id: String) = withExtension(id) { we ->
        controller().uninstall(we).then<Void>(
            {
                if (ExtensionCatalog.byId(id) != null) {
                    settings.update { it.withExtension(id, enabled = false).withExtensionPrivate(id, false) }
                    infos[id] = ExtInfo(ExtStatus.NOT_INSTALLED)
                }
                installed.remove(id)
                null
            },
            { e -> fail(id, e); null },
        )
    }
}
