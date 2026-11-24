package dev.browsery.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.browser.BrowserController
import dev.browsery.app.settings.AppSettings
import dev.browsery.app.settings.Hosts
import dev.browsery.app.settings.TrackingLevel

/** Panel info situs: asal, status HTTPS menurut engine, jumlah yang diblokir engine, dan kontrol per situs. */
@Composable
fun SiteInfoDialog(controller: BrowserController, settings: AppSettings, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val st = controller.state
    val s by settings.state.collectAsState()
    val host = Hosts.normalize(st.host)
    val globalOn = s.trackingLevel != TrackingLevel.OFF
    val protectedHere = s.protectionFor(host)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.site_title)) },
        text = {
            Column {
                Text(stringResource(R.string.site_host, host ?: "-"), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(if (st.isSecure && !st.isHttp) R.string.site_secure else R.string.site_insecure),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    stringResource(R.string.site_blocked, st.blockedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.site_protection), modifier = Modifier.weight(1f))
                    Switch(
                        checked = protectedHere,
                        enabled = globalOn && host != null,
                        onCheckedChange = { on ->
                            settings.update { it.withException(host, !on) }
                            controller.refreshProtection()
                        },
                    )
                }
                if (!globalOn) {
                    Text(stringResource(R.string.site_protection_global_off), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = {
                        controller.clearSiteData { ok ->
                            Toast.makeText(ctx, if (ok) R.string.site_cleared else R.string.site_clear_failed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text(stringResource(R.string.site_clear)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.site_close)) } },
    )
}
