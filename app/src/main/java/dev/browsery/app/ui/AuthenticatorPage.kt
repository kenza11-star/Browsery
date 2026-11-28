package dev.browsery.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.browsery.app.R
import dev.browsery.app.otp.Base32
import dev.browsery.app.otp.OtpAlgorithm
import dev.browsery.app.otp.OtpEntry
import dev.browsery.app.otp.OtpStore
import dev.browsery.app.otp.OtpUri
import dev.browsery.app.otp.Totp
import kotlinx.coroutines.delay

/** Authenticator lokal: kode TOTP dihitung di perangkat. Layar ini ditandai FLAG_SECURE (tanpa screenshot/rekam layar). */
@Composable
fun AuthenticatorPage(store: OtpStore, onClose: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { store.load() }
    DisposableEffect(Unit) {
        val w = ctx.findActivity()?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<OtpEntry?>(null) }
    var deleting by remember { mutableStateOf<OtpEntry?>(null) }
    val copiedMsg = stringResource(R.string.otp_copied)

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            PageHeader(stringResource(R.string.otp_title), onClose) {
                IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, stringResource(R.string.otp_add)) }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(stringResource(R.string.otp_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                store.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
                if (store.entries.isEmpty() && store.error == null) {
                    item { Text(stringResource(R.string.otp_empty), modifier = Modifier.padding(vertical = 24.dp)) }
                }
                items(store.entries, key = { it.id }) { e ->
                    val code = Totp.codeFor(e, now) ?: "------"
                    val left = Totp.secondsRemaining(now, e.period)
                    Row(
                        Modifier.fillMaxWidth().glassSurface().pressScale({ copy(ctx, code); Toast.makeText(ctx, copiedMsg, Toast.LENGTH_SHORT).show() }, 0.98f).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            if (e.issuer.isNotBlank() && e.account.isNotBlank()) {
                                Text(e.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            val shown = if (code.length == 6) code.substring(0, 3) + " " + code.substring(3) else code
                            Text(
                                shown,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                color = if (left <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            Text(stringResource(R.string.otp_tap_copy), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                            CircularProgressIndicator(progress = { left / e.period.toFloat() }, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                            Text(left.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                        Column {
                            IconButton(onClick = { renaming = e }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Edit, stringResource(R.string.otp_edit), modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { deleting = e }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.otp_delete), modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.otp_notes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }

    if (adding) AddDialog(store, onDismiss = { adding = false })
    renaming?.let { e -> RenameDialog(e, onDismiss = { renaming = null }) { i, a -> store.rename(e.id, i, a); renaming = null } }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.otp_delete_confirm, e.title)) },
            confirmButton = { TextButton(onClick = { store.remove(e.id); deleting = null }) { Text(stringResource(R.string.otp_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

private fun copy(ctx: Context, code: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("otp", code)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    }
    cm.setPrimaryClip(clip)
}

@Composable
private fun AddDialog(store: OtpStore, onDismiss: () -> Unit) {
    var secret by remember { mutableStateOf("") }
    var issuer by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf(6) }
    var alg by remember { mutableStateOf(OtpAlgorithm.SHA1) }
    var err by remember { mutableStateOf<String?>(null) }
    val invalid = stringResource(R.string.otp_invalid_secret)
    val dup = stringResource(R.string.otp_duplicate)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.otp_dialog_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(secret, { secret = it }, label = { Text(stringResource(R.string.otp_secret_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(issuer, { issuer = it }, label = { Text(stringResource(R.string.otp_issuer_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(account, { account = it }, label = { Text(stringResource(R.string.otp_account_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.otp_digits), style = MaterialTheme.typography.labelMedium)
                    listOf(6, 8).forEach { d -> FilterChip(selected = digits == d, onClick = { digits = d }, label = { Text(d.toString()) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OtpAlgorithm.entries.forEach { a -> FilterChip(selected = alg == a, onClick = { alg = a }, label = { Text(a.id) }) }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                val t = secret.trim()
                val entry = if (t.startsWith("otpauth://", ignoreCase = true)) {
                    OtpUri.parse(t)
                } else {
                    val s = Base32.normalize(t)
                    if (OtpUri.validSecret(s)) OtpEntry(issuer = issuer.trim(), account = account.trim(), secret = s, digits = digits, algorithm = alg) else null
                }
                if (entry == null) {
                    err = invalid
                } else if (store.add(entry)) {
                    onDismiss()
                } else {
                    err = store.error ?: dup
                }
            }) { Text(stringResource(R.string.otp_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun RenameDialog(e: OtpEntry, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var issuer by remember { mutableStateOf(e.issuer) }
    var account by remember { mutableStateOf(e.account) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.otp_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(issuer, { issuer = it }, label = { Text(stringResource(R.string.otp_issuer_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(account, { account = it }, label = { Text(stringResource(R.string.otp_account_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(issuer, account) }) { Text(stringResource(R.string.otp_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
