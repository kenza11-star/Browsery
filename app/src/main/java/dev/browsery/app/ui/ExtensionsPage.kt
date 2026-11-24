package dev.browsery.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.extensions.BundledExtension
import dev.browsery.app.extensions.ExtInfo
import dev.browsery.app.extensions.ExtStatus
import dev.browsery.app.extensions.ExtensionCatalog
import dev.browsery.app.extensions.ExtensionManager

@Composable
fun ExtensionsPage(manager: ExtensionManager, onClose: () -> Unit) {
    LaunchedEffect(Unit) { manager.refresh() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PageHeader(stringResource(R.string.ext_title), onClose)
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Text(
                    stringResource(R.string.ext_intro),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                ExtensionCatalog.all.forEach { item ->
                    ExtensionCard(manager, item, manager.infos[item.id] ?: ExtInfo(ExtStatus.NOT_INSTALLED))
                    HorizontalDivider()
                }
                Text(
                    stringResource(R.string.ext_limits_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                Text(
                    stringResource(R.string.ext_limits),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ExtensionCard(manager: ExtensionManager, item: BundledExtension, info: ExtInfo) {
    var consent by remember { mutableStateOf(false) }
    var privateConsent by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val sites = item.hostAccess.joinToString(", ")

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(item.name, style = MaterialTheme.typography.titleMedium)
        Text(item.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        Text(
            when (info.status) {
                ExtStatus.NOT_INSTALLED -> stringResource(R.string.ext_status_not_installed)
                ExtStatus.ENABLED -> stringResource(R.string.ext_status_enabled, info.version.orEmpty())
                ExtStatus.DISABLED -> stringResource(R.string.ext_status_disabled, info.version.orEmpty())
                ExtStatus.ERROR -> stringResource(R.string.ext_status_error, info.error.orEmpty())
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (info.status == ExtStatus.NOT_INSTALLED || info.status == ExtStatus.ERROR) {
            Button(onClick = { consent = true }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.ext_install))
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ext_active), modifier = Modifier.weight(1f))
                Switch(checked = info.status == ExtStatus.ENABLED, onCheckedChange = { manager.setEnabled(item, it) })
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ext_private))
                    Text(stringResource(R.string.ext_private_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = info.allowedPrivate,
                    onCheckedChange = { on -> if (on) privateConsent = true else manager.setPrivate(item, false) },
                )
            }
            Text(
                stringResource(R.string.ext_permissions),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            val perms = info.permissions
            Text(
                if (perms.isEmpty()) stringResource(R.string.ext_no_permissions) else perms.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
            )
            if (info.origins.isNotEmpty()) {
                Text(stringResource(R.string.ext_sites, info.origins.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { confirmRemove = true }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.ext_remove))
            }
        }
    }

    if (consent) {
        AlertDialog(
            onDismissRequest = { consent = false },
            title = { Text(stringResource(R.string.ext_consent_title, item.name)) },
            text = {
                Column {
                    Text(stringResource(R.string.ext_consent_sites, sites))
                    Text(item.privacyNote, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { consent = false; manager.install(item) }) {
                    Text(stringResource(R.string.ext_consent_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { consent = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (privateConsent) {
        AlertDialog(
            onDismissRequest = { privateConsent = false },
            title = { Text(stringResource(R.string.ext_private_consent_title)) },
            text = { Text(stringResource(R.string.ext_private_desc)) },
            confirmButton = {
                TextButton(onClick = { privateConsent = false; manager.setPrivate(item, true) }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = { TextButton(onClick = { privateConsent = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            text = { Text(stringResource(R.string.ext_remove) + ": " + item.name + "?") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; manager.remove(item) }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
