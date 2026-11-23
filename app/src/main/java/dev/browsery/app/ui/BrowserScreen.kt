package dev.browsery.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.browser.BrowserController
import dev.browsery.app.browser.BrowserState
import dev.browsery.app.browser.TabManager
import dev.browsery.app.browser.TabRecord
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.data.BookmarkEntity
import dev.browsery.app.download.DownloadManager
import dev.browsery.app.engine.GeckoHost
import dev.browsery.app.settings.AppSettings
import kotlinx.coroutines.launch

enum class Page { Browser, Tabs, Bookmarks, History, Downloads, Settings }

@Composable
fun AppRoot(tabs: TabManager, db: AppDatabase, downloads: DownloadManager, settings: AppSettings) {
    var page by remember { mutableStateOf(Page.Browser) }
    val s by settings.state.collectAsState()
    BackHandler(enabled = page != Page.Browser) { page = Page.Browser }
    when (page) {
        Page.Browser -> BrowserPage(tabs, db, bottomBar = s.toolbarBottom, go = { page = it })
        Page.Tabs -> TabsPage(tabs, onClose = { page = Page.Browser })
        Page.Bookmarks -> BookmarksPage(db, tabs, onClose = { page = Page.Browser })
        Page.History -> HistoryPage(db, tabs, onClose = { page = Page.Browser })
        Page.Downloads -> DownloadsPage(downloads, onClose = { page = Page.Browser })
        Page.Settings -> SettingsPage(settings, db, downloads, onClose = { page = Page.Browser })
    }
}

@Composable
fun BrowserPage(tabs: TabManager, db: AppDatabase, bottomBar: Boolean, go: (Page) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val s = tabs.state
    val active = s.active
    val controller = active?.let { tabs.controllerOf(it.id) }
    val st = controller?.state
    val fullscreen = st?.isFullScreen == true
    val isHome = controller == null || st?.started != true
    val url = if (isHome) "" else st?.url.orEmpty()
    val canBookmark = !isHome && st?.isWebUrl == true
    val bookmarked by remember(url) { db.bookmarkDao().isBookmarked(url) }.collectAsState(initial = false)

    BackHandler(enabled = fullscreen || (!isHome && st?.canGoBack == true)) {
        if (fullscreen) controller?.exitFullScreen() else controller?.goBack()
    }

    val chrome: @Composable () -> Unit = {
        if (wide) TabStrip(tabs)
        if (bottomBar) {
            // Progres dan pesan di atas toolbar agar dekat dengan konten.
            ProgressAndMessage(st, isHome, controller)
        }
        Toolbar(
            tabs = tabs,
            active = active,
            controller = controller,
            st = st,
            wide = wide,
            isHome = isHome,
            canBookmark = canBookmark,
            bookmarked = bookmarked,
            onToggleBookmark = {
                if (canBookmark) {
                    scope.launch {
                        if (bookmarked) {
                            db.bookmarkDao().deleteByUrl(url)
                        } else {
                            db.bookmarkDao().insert(
                                BookmarkEntity(
                                    url = url,
                                    title = st?.title.orEmpty().ifBlank { url },
                                    createdAt = System.currentTimeMillis(),
                                ),
                            )
                        }
                    }
                }
            },
            onShare = { if (canBookmark) sharePage(ctx, url) },
            go = go,
        )
        if (!bottomBar) ProgressAndMessage(st, isHome, controller)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (fullscreen) Modifier else Modifier.safeDrawingPadding()),
        ) {
            if (!fullscreen && !bottomBar) chrome()
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (isHome || controller == null || active == null) {
                    HomePage(tabs, db)
                } else {
                    key(active.id) {
                        GeckoHost(session = controller.session, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            if (!fullscreen && bottomBar) chrome()
        }
    }
}

@Composable
private fun ProgressAndMessage(st: BrowserState?, isHome: Boolean, controller: BrowserController?) {
    if (st?.isLoading == true && !isHome) {
        LinearProgressIndicator(
            progress = { maxOf(st.progress, 5) / 100f },
            modifier = Modifier.fillMaxWidth().height(3.dp),
        )
    }
    st?.message?.let { msg ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { controller?.dismissMessage() }) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
}

private fun sharePage(ctx: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    ctx.startActivity(Intent.createChooser(send, null))
}

@Composable
private fun TabStrip(tabs: TabManager) {
    val s = tabs.state
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        s.tabs.forEach { rec ->
            val isActive = rec.id == s.activeId
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (rec.isPrivate) "🔒 " else "") +
                            tabs.displayTitle(rec).ifBlank { stringResource(R.string.new_tab_title) },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 180.dp)
                            .clickable { tabs.select(rec.id) }
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                    )
                    IconButton(onClick = { tabs.closeTab(rec.id) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.close_tab), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        IconButton(onClick = { tabs.newTab(false) }) {
            Icon(Icons.Filled.Add, stringResource(R.string.menu_new_tab))
        }
    }
}

