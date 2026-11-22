package dev.browsery.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.browser.BrowserController
import dev.browsery.app.browser.BrowserState
import dev.browsery.app.engine.GeckoHost

@Composable
fun BrowserScreen(controller: BrowserController) {
    val state = controller.state
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = state.isFullScreen || state.canGoBack) {
        if (state.isFullScreen) controller.exitFullScreen() else controller.goBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (state.isFullScreen) Modifier else Modifier.safeDrawingPadding()),
        ) {
            if (!state.isFullScreen) {
                AddressBar(state, controller, onSubmitted = { focusManager.clearFocus() })
                if (state.isLoading) {
                    LinearProgressIndicator(
                        progress = { maxOf(state.progress, 5) / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                    )
                }
                state.message?.let { MessageBar(it, onDismiss = controller::dismissMessage) }
            }
            GeckoHost(
                session = controller.session,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun AddressBar(
    state: BrowserState,
    controller: BrowserController,
    onSubmitted: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = controller::goBack, enabled = state.canGoBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
        }
        IconButton(onClick = controller::goForward, enabled = state.canGoForward) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.action_forward))
        }
        OutlinedTextField(
            value = if (focused) draft else state.displayUrl,
            onValueChange = { draft = it },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { f ->
                    if (f.isFocused && !focused) draft = state.displayUrl
                    focused = f.isFocused
                },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text(stringResource(R.string.address_hint)) },
            leadingIcon = { SecurityIcon(state) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                controller.submit(draft)
                onSubmitted()
            }),
        )
        if (state.isLoading) {
            IconButton(onClick = controller::stop) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_stop))
            }
        } else {
            IconButton(onClick = controller::reload) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.action_reload))
            }
        }
    }
}

@Composable
private fun SecurityIcon(state: BrowserState) {
    when {
        state.displayUrl.isEmpty() -> Icon(Icons.Filled.Search, contentDescription = null)
        state.isSecure && !state.isHttp ->
            Icon(Icons.Filled.Lock, stringResource(R.string.secure_connection))
        else -> Icon(Icons.Filled.Warning, stringResource(R.string.not_secure))
    }
}

@Composable
private fun MessageBar(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
    }
}
