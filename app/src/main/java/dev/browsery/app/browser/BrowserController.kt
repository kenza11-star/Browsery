package dev.browsery.app.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.browsery.app.BrowseryApp
import dev.browsery.app.R
import dev.browsery.app.download.DownloadRequest
import dev.browsery.app.download.FileNames
import dev.browsery.app.engine.GeckoRuntimeProvider
import dev.browsery.app.settings.Hosts
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

data class BrowserState(
    val url: String = "",
    val title: String = "",
    val started: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val isSecure: Boolean = false,
    val isFullScreen: Boolean = false,
    val message: String? = null,
    /** Jumlah sumber daya yang DIBLOKIR menurut laporan engine pada halaman ini (bukan perkiraan). */
    val blockedCount: Int = 0,
) {
    /** Teks untuk address bar; kosong untuk halaman kosong/halaman error internal. */
    val displayUrl: String
        get() = if (url == UrlUtils.BLANK || url.startsWith("data:")) "" else url

    val isHttp: Boolean get() = url.startsWith("http://", ignoreCase = true)

    val isWebUrl: Boolean
        get() = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    val host: String? get() = if (isWebUrl) Uri.parse(url).host else null
}

/**
 * Pemilik satu GeckoSession (satu tab). State UI dipisahkan dari engine: Compose hanya membaca
 * [state]; rotasi/recomposition tidak membuat ulang session.
 */
class BrowserController(context: Context, val isPrivate: Boolean = false) {
    private val appContext = context.applicationContext
    private val app get() = appContext as? BrowseryApp

    val session = GeckoSession(
        GeckoSessionSettings.Builder().usePrivateMode(isPrivate).build(),
    )

    var state by mutableStateOf(BrowserState())
        private set

    var onFullScreenChange: ((Boolean) -> Unit)? = null

    /** Dipanggil saat lokasi/judul berubah (untuk menyimpan sesi). */
    var onChanged: (() -> Unit)? = null

    /** Dipanggil saat halaman web selesai dimuat dengan sukses (untuk riwayat; TabManager memfilter privat). */
    var onPageLoaded: ((url: String, title: String) -> Unit)? = null

    private var failedUrl: String? = null
    private var lastUrl: String = UrlUtils.BLANK
    private var recoverCount = 0

