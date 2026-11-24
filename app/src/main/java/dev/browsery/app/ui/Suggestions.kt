package dev.browsery.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.data.BookmarkEntity
import dev.browsery.app.data.HistoryEntity

private data class Suggestion(val url: String, val title: String, val bookmark: Boolean)

/** Saran saat mengetik di kolom alamat: bookmark dan riwayat lokal (tidak ada permintaan jaringan). */
@Composable
fun Suggestions(db: AppDatabase, query: String, onPick: (String) -> Unit) {
    val q = query.trim()
    val bookmarks by remember(q) { db.bookmarkDao().observe(q) }.collectAsState(initial = emptyList<BookmarkEntity>())
    val history by remember(q) { db.historyDao().observe(q) }.collectAsState(initial = emptyList<HistoryEntity>())
    val items = remember(bookmarks, history) {
        (bookmarks.map { Suggestion(it.url, it.title, true) } + history.map { Suggestion(it.url, it.title, false) })
            .distinctBy { it.url }
            .take(8)
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(q) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.sug_search_for, q),
                        modifier = Modifier.padding(start = 16.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            items(items, key = { it.url }) { s ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(s.url) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (s.bookmark) Icons.Filled.Star else Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(s.title.ifBlank { s.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            s.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
