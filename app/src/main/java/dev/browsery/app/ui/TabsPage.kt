package dev.browsery.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.browser.TabManager

@Composable
fun TabsPage(tabs: TabManager, onClose: () -> Unit) {
    val s = tabs.state
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PageHeader(
                title = stringResource(R.string.tabs_title) + " (" + s.tabs.size + ")",
                onBack = onClose,
            ) {
                TextButton(onClick = { tabs.reopenClosed(); onClose() }, enabled = s.closed.isNotEmpty()) {
                    Text(stringResource(R.string.tabs_reopen))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { tabs.newTab(false); onClose() }) { Text(stringResource(R.string.menu_new_tab)) }
                TextButton(onClick = { tabs.newTab(true); onClose() }) { Text(stringResource(R.string.menu_new_private)) }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(s.tabs, key = { it.id }) { rec ->
                    val isActive = rec.id == s.activeId
                    Card(
                        onClick = { tabs.select(rec.id); onClose() },
                        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = tabs.displayTitle(rec).ifBlank { stringResource(R.string.new_tab_title) },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { tabs.closeTab(rec.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.close_tab), modifier = Modifier.size(18.dp))
                                }
                            }
                            Text(
                                text = tabs.displayUrl(rec),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (rec.isPrivate) {
                                    Text(
                                        stringResource(R.string.private_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Text("", modifier = Modifier.weight(1f))
                                }
                                IconButton(onClick = { tabs.moveTab(rec.id, -1) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.move_earlier), modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { tabs.moveTab(rec.id, 1) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.move_later), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
