package dev.browsery.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.browser.UrlUtils
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.download.DownloadManager
import dev.browsery.app.download.DownloadSettings
import dev.browsery.app.engine.GeckoRuntimeProvider
import dev.browsery.app.settings.AppSettings
import dev.browsery.app.settings.SearchEngine
import dev.browsery.app.settings.ThemeMode
import kotlinx.coroutines.launch
import org.mozilla.geckoview.StorageController

private enum class Pending { SiteData, Cache, History, Reset }

@Composable
fun SettingsPage(settings: AppSettings, db: AppDatabase, downloads: DownloadManager, onClose: () -> Unit, onAppearance: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by settings.state.collectAsState()
    var pending by remember { mutableStateOf<Pending?>(null) }
    var custom by remember { mutableStateOf(s.customTemplate) }
    var conn by remember { mutableStateOf(downloads.settings.connections) }
    val customValid = UrlUtils.isValidSearchTemplate(custom)
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    fun done(ok: Boolean) {
        Toast.makeText(ctx, if (ok) R.string.clear_done else R.string.clear_failed, Toast.LENGTH_SHORT).show()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PageHeader(stringResource(R.string.settings_title), onClose)
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    OutlinedButton(onClick = onAppearance) { Text(stringResource(R.string.settings_appearance_entry)) }
                }
                Section(stringResource(R.string.settings_search))
                Chips {
                    SearchEngine.entries.forEach { e ->
                        FilterChip(
                            selected = s.searchEngine == e,
                            onClick = { settings.update { it.copy(searchEngine = e) } },
                            label = { Text(if (e == SearchEngine.CUSTOM) stringResource(R.string.settings_search_custom_label) else e.label) },
                        )
                    }
                }
                if (s.searchEngine == SearchEngine.CUSTOM) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = {
                            custom = it
                            settings.update { d -> d.copy(customTemplate = it) }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_custom_template)) },
                        isError = custom.isNotEmpty() && !customValid,
                        supportingText = {
                            if (!customValid) Text(stringResource(R.string.settings_custom_invalid))
                        },
                    )
                    if (!customValid) {
                        Text(
                            stringResource(R.string.settings_custom_inactive),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

                Section(stringResource(R.string.settings_appearance))
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp))
                Chips {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(
                            selected = s.theme == m,
                            onClick = { settings.update { it.copy(theme = m) } },
                            label = {
                                Text(
                                    stringResource(
                                        when (m) {
                                            ThemeMode.SYSTEM -> R.string.theme_system
                                            ThemeMode.LIGHT -> R.string.theme_light
                                            ThemeMode.DARK -> R.string.theme_dark
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_toolbar_bottom), modifier = Modifier.weight(1f))
                    Switch(checked = s.toolbarBottom, onCheckedChange = { v -> settings.update { it.copy(toolbarBottom = v) } })
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                Section(stringResource(R.string.dl_title))
                Text(stringResource(R.string.dl_connections), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp))
                Chips {
                    DownloadSettings.ALLOWED.forEach { v ->
                        FilterChip(
                            selected = conn == v,
                            onClick = {
                                downloads.settings.connections = v
                                conn = v
                            },
                            label = { Text(if (v == 0) stringResource(R.string.dl_conn_auto) else v.toString()) },
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                Section(stringResource(R.string.settings_data))
                Text(stringResource(R.string.clear_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pending = Pending.History }) { Text(stringResource(R.string.clear_history)) }
                }
                Row(modifier = Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pending = Pending.SiteData }) { Text(stringResource(R.string.clear_site_data)) }
                }
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    OutlinedButton(onClick = { pending = Pending.Cache }) { Text(stringResource(R.string.clear_cache)) }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    OutlinedButton(onClick = { pending = Pending.Reset }) { Text(stringResource(R.string.settings_reset)) }
                }
                Section(stringResource(R.string.settings_about))
                Text(
                    stringResource(R.string.settings_version, version),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    pending?.let { p ->
        val text = when (p) {
            Pending.SiteData -> R.string.confirm_clear_site_data
            Pending.Cache -> R.string.confirm_clear_cache
            Pending.History -> R.string.confirm_clear_history
            Pending.Reset -> R.string.confirm_reset
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            text = { Text(stringResource(text)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    when (p) {
                        Pending.History -> scope.launch {
                            db.historyDao().deleteAll()
                            done(true)
                        }
                        Pending.SiteData -> clearGecko(ctx, StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES, ::done)
                        Pending.Cache -> clearGecko(ctx, StorageController.ClearFlags.NETWORK_CACHE or StorageController.ClearFlags.IMAGE_CACHE, ::done)
                        Pending.Reset -> {
                            settings.reset()
                            custom = ""
                        }
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun clearGecko(ctx: android.content.Context, flags: Long, done: (Boolean) -> Unit) {
    try {
        GeckoRuntimeProvider.get(ctx).storageController.clearData(flags).then<Void>(
            { done(true); null },
            { done(false); null },
        )
    } catch (e: Exception) {
        done(false)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