    private val navigationDelegate = object : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            val failed = failedUrl
            val shown = if (url != null && url.startsWith("data:text/html") && failed != null) failed else (url ?: "")
            if (!shown.startsWith("data:")) lastUrl = shown
            state = state.copy(url = shown, message = null, started = true)
            onChanged?.invoke()
            // Terapkan pengecualian per situs: bila perlu berubah, muat ulang sekali.
            if (url != null && state.isWebUrl && syncProtection()) session.reload()
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            state = state.copy(canGoBack = canGoBack)
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            state = state.copy(canGoForward = canGoForward)
        }

        override fun onLoadRequest(
            session: GeckoSession,
            request: GeckoSession.NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny>? {
            val uri = request.uri
            val scheme = uri.substringBefore(':', "").lowercase()
            if (!uri.startsWith("data:")) failedUrl = null
            return when {
                scheme == "tel" || scheme == "mailto" || scheme == "sms" -> {
                    launchExternal(uri)
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                scheme == "intent" -> {
                    state = state.copy(message = appContext.getString(R.string.msg_invalid_input))
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                // Popup / window.open: dibuka di tab saat ini.
                request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW &&
                    (scheme == "http" || scheme == "https") -> {
                    session.loadUri(uri)
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                else -> null
            }
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? = null

        override fun onLoadError(
            session: GeckoSession,
            uri: String?,
            error: WebRequestError,
        ): GeckoResult<String>? {
            failedUrl = uri
            return GeckoResult.fromValue(errorPageUri(uri, error))
        }
    }

    private val progressDelegate = object : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            state = state.copy(isLoading = true, progress = 0, blockedCount = 0)
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            if (success) recoverCount = 0
            state = state.copy(isLoading = false, progress = if (success) 100 else state.progress)
            val s = state
            if (success && s.isWebUrl) onPageLoaded?.invoke(s.url, s.title)
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            state = state.copy(progress = progress)
        }

        override fun onSecurityChange(
            session: GeckoSession,
            securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
        ) {
            state = state.copy(isSecure = securityInfo.isSecure)
        }
    }

    private val contentDelegate = object : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            state = state.copy(title = title ?: "")
            onChanged?.invoke()
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            state = state.copy(isFullScreen = fullScreen)
            onFullScreenChange?.invoke(fullScreen)
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            handleDownload(response)
        }

        override fun onCrash(session: GeckoSession) = recover()

        override fun onKill(session: GeckoSession) = recover()
    }

    private val blockingDelegate = object : ContentBlocking.Delegate {
        override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
            if (event.isBlocking) state = state.copy(blockedCount = state.blockedCount + 1)
        }
    }

    init {
        session.navigationDelegate = navigationDelegate
        session.progressDelegate = progressDelegate
        session.contentDelegate = contentDelegate
        session.contentBlockingDelegate = blockingDelegate
    }

    /** Membuka session (sekali). Tidak memuat apa pun. */
    fun attach() {
        if (!session.isOpen) {
            session.open(GeckoRuntimeProvider.get(appContext))
        }
    }

    fun submit(input: String) {
        when (val r = UrlUtils.resolve(input)) {
            ResolvedInput.Empty -> Unit
            is ResolvedInput.Navigate -> load(r.url)
            is ResolvedInput.Search -> load(r.url)
            is ResolvedInput.External -> launchExternal(r.url)
            ResolvedInput.Rejected ->
                state = state.copy(message = appContext.getString(R.string.msg_invalid_input))
        }
    }

    /** Memuat URL yang sudah tervalidasi (restore sesi). */
    fun loadUrl(url: String) = load(url)

    fun goBack() = session.goBack()

    fun goForward() = session.goForward()

    fun stop() = session.stop()

    fun reload() {
        val failed = failedUrl
        if (failed != null) {
            failedUrl = null
            session.loadUri(failed)
        } else {
            session.reload()
        }
    }

    fun exitFullScreen() = session.exitFullScreen()

    fun dismissMessage() {
        state = state.copy(message = null)
    }

    /** Dipanggil setelah pengecualian situs diubah dari panel info situs: terapkan lalu muat ulang bila berubah. */
    fun refreshProtection() {
        if (syncProtection()) session.reload()
    }

    /** Menghapus cookie dan data situs untuk host halaman saat ini. Hasil dilaporkan lewat [done]. */
    fun clearSiteData(done: (Boolean) -> Unit) {
        val host = Hosts.normalize(state.host)
        if (host == null) {
            done(false)
            return
        }
        try {
            val flags = StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES or
                StorageController.ClearFlags.NETWORK_CACHE or StorageController.ClearFlags.IMAGE_CACHE
            GeckoRuntimeProvider.get(appContext).storageController.clearDataFromBaseDomain(host, flags).then<Void>(
                { done(true); null },
                { done(false); null },
            )
        } catch (e: Exception) {
            done(false)
        }
    }

    fun close() {
        onFullScreenChange = null
        onChanged = null
        onPageLoaded = null
        if (session.isOpen) session.close()
    }

    /** Menyamakan tracking protection session dengan pengaturan global + pengecualian situs. True bila berubah. */
    private fun syncProtection(): Boolean {
        val settings = app?.settings?.state?.value ?: return false
        val want = settings.protectionFor(state.host)
        if (session.settings.useTrackingProtection == want) return false
        session.settings.useTrackingProtection = want
        return true
    }

    private fun load(url: String) {
        failedUrl = null
        lastUrl = url
        state = state.copy(message = null, started = true)
        session.loadUri(url)
    }

    /**
     * Respons yang tidak bisa ditampilkan (berkas) diserahkan ke DownloadManager. Tab privat tidak mengunduh
     * (agar tidak ada jejak). Cookie/otorisasi sesi tidak ikut terbawa ke mesin unduhan (batasan Stage 5).
     */
    private fun handleDownload(response: WebResponse) {
        try {
            val uri = response.uri
            val scheme = uri.substringBefore(':', "").lowercase()
            val headers = response.headers.entries.associate { it.key.lowercase() to it.value }
            when {
                isPrivate -> toast(R.string.dl_private_blocked)
                scheme != "http" && scheme != "https" -> toast(R.string.dl_unsupported_scheme)
                else -> {
                    val name = FileNames.sanitize(
                        FileNames.fromContentDisposition(headers["content-disposition"])
                            ?: FileNames.fromUrl(uri),
                    )
                    val mime = headers["content-type"]?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
                    val downloads = app?.downloads
                    if (downloads != null) {
                        downloads.enqueue(DownloadRequest(uri, lastUrl.takeIf { it.startsWith("http") }, name, mime))
                        Toast.makeText(appContext, appContext.getString(R.string.dl_started, name), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } finally {
            try {
                response.body?.close()
            } catch (e: Exception) {
                // sudah ditutup
            }
        }
    }

    private fun toast(res: Int) {
        Toast.makeText(appContext, res, Toast.LENGTH_LONG).show()
    }

    private fun launchExternal(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            state = state.copy(message = appContext.getString(R.string.msg_no_app))
        }
    }

    /** Pulihkan session setelah proses konten crash/dibunuh; berhenti mengulang setelah 2 kali. */
    private fun recover() {
        state = state.copy(isLoading = false, message = appContext.getString(R.string.page_process_stopped))
        session.open(GeckoRuntimeProvider.get(appContext))
        recoverCount++
        session.loadUri(if (recoverCount > 2) UrlUtils.BLANK else lastUrl)
    }

    private fun errorPageUri(uri: String?, error: WebRequestError): String {
        val (titleRes, detailRes) = when {
            error.code == WebRequestError.ERROR_OFFLINE ->
                R.string.err_offline_title to R.string.err_offline_detail
            error.code == WebRequestError.ERROR_UNKNOWN_HOST ->
                R.string.err_host_title to R.string.err_host_detail
            error.code == WebRequestError.ERROR_NET_TIMEOUT ->
                R.string.err_timeout_title to R.string.err_timeout_detail
            error.code == WebRequestError.ERROR_CONNECTION_REFUSED ->
                R.string.err_refused_title to R.string.err_refused_detail
            error.category == WebRequestError.ERROR_CATEGORY_SECURITY ||
                error.code == WebRequestError.ERROR_SECURITY_BAD_CERT ||
                error.code == WebRequestError.ERROR_BAD_HSTS_CERT ->
                R.string.err_security_title to R.string.err_security_detail
            else -> R.string.err_generic_title to R.string.err_generic_detail
        }
        val title = appContext.getString(titleRes).escapeHtml()
        val detail = appContext.getString(detailRes).escapeHtml()
        val html = "<!doctype html><html><head><meta charset='utf-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<style>body{font-family:sans-serif;margin:24px;color:#222;background:#fff}" +
            "@media(prefers-color-scheme:dark){body{color:#eee;background:#121212}}" +
            "h1{font-size:20px}p{line-height:1.5}code{word-break:break-all}</style></head>" +
            "<body><h1>$title</h1><p>$detail</p><p><code>${(uri ?: "").escapeHtml()}</code></p></body></html>"
        return "data:text/html;charset=utf-8," + Uri.encode(html)
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
}
