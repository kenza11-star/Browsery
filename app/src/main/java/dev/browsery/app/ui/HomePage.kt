package dev.browsery.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.browsery.app.BrowseryApp
import dev.browsery.app.R
import dev.browsery.app.browser.ResolvedInput
import dev.browsery.app.browser.TabManager
import dev.browsery.app.browser.UrlUtils
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.data.ShortcutEntity
import kotlinx.coroutines.launch

/** Tab baru: logo Saturnus + nama, pintasan kaca buatan pengguna. Pencarian lewat kolom alamat di atas. */
@Composable
fun HomePage(tabs: TabManager, db: AppDatabase) {
    val scope = rememberCoroutineScope()
    val wp = (LocalContext.current.applicationContext as BrowseryApp).wallpaper
    val onWallpaper = wp.sharp != null
    val ink = if (onWallpaper) Color.White else MaterialTheme.colorScheme.onBackground
    val shortcuts by remember { db.shortcutDao().observeAll() }.collectAsState(initial = emptyList<ShortcutEntity>())
    var editing by remember { mutableStateOf<ShortcutEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val tile = RoundedCornerShape(20.dp)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(84.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                SaturnLogo(
                    modifier = Modifier.size(56.dp),
                    ring = ink,
                    planet = ink,
                    edge = if (onWallpaper) Color(0xFF0B1220) else MaterialTheme.colorScheme.surface,
                )
                Text(
                    stringResource(R.string.home_title),
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        shadow = if (onWallpaper) Shadow(Color(0x66000000), Offset(0f, 3f), 14f) else null,
                    ),
                    color = ink,
                )
            }
        }
        items(shortcuts, key = { it.id }) { sc ->
            Column(
                modifier = Modifier.animateItem().padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(60.dp).glassSurface(tile).pressScale({ tabs.navigate(sc.url) }, 0.92f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(sc.title.trim().take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = ink)
                }
                Text(
                    sc.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                IconButton(onClick = { editing = sc }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Edit, stringResource(R.string.shortcut_edit), tint = ink, modifier = Modifier.size(14.dp))
                }
            }
        }
        item {
            Column(
                modifier = Modifier.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(60.dp).glassSurface(tile).pressScale({ adding = true }, 0.92f),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.shortcut_add), tint = ink)
                }
                Text(
                    stringResource(R.string.shortcut_add),
                    style = MaterialTheme.typography.labelMedium,
                    color = ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
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
        shape = Glass.Card,
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