@Composable
private fun Toolbar(
    tabs: TabManager,
    active: TabRecord?,
    controller: BrowserController?,
    st: BrowserState?,
    wide: Boolean,
    isHome: Boolean,
    canBookmark: Boolean,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onShare: () -> Unit,
    go: (Page) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var field by remember(active?.id) { mutableStateOf(TextFieldValue("")) }
    var menu by remember { mutableStateOf(false) }
    val shownUrl = if (isHome) "" else st?.displayUrl.orEmpty()
    val canBack = !isHome && st?.canGoBack == true
    val canForward = !isHome && st?.canGoForward == true
    val loading = st?.isLoading == true
    val isPrivate = active?.isPrivate == true
    val tabCount = tabs.state.tabs.size
    val bg = if (isPrivate) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wide) {
            IconButton(onClick = { controller?.goBack() }, enabled = canBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            IconButton(onClick = { controller?.goForward() }, enabled = canForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.action_forward))
            }
        }
        OutlinedTextField(
            value = if (focused) field else TextFieldValue(shownUrl),
            onValueChange = { field = it },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { f ->
                    if (f.isFocused && !focused) field = TextFieldValue(shownUrl, TextRange(0, shownUrl.length))
                    focused = f.isFocused
                },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text(stringResource(R.string.address_hint), maxLines = 1) },
            leadingIcon = {
                when {
                    shownUrl.isEmpty() -> Icon(Icons.Filled.Search, contentDescription = null)
                    st != null && st.isSecure && !st.isHttp ->
                        Icon(Icons.Filled.Lock, stringResource(R.string.secure_connection))
                    else -> Icon(Icons.Filled.Warning, stringResource(R.string.not_secure))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                tabs.navigate(field.text)
                focusManager.clearFocus()
            }),
        )
        if (isPrivate) {
            Text(
                text = stringResource(R.string.private_label),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (wide) {
            if (loading) {
                IconButton(onClick = { controller?.stop() }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_stop))
                }
            } else {
                IconButton(onClick = { controller?.reload() }, enabled = !isHome) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.action_reload))
                }
            }
            IconButton(onClick = onToggleBookmark, enabled = canBookmark) {
                Icon(
                    Icons.Filled.Star,
                    stringResource(if (bookmarked) R.string.menu_bookmark_remove else R.string.menu_bookmark_add),
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val countDesc = stringResource(R.string.tabs_count_desc, tabCount)
        IconButton(onClick = { go(Page.Tabs) }) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp))
                    .semantics { contentDescription = countDesc },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (tabCount > 99) "99+" else tabCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.menu_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!wide) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_forward)) },
                        onClick = { menu = false; controller?.goForward() },
                        enabled = canForward,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (loading) R.string.action_stop else R.string.action_reload)) },
                        onClick = { menu = false; if (loading) controller?.stop() else controller?.reload() },
                        enabled = !isHome,
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (bookmarked) R.string.menu_bookmark_remove else R.string.menu_bookmark_add))
                        },
                        onClick = { menu = false; onToggleBookmark() },
                        enabled = canBookmark,
                    )
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_new_tab)) },
                    onClick = { menu = false; tabs.newTab(false) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_new_private)) },
                    onClick = { menu = false; tabs.newTab(true) },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_history)) },
                    onClick = { menu = false; go(Page.History) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_bookmarks)) },
                    onClick = { menu = false; go(Page.Bookmarks) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dl_menu)) },
                    onClick = { menu = false; go(Page.Downloads) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_share)) },
                    onClick = { menu = false; onShare() },
                    enabled = canBookmark,
                )
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_find)) }, onClick = {}, enabled = false)
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_desktop)) }, onClick = {}, enabled = false)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_title)) },
                    onClick = { menu = false; go(Page.Settings) },
                )
            }
        }
    }
}
