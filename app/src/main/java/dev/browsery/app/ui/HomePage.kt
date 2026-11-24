package dev.browsery.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.browser.ResolvedInput
import dev.browsery.app.browser.TabManager
import dev.browsery.app.browser.UrlUtils
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.data.ShortcutEntity
import kotlinx.coroutines.launch

/** Tab baru: nama aplikasi dan pintasan buatan pengguna (awalnya kosong). Pencarian lewat kolom alamat di atas. */
@Composable
fun HomePage(tabs: TabManager, db: AppDatabase) {
    val scope = rememberCoroutineScope()
    val shortcuts by remember { db.shortcutDao().observeAll() }.collectAsState(initial = emptyList<ShortcutEntity>())
    var editing by remember { mutableStateOf<ShortcutEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(84.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.home_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(shortcuts, key = { it.id }) { sc ->
            Column(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { tabs.navigate(sc.url) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(sc.title.trim().take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                    }
                }
                Text(
                    sc.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                IconButton(onClick = { editing = sc }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Edit, stringResource(R.string.shortcut_edit), modifier = Modifier.size(14.dp))
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { adding = true }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, stringResource(R.string.shortcut_add))
                    }
                }
                Text(
                    stringResource(R.string.shortcut_add),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    if (adding || editing != null) {
        ShortcutDialog(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { title, url ->
                val current = editing
                scope.launch {
                    if (current == null) {
                        db.shortcutDao().insert(
                            ShortcutEntity(title = title, url = url, position = db.shortcutDao().nextPosition()),
                        )
                    } else {
                        db.shortcutDao().update(current.copy(title = title, url = url))
                    }
                }
                adding = false
                editing = null
            },
            onDelete = {
                val current = editing
                if (current != null) scope.launch { db.shortcutDao().delete(current) }
                adding = false
                editing = null
            },
        )
    }
}

@Composable
private fun ShortcutDialog(
    initial: ShortcutEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, url: String) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.shortcut_add else R.string.shortcut_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.field_title)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = false },
                    label = { Text(stringResource(R.string.field_url)) },
                    singleLine = true,
                    isError = error,
                    supportingText = { if (error) Text(stringResource(R.string.invalid_url)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                if (initial != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val resolved = UrlUtils.resolve(url)
                if (resolved is ResolvedInput.Navigate && resolved.url != UrlUtils.BLANK) {
                    onSave(title.trim().ifBlank { resolved.url }, resolved.url)
                } else {
                    error = true
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
