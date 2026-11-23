package dev.browsery.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.browsery.app.browser.UrlUtils
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

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsData> = _state

    init {
        UrlUtils.currentTemplate = _state.value.searchTemplate()
    }

    private fun load(): SettingsData = try {
        val p = runBlocking { store.data.first() }
        SettingsData(
            searchEngine = SearchEngine.fromId(p[kEngine]),
            customTemplate = p[kCustom].orEmpty(),
            theme = ThemeMode.fromId(p[kTheme]),
            toolbarBottom = p[kBottom] ?: false,
        )
    } catch (e: Exception) {
        SettingsData()
    }

    fun update(transform: (SettingsData) -> SettingsData) {
        val next = transform(_state.value)
        _state.value = next
        UrlUtils.currentTemplate = next.searchTemplate()
        scope.launch {
            try {
                store.edit {
                    it[kEngine] = next.searchEngine.id
                    it[kCustom] = next.customTemplate
                    it[kTheme] = next.theme.id
                    it[kBottom] = next.toolbarBottom
                }
            } catch (e: Exception) {
                // Gagal menulis: nilai tetap berlaku selama proses hidup.
            }
        }
    }

    fun reset() = update { SettingsData() }
}
