package dev.browsery.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.browser.BrowserController
import org.mozilla.geckoview.GeckoSession

@Composable
fun FindBar(controller: BrowserController, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val none = stringResource(R.string.find_none)
    val finder = controller.session.finder

    DisposableEffect(controller) {
        onDispose { finder.clear() }
    }

    fun run(backwards: Boolean) {
        if (text.isBlank()) {
            finder.clear()
            info = ""
            return
        }
        val result = if (backwards) {
            finder.find(text, GeckoSession.FINDER_FIND_BACKWARDS)
        } else {
            finder.find(text, 0)
        }
        result.accept { r ->
            info = when {
                r == null -> ""
                !r.found -> none
                else -> "${r.current}/${r.total}"
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.find_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run(false) }),
            )
            Text(info, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp))
            IconButton(onClick = { run(true) }) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.find_prev))
            }
            IconButton(onClick = { run(false) }) {
                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.find_next))
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, stringResource(R.string.find_close))
            }
        }
    }
}
