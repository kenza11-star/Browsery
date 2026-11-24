package dev.browsery.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.browsery.app.browser.UrlUtils
import dev.browsery.app.engine.GeckoRuntimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.settingsStore by preferencesDataStore(name = "settings")

/**
 * Pengaturan aplikasi (DataStore Preferences). Nilai awal dibaca sekali saat start agar tema/mesin pencari
 * langsung benar; perubahan langsung terlihat lewat [state] dan ditulis ke disk di latar belakang.
 * Tidak ada data sensitif di sini dan tidak ada yang disinkronkan.
 */
class AppSettings(context: Context) {
    private val store = context.applicationContext.settingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val kEngine = stringPreferencesKey("search_engine")
    private val kCustom = stringPreferencesKey("custom_template")
    private val kTheme = stringPreferencesKey("theme")
    private val kBottom = booleanPreferencesKey("toolbar_bottom")
    private val kTracking = stringPreferencesKey("tracking_level")
    private val kCookie = stringPreferencesKey("cookie_policy")
    private val kHttpsOnly = booleanPreferencesKey("https_only")
    private val kGpc = booleanPreferencesKey("gpc")
    private val kExceptions = stringSetPreferencesKey("site_exceptions")
    private val kExtEnabled = stringSetPreferencesKey("extensions_enabled")
    private val kExtPrivate = stringSetPreferencesKey("extensions_private")

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsData> = _state

    init {
        UrlUtils.currentTemplate = _state.value.searchTemplate()
        GeckoRuntimeProvider.configure(_state.value)
    }

    private fun load(): SettingsData = try {
        val p = runBlocking { store.data.first() }
        SettingsData(
            searchEngine = SearchEngine.fromId(p[kEngine]),
            customTemplate = p[kCustom].orEmpty(),
            theme = ThemeMode.fromId(p[kTheme]),
            toolbarBottom = p[kBottom] ?: false,
            trackingLevel = TrackingLevel.fromId(p[kTracking]),
            cookiePolicy = CookiePolicy.fromId(p[kCookie]),
            httpsOnly = p[kHttpsOnly] ?: false,
            globalPrivacyControl = p[kGpc] ?: false,
            siteExceptions = p[kExceptions] ?: emptySet(),
            extensionsEnabled = p[kExtEnabled] ?: emptySet(),
            extensionsPrivate = p[kExtPrivate] ?: emptySet(),
        )
    } catch (e: Exception) {
        SettingsData()
    }

    fun update(transform: (SettingsData) -> SettingsData) {
        val prev = _state.value
        val next = transform(prev)
        _state.value = next
        UrlUtils.currentTemplate = next.searchTemplate()
        if (next.trackingLevel != prev.trackingLevel || next.cookiePolicy != prev.cookiePolicy ||
            next.httpsOnly != prev.httpsOnly || next.globalPrivacyControl != prev.globalPrivacyControl
        ) {
            GeckoRuntimeProvider.configure(next)
        }
        scope.launch {
            try {
                store.edit {
                    it[kEngine] = next.searchEngine.id
                    it[kCustom] = next.customTemplate
                    it[kTheme] = next.theme.id
                    it[kBottom] = next.toolbarBottom
                    it[kTracking] = next.trackingLevel.id
                    it[kCookie] = next.cookiePolicy.id
                    it[kHttpsOnly] = next.httpsOnly
                    it[kGpc] = next.globalPrivacyControl
                    it[kExceptions] = next.siteExceptions
                    it[kExtEnabled] = next.extensionsEnabled
                    it[kExtPrivate] = next.extensionsPrivate
                }
            } catch (e: Exception) {
                // Gagal menulis: nilai tetap berlaku selama proses hidup.
            }
        }
    }

    /** Mengembalikan pengaturan ke nilai awal. Pilihan ekstensi (persetujuan pengguna) dipertahankan. */
    fun reset() = update {
        SettingsData(extensionsEnabled = it.extensionsEnabled, extensionsPrivate = it.extensionsPrivate)
    }
}
