package dev.browsery.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.browser.TabManager
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.data.BookmarkEntity
import dev.browsery.app.data.HistoryEntity
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** Header halaman: tombol kembali, judul tebal, aksi. Dipakai semua halaman non-web. */
@Composable
fun PageHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        actions()
    }
}

@Composable
private fun ConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Glass.Card,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_all)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun BookmarksPage(db: AppDatabase, tabs: TabManager, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val items by remember(query) { db.bookmarkDao().observe(query) }
        .collectAsState(initial = emptyList<BookmarkEntity>())
    var confirm by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PageHeader(stringResource(R.string.bookmarks_title), onClose) {
                TextButton(onClick = { confirm = true }, enabled = items.isNotEmpty()) {
                    Text(stringResource(R.string.delete_all))
                }
            }
            SearchField(query) { query = it }
            if (items.isEmpty()) {
                Text(
                    stringResource(if (query.isEmpty()) R.string.empty_bookmarks else R.string.empty_search),
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { b ->
                    ListItem(
                        headlineContent = { Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(b.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { db.bookmarkDao().deleteById(b.id) } }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.animateItem().glassSurface().pressScale({ tabs.navigate(b.url); onClose() }, 0.98f),
                    )
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            text = stringResource(R.string.confirm_clear_bookmarks),
            onConfirm = { confirm = false; scope.launch { db.bookmarkDao().deleteAll() } },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
fun HistoryPage(db: AppDatabase, tabs: TabManager, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val items by remember(query) { db.historyDao().observe(query) }
        .collectAsState(initial = emptyList<HistoryEntity>())
    var confirm by remember { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PageHeader(stringResource(R.string.history_title), onClose) {
                TextButton(onClick = { confirm = true }, enabled = items.isNotEmpty()) {
                    Text(stringResource(R.string.delete_all))
                }
            }
            SearchField(query) { query = it }
            if (items.isEmpty()) {
                Text(
                    stringResource(if (query.isEmpty()) R.string.empty_history else R.string.empty_search),
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { h ->
                    ListItem(
                        headlineContent = { Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(fmt.format(Date(h.visitedAt)) + " · " + h.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { db.historyDao().deleteById(h.id) } }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.animateItem().glassSurface().pressScale({ tabs.navigate(h.url); onClose() }, 0.98f),
                    )
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            text = stringResource(R.string.confirm_clear_history),
            onConfirm = { confirm = false; scope.launch { db.historyDao().deleteAll() } },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        singleLine = true,
        shape = Glass.Card,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}
