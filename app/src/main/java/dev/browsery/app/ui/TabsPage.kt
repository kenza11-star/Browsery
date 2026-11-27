package dev.browsery.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.browser.TabManager
import dev.browsery.app.engine.ThumbnailStore

/** Pengalih tab kaca: grid dua kolom, gambar mini halaman nyata (bila ada), pencarian, tab biasa/privat. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabsPage(tabs: TabManager, onClose: () -> Unit) {
    val s = tabs.state
    var showPrivate by remember { mutableStateOf(s.active?.isPrivate == true) }
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val thumbVersion = ThumbnailStore.version
    val visible = s.tabs.filter { rec ->
        rec.isPrivate == showPrivate &&
            (q.isEmpty() ||
                tabs.displayTitle(rec).contains(q, ignoreCase = true) ||
                tabs.displayUrl(rec).contains(q, ignoreCase = true))
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
                FilledTonalIconButton(onClick = { tabs.newTab(showPrivate); onClose() }) {
                    Icon(Icons.Filled.Add, stringResource(if (showPrivate) R.string.menu_new_private else R.string.menu_new_tab))
                }
                FilterChip(
                    selected = !showPrivate,
                    onClick = { showPrivate = false },
                    label = { Text(stringResource(R.string.tabs_normal)) },
                )
                FilterChip(
                    selected = showPrivate,
                    onClick = { showPrivate = true },
                    label = { Text(stringResource(R.string.private_label)) },
                )
                TextButton(onClick = { tabs.reopenClosed(); onClose() }, enabled = s.closed.isNotEmpty()) {
                    Text(stringResource(R.string.tabs_reopen))
                }
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).glassSurface(RoundedCornerShape(24.dp)),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.tabs_search)) },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            if (visible.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.tabs_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visible, key = { it.id }) { rec ->
                    val isActive = rec.id == s.activeId
                    val title = tabs.displayTitle(rec).ifBlank { stringResource(R.string.new_tab_title) }
                    val url = tabs.displayUrl(rec)
                    val thumb = if (thumbVersion >= 0) ThumbnailStore.get(tabs.controllerOf(rec.id)?.session) else null
                    val shape = RoundedCornerShape(18.dp)
                    Column(
                        modifier = Modifier
                            .animateItem()
                            .glassSurface(shape)
                            .then(if (isActive) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                            .pressScale({ tabs.select(rec.id); onClose() }, 0.97f),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { tabs.closeTab(rec.id) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, stringResource(R.string.close_tab), modifier = Modifier.size(18.dp))
                            }
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 8.dp).padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                )
                            } else {
                                Text(
                                    text = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/'),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (isActive) {
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                IconButton(onClick = { tabs.moveTab(rec.id, -1) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.move_earlier), modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { tabs.moveTab(rec.id, 1) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.move_later), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
