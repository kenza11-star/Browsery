package dev.browsery.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.extensions.ExtensionManager

/** Deskripsi izin dalam bahasa Indonesia untuk izin yang umum; yang lain ditampilkan apa adanya. */
private fun describe(p: String): String = when (p) {
    "<all_urls>", "*://*/*", "http://*/*", "https://*/*" -> "Membaca dan mengubah semua situs ($p)"
    "tabs" -> "Melihat tab yang terbuka (tabs)"
    "activeTab" -> "Mengakses tab aktif saat dipakai (activeTab)"
    "webRequest" -> "Melihat permintaan jaringan (webRequest)"
    "webRequestBlocking" -> "Memblokir/mengubah permintaan jaringan (webRequestBlocking)"
    "declarativeNetRequest" -> "Memblokir permintaan jaringan lewat aturan (declarativeNetRequest)"
    "storage" -> "Menyimpan data ekstensi (storage)"
    "unlimitedStorage" -> "Penyimpanan besar (unlimitedStorage)"
    "cookies" -> "Membaca dan mengubah cookie (cookies)"
    "history" -> "Membaca riwayat (history)"
    "bookmarks" -> "Membaca dan mengubah bookmark (bookmarks)"
    "downloads" -> "Mengelola unduhan (downloads)"
    "notifications" -> "Menampilkan notifikasi (notifications)"
    "clipboardWrite" -> "Menulis ke papan klip (clipboardWrite)"
    "clipboardRead" -> "Membaca papan klip (clipboardRead)"
    "privacy" -> "Mengubah pengaturan privasi (privacy)"
    "proxy" -> "Mengatur proxy (proxy)"
    else -> p
}

/** Dialog persetujuan izin ekstensi. Dipasang di akar aplikasi agar permintaan dari engine selalu punya jawaban pengguna. */
@Composable
fun ExtensionPromptHost(manager: ExtensionManager) {
    val p = manager.pending ?: return
    var allowPrivate by remember(p) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { manager.answerPending(false, false) },
        title = { Text(stringResource(R.string.ext_prompt_title, p.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.ext_prompt_perms), style = MaterialTheme.typography.labelLarge)
                val all = p.permissions + p.origins
                if (all.isEmpty()) {
                    Text(stringResource(R.string.ext_prompt_none), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                } else {
                    all.forEach { Text("• " + describe(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Checkbox(checked = allowPrivate, onCheckedChange = { allowPrivate = it })
                    Text(stringResource(R.string.ext_prompt_private), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.ext_prompt_warn), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { manager.answerPending(true, allowPrivate) }) { Text(stringResource(R.string.ext_consent_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { manager.answerPending(false, false) }) { Text(stringResource(R.string.cancel)) }
        },
    )
}
