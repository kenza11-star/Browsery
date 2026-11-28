package dev.browsery.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import dev.browsery.app.R
import dev.browsery.app.extensions.AmoAddon
import dev.browsery.app.extensions.AmoClient
import dev.browsery.app.extensions.BundledExtension
import dev.browsery.app.extensions.ExtRow
import dev.browsery.app.extensions.ExtensionCatalog
import dev.browsery.app.extensions.ExtensionManager
import kotlinx.coroutines.launch

@Composable
fun ExtensionsPage(manager: ExtensionManager, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AmoAddon>>(emptyList()) }
    var featured by remember { mutableStateOf<List<AmoAddon>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var privateTarget by remember { mutableStateOf<ExtRow?>(null) }
    var removeTarget by remember { mutableStateOf<ExtRow?>(null) }
    var bundledTarget by remember { mutableStateOf<BundledExtension?>(null) }
    val netErrorFmt = stringResource(R.string.ext_net_error, "%s")
    val linkBad = stringResource(R.string.ext_link_bad)

    LaunchedEffect(Unit) {
        manager.refresh()
        // Rekomendasi diambil nyata dari AMO; bila gagal, bagian ini tidak ditampilkan (tanpa data tiruan).
        featured = listOf("ublock-origin", "darkreader").mapNotNull { slug ->
            try {
                AmoClient.bySlug(slug)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun runSearch() {
        scope.launch {
            loading = true
            error = null
            try {
                results = AmoClient.search(query.trim())
                searched = true
            } catch (e: Exception) {
                results = emptyList()
                error = netErrorFmt.replace("%s", e.message ?: "?")
            } finally {
                loading = false
            }
        }
    }

    fun installLink() {
        scope.launch {
            error = null
            try {
                val u = AmoClient.resolveInstallUrl(link)
                if (u == null) error = linkBad else manager.installFromUri(u)
            } catch (e: Exception) {
                error = netErrorFmt.replace("%s", e.message ?: "?")
            }
        }
    }

    val installedGuids = manager.installed.keys

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PageHeader(stringResource(R.string.ext_title), onClose)
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.ext_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Column(Modifier.fillMaxWidth().glassSurface().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.ext_amo_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(24.dp)),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        placeholder = { Text(stringResource(R.string.ext_search_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                        colors = transparentFieldColors(),
                    )
                    Button(onClick = { runSearch() }, enabled = !loading) { Text(stringResource(R.string.ext_search)) }
                    if (loading) Text(stringResource(R.string.app_busy), style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    manager.installMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (manager.installing) Text(stringResource(R.string.ext_installing), style = MaterialTheme.typography.bodySmall)
                    if (searched && results.isEmpty() && !loading && error == null) {
                        Text(stringResource(R.string.ext_search_empty), style = MaterialTheme.typography.bodySmall)
                    }
                    results.forEach { a -> AddonRow(a, a.guid in installedGuids || manager.installed.containsKey(a.guid)) { manager.installFromUri(a.fileUrl) } }
                }

                if (featured.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().glassSurface().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.ext_featured), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.ext_featured_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        featured.forEach { a -> AddonRow(a, manager.installed.containsKey(a.guid)) { manager.installFromUri(a.fileUrl) } }
                    }
                }

                Column(Modifier.fillMaxWidth().glassSurface().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.ext_by_link), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    TextField(
                        value = link,
                        onValueChange = { link = it },
                        modifier = Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(24.dp)),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.ext_link_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { installLink() }),
                        colors = transparentFieldColors(),
                    )
                    Button(onClick = { installLink() }, enabled = link.isNotBlank() && !manager.installing) { Text(stringResource(R.string.ext_install)) }
                }

                Column(Modifier.fillMaxWidth().glassSurface().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.ext_installed_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    val rows = manager.installed.values.sortedBy { it.name.lowercase() }
                    if (rows.isEmpty()) Text(stringResource(R.string.ext_none_installed), style = MaterialTheme.typography.bodySmall)
                    rows.forEach { r ->
                        InstalledRow(
                            r,
                            onEnabled = { manager.setEnabledById(r.id, it) },
                            onPrivate = { on -> if (on) privateTarget = r else manager.setPrivateById(r.id, false) },
                            onRemove = { removeTarget = r },
                        )
                    }
                }

                val pendingBundled = ExtensionCatalog.all.filter { !manager.installed.containsKey(it.id) }
                if (pendingBundled.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().glassSurface().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.ext_bundled_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        pendingBundled.forEach { b ->
                            Column {
                                Text(b.name, style = MaterialTheme.typography.titleMedium)
                                Text(b.description, style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { bundledTarget = b }, modifier = Modifier.padding(top = 6.dp)) { Text(stringResource(R.string.ext_install)) }
                            }
                        }
                    }
                }

                Text(stringResource(R.string.ext_limits_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.ext_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }

    privateTarget?.let { r ->
        AlertDialog(
            onDismissRequest = { privateTarget = null },
            title = { Text(stringResource(R.string.ext_private_consent_title)) },
            text = { Text(stringResource(R.string.ext_private_desc)) },
            confirmButton = { TextButton(onClick = { privateTarget = null; manager.setPrivateById(r.id, true) }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { privateTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    removeTarget?.let { r ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            text = { Text(stringResource(R.string.ext_remove) + ": " + r.name + "?") },
            confirmButton = { TextButton(onClick = { removeTarget = null; manager.removeById(r.id) }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    bundledTarget?.let { b ->
        AlertDialog(
            onDismissRequest = { bundledTarget = null },
            title = { Text(stringResource(R.string.ext_consent_title, b.name)) },
            text = {
                Column {
                    Text(stringResource(R.string.ext_consent_sites, b.hostAccess.joinToString(", ")))
                    Text(b.privacyNote, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { bundledTarget = null; manager.install(b) }) { Text(stringResource(R.string.ext_consent_confirm)) } },
            dismissButton = { TextButton(onClick = { bundledTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
)

@Composable
private fun AddonRow(a: AmoAddon, isInstalled: Boolean, onInstall: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (a.summary.isNotEmpty()) Text(a.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.ext_users, a.users.toString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isInstalled) {
            Text(stringResource(R.string.ext_already), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(onClick = onInstall) { Text(stringResource(R.string.ext_install)) }
        }
    }
}

@Composable
private fun InstalledRow(r: ExtRow, onEnabled: (Boolean) -> Unit, onPrivate: (Boolean) -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    r.version + if (r.builtIn) " · " + stringResource(R.string.ext_builtin_tag) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = r.enabled, onCheckedChange = onEnabled)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ext_private), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Switch(checked = r.allowedPrivate, onCheckedChange = onPrivate)
        }
        val perms = r.permissions + r.origins
        Text(
            if (perms.isEmpty()) stringResource(R.string.ext_no_permissions) else perms.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.ext_remove)) }
    }
}
