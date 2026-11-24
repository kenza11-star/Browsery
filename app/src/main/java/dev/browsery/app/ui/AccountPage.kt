package dev.browsery.app.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.browsery.app.BrowseryApp
import dev.browsery.app.MainActivity
import dev.browsery.app.R
import dev.browsery.app.auth.AuthLinks
import dev.browsery.app.auth.AuthManager
import dev.browsery.app.auth.AuthState
import dev.browsery.app.auth.SupabaseConfig
import java.text.DateFormat
import java.util.Date

@Composable
fun AccountPage(auth: AuthManager, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val sync = (ctx.applicationContext as BrowseryApp).sync
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state = auth.state
    val busy = state is AuthState.Busy

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
                Text(stringResource(R.string.acc_title), style = MaterialTheme.typography.headlineSmall)
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (state is AuthState.SignedIn) {
                Text(stringResource(R.string.acc_signed_in_as), style = MaterialTheme.typography.labelLarge)
                Text(state.email.ifBlank { state.userId }, style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { auth.signOut() }) { Text(stringResource(R.string.acc_sign_out)) }

                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.sync_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = sync.enabled, onCheckedChange = { sync.setEnabled(it) })
                }
                Text(stringResource(R.string.sync_consent), style = MaterialTheme.typography.bodySmall)
                if (sync.enabled) {
                    if (sync.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Button(onClick = { sync.syncNow() }, enabled = !sync.busy) {
                        Text(stringResource(R.string.sync_now))
                    }
                    Text(
                        if (sync.lastSyncMs > 0L) {
                            stringResource(
                                R.string.sync_last,
                                DateFormat.getDateTimeInstance().format(Date(sync.lastSyncMs)),
                            )
                        } else {
                            stringResource(R.string.sync_never)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    sync.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    label = { Text(stringResource(R.string.acc_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    label = { Text(stringResource(R.string.acc_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { auth.signIn(email, password) }, enabled = !busy) {
                        Text(stringResource(R.string.acc_sign_in))
                    }
                    OutlinedButton(onClick = { auth.signUp(email, password) }, enabled = !busy) {
                        Text(stringResource(R.string.acc_sign_up))
                    }
                }
                OutlinedButton(
                    onClick = { openGoogle(ctx) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.acc_google)) }
            }

            auth.message?.let { msg ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { auth.clearMessage() }) { Text(stringResource(R.string.dismiss)) }
                }
            }
            Text(stringResource(R.string.acc_limits), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Login Google lewat browser LAIN (bukan Browsery sendiri), agar pengalihan browsery://auth-callback kembali ke aplikasi. */
private fun openGoogle(ctx: android.content.Context) {
    val url = AuthLinks.authorizeUrl(SupabaseConfig.URL, "google", SupabaseConfig.REDIRECT)
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    val chooser = Intent.createChooser(view, null).apply {
        putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(ctx, MainActivity::class.java)))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        ctx.startActivity(chooser)
    } catch (e: android.content.ActivityNotFoundException) {
        // tidak ada browser lain; pengguna melihat pesan di halaman
    }
}
