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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.browsery.app.R
import dev.browsery.app.auth.AuthManager
import dev.browsery.app.browser.BrowserController
import dev.browsery.app.browser.BrowserState
import dev.browsery.app.browser.TabManager
import dev.browsery.app.browser.TabRecord
import dev.browsery.app.browser.isDesktop
import dev.browsery.app.browser.setDesktop
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.data.BookmarkEntity
import dev.browsery.app.download.DownloadManager
import dev.browsery.app.engine.GeckoHost
import dev.browsery.app.extensions.ExtensionManager
import dev.browsery.app.settings.AppSettings
import kotlinx.coroutines.launch

enum class Page { Browser, Tabs, Bookmarks, History, Downloads, Settings, Privacy, Extensions, Account }

@Composable
fun AppRoot(
    tabs: TabManager,
    db: AppDatabase,
    downloads: DownloadManager,
    settings: AppSettings,
    extensions: ExtensionManager,
    auth: AuthManager,
) {
    var page by remember { mutableStateOf(Page.Browser) }
    val s by settings.state.collectAsState()
    BackHandler(enabled = page != Page.Browser) { page = Page.Browser }
    when (page) {
        Page.Browser -> BrowserPage(tabs, db, settings, bottomBar = s.toolbarBottom, go = { page = it })
        Page.Tabs -> TabsPage(tabs, onClose = { page = Page.Browser })
        Page.Bookmarks -> BookmarksPage(db, tabs, onClose = { page = Page.Browser })
        Page.History -> HistoryPage(db, tabs, onClose = { page = Page.Browser })
        Page.Downloads -> DownloadsPage(downloads, onClose = { page = Page.Browser })
        Page.Settings -> SettingsPage(settings, db, downloads, onClose = { page = Page.Browser })
        Page.Privacy -> PrivacyPage(settings, onClose = { page = Page.Browser })
        Page.Extensions -> ExtensionsPage(extensions, onClose = { page = Page.Browser })
        Page.Account -> AccountPage(auth, onClose = { page = Page.Browser })
    }
}

@Composable
fun BrowserPage(tabs: TabManager, db: AppDatabase, settings: AppSettings, bottomBar: Boolean, go: (Page) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val s = tabs.state
    val active = s.active
    val controller = active?.let { tabs.controllerOf(it.id) }
    val st = controller?.state
    val videoFs = st?.isFullScreen == true
    var immersive by remember { mutableStateOf(false) }
    val fullscreen = videoFs || immersive
    val hostView = LocalView.current
    // Layar penuh buatan pengguna: sembunyikan bar aplikasi dan bar sistem; geser dari tepi untuk memunculkan bar sistem sementara.
    LaunchedEffect(immersive) {
        val w = ctx.findActivity()?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(w, hostView)
        if (immersive) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else if (!videoFs) {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val w = ctx.findActivity()?.window
            if (w != null) WindowCompat.getInsetsController(w, hostView).show(WindowInsetsCompat.Type.systemBars())
        }
    }
    val isHome = controller == null || st?.started != true
    val url = if (isHome) "" else st?.url.orEmpty()
    val fullUrl = if (isHome) "" else st?.displayUrl.orEmpty()
    val canBookmark = !isHome && st?.isWebUrl == true
    val isPrivate = active?.isPrivate == true
    val bookmarked by remember(url) { db.bookmarkDao().isBookmarked(url) }.collectAsState(initial = false)
    var siteInfo by remember { mutableStateOf(false) }
    var findOpen by remember(active?.id) { mutableStateOf(false) }
    var desktopOn by remember(active?.id) { mutableStateOf(controller?.isDesktop() == true) }
    var field by remember(active?.id) { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(false) }
    // Saran hanya dari data lokal dan tidak di tab privat (agar riwayat biasa tidak muncul di sana).
    val showSuggestions = focused && !isPrivate && field.text.isNotBlank() && field.text.trim() != fullUrl

    BackHandler(enabled = fullscreen || (!isHome && st?.canGoBack == true)) {
        if (immersive) {
            immersive = false
        } else if (videoFs) {
            controller?.exitFullScreen()
        } else {
            controller?.goBack()
        }
    }

    val addressRow: @Composable () -> Unit = {
        AddressRow(
            tabs = tabs,
            active = active,
            controller = controller,
            st = st,
            wide = wide,
            isHome = isHome,
            canBookmark = canBookmark,
            bookmarked = bookmarked,
            desktop = desktopOn,
            field = field,
            onField = { field = it },
            focused = focused,
            onFocused = { focused = it },
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
            onSiteInfo = { if (canBookmark) siteInfo = true },
            onFind = { if (canBookmark) findOpen = true },
            onDesktop = {
                if (canBookmark && controller != null) {
                    val next = !desktopOn
                    controller.setDesktop(next)
                    desktopOn = next
                }
            },
            onImmersive = { immersive = true },
            go = go,
        )
        ProgressAndMessage(st, isHome, controller)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (fullscreen) Modifier else Modifier.safeDrawingPadding()),
        ) {
            if (!fullscreen && wide) TabStrip(tabs)
            if (!fullscreen && !bottomBar) addressRow()
            if (!fullscreen && findOpen && controller != null && !isHome) {
                key(active?.id) { FindBar(controller, onClose = { findOpen = false }) }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (showSuggestions) {
                    Suggestions(db, field.text) { picked ->
                        tabs.navigate(picked)
                        focusManager.clearFocus()
                    }
                } else if (isHome || controller == null || active == null) {
                    HomePage(tabs, db)
                } else {
                    key(active.id) {
                        GeckoHost(session = controller.session, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            if (!fullscreen && bottomBar) addressRow()
            if (!fullscreen && !wide) {
                NavBar(
                    controller = controller,
                    canBack = !isHome && st?.canGoBack == true,
                    canForward = !isHome && st?.canGoForward == true,
                    tabCount = tabs.state.tabs.size,
                    onNewTab = { tabs.newTab(isPrivate) },
                    onTabs = { go(Page.Tabs) },
                )
            }
        }
        if (immersive && !videoFs) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    text = stringResource(R.string.exit_fullscreen),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { immersive = false }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }

    if (siteInfo && controller != null) {
        SiteInfoDialog(controller, settings, onDismiss = { siteInfo = false })
    }
}

@Composable
private fun ProgressAndMessage(st: BrowserState?, isHome: Boolean, controller: BrowserController?) {
    if (st?.isLoading == true && !isHome) {
        LinearProgressIndicator(
            progress = { maxOf(st.progress, 5) / 100f },
            modifier = Modifier.fillMaxWidth().height(2.dp),
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

/** Bilah bawah kaca mengambang (ponsel): kembali, maju, tab baru, daftar tab. */
@Composable
private fun NavBar(
    controller: BrowserController?,
    canBack: Boolean,
    canForward: Boolean,
    tabCount: Int,
    onNewTab: () -> Unit,
    onTabs: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(56.dp)
            .glassSurface(RoundedCornerShape(28.dp)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { controller?.goBack() }, enabled = canBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
        }
        IconButton(onClick = { controller?.goForward() }, enabled = canForward) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.action_forward))
        }
        IconButton(onClick = onNewTab) {
            Icon(Icons.Filled.Add, stringResource(R.string.menu_new_tab))
        }
        TabCountButton(tabCount, onTabs)
    }
}

@Composable
private fun TabCountButton(tabCount: Int, onClick: () -> Unit) {
    val countDesc = stringResource(R.string.tabs_count_desc, tabCount)
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp))
                .semantics { contentDescription = countDesc },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (tabCount > 99) ":D" else tabCount.toString(),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
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
private fun AddressRow(
    tabs: TabManager,
    active: TabRecord?,
    controller: BrowserController?,
    st: BrowserState?,
    wide: Boolean,
    isHome: Boolean,
    canBookmark: Boolean,
    bookmarked: Boolean,
    desktop: Boolean,
    field: TextFieldValue,
    onField: (TextFieldValue) -> Unit,
    focused: Boolean,
    onFocused: (Boolean) -> Unit,
    onToggleBookmark: () -> Unit,
    onShare: () -> Unit,
    onSiteInfo: () -> Unit,
    onFind: () -> Unit,
    onDesktop: () -> Unit,
    onImmersive: () -> Unit,
    go: (Page) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var menu by remember { mutableStateOf(false) }
    val shownUrl = if (isHome) "" else st?.displayUrl.orEmpty()
    val pretty = shownUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
    val canBack = !isHome && st?.canGoBack == true
    val canForward = !isHome && st?.canGoForward == true
    val loading = st?.isLoading == true
    val isPrivate = active?.isPrivate == true
    val pillTint = if (isPrivate) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f) else Color.Transparent
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wide) {
            IconButton(onClick = { controller?.goBack() }, enabled = canBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            IconButton(onClick = { controller?.goForward() }, enabled = canForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.action_forward))
            }
            IconButton(onClick = { if (loading) controller?.stop() else controller?.reload() }, enabled = !isHome) {
                Icon(
                    if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                    stringResource(if (loading) R.string.action_stop else R.string.action_reload),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = pillTint,
            modifier = Modifier.weight(1f).height(46.dp).glassSurface(RoundedCornerShape(24.dp)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    shownUrl.isEmpty() ->
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 14.dp, end = 4.dp).size(20.dp),
                        )
                    st != null && st.isSecure && !st.isHttp ->
                        IconButton(onClick = onSiteInfo, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.Lock, stringResource(R.string.menu_site_info), modifier = Modifier.size(18.dp))
                        }
                    else ->
                        IconButton(onClick = onSiteInfo, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Filled.Warning,
                                stringResource(R.string.not_secure),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                }
                val blocked = st?.blockedCount ?: 0
                if (!isHome && blocked > 0) {
                    val shieldDesc = stringResource(R.string.shield_count_desc, blocked)
                    Text(
                        text = "🛡 $blocked",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clickable(onClick = onSiteInfo)
                            .semantics { contentDescription = shieldDesc }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
                if (isPrivate) {
                    Text(
                        stringResource(R.string.private_label),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (!focused && pretty.isEmpty()) {
                        Text(
                            stringResource(R.string.address_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = if (focused) field else TextFieldValue(pretty),
                        onValueChange = onField,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { f ->
                                if (f.isFocused && !focused) onField(TextFieldValue(shownUrl, TextRange(0, shownUrl.length)))
                                onFocused(f.isFocused)
                            },
                        singleLine = true,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            tabs.navigate(field.text)
                            focusManager.clearFocus()
                        }),
                    )
                }
                if (focused && field.text.isNotEmpty()) {
                    IconButton(onClick = { onField(TextFieldValue("")) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.clear_text), modifier = Modifier.size(18.dp))
                    }
                } else if (canBookmark) {
                    IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Share, stringResource(R.string.menu_share), modifier = Modifier.size(18.dp))
                    }
                } else {
                    Spacer(Modifier.size(8.dp))
                }
            }
        }
        if (wide) TabCountButton(tabs.state.tabs.size) { go(Page.Tabs) }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.menu_more))
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { menu = false; onToggleBookmark() }, enabled = canBookmark) {
                        Icon(
                            Icons.Filled.Star,
                            stringResource(if (bookmarked) R.string.menu_bookmark_remove else R.string.menu_bookmark_add),
                            tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { menu = false; onShare() }, enabled = canBookmark) {
                        Icon(Icons.Filled.Share, stringResource(R.string.menu_share))
                    }
                    IconButton(
                        onClick = { menu = false; if (loading) controller?.stop() else controller?.reload() },
                        enabled = !isHome,
                    ) {
                        Icon(
                            if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                            stringResource(if (loading) R.string.action_stop else R.string.action_reload),
                        )
                    }
                    IconButton(onClick = { menu = false; onSiteInfo() }, enabled = canBookmark) {
                        Icon(Icons.Filled.Info, stringResource(R.string.menu_site_info))
                    }
                }
                HorizontalDivider()
                MenuRow(stringResource(R.string.menu_new_tab), Icons.Filled.Add) { menu = false; tabs.newTab(false) }
                MenuRow(stringResource(R.string.menu_new_private), Icons.Filled.Lock) { menu = false; tabs.newTab(true) }
                HorizontalDivider()
                MenuRow(stringResource(R.string.menu_history), null) { menu = false; go(Page.History) }
                MenuRow(stringResource(R.string.menu_bookmarks), Icons.Filled.Star) { menu = false; go(Page.Bookmarks) }
                MenuRow(stringResource(R.string.dl_menu), null) { menu = false; go(Page.Downloads) }
                HorizontalDivider()
                MenuRow(stringResource(R.string.menu_find), Icons.Filled.Search, canBookmark) { menu = false; onFind() }
                MenuRow(
                    stringResource(if (desktop) R.string.menu_mobile else R.string.menu_desktop),
                    null,
                    canBookmark,
                ) { menu = false; onDesktop() }
                MenuRow(stringResource(R.string.menu_fullscreen), null) { menu = false; onImmersive() }
                HorizontalDivider()
                MenuRow(stringResource(R.string.menu_extensions), null) { menu = false; go(Page.Extensions) }
                MenuRow(stringResource(R.string.menu_account), Icons.Filled.Person) { menu = false; go(Page.Account) }
                MenuRow(stringResource(R.string.menu_privacy), null) { menu = false; go(Page.Privacy) }
                MenuRow(stringResource(R.string.settings_title), Icons.Filled.Settings) { menu = false; go(Page.Settings) }
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, icon: ImageVector?, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = {
            if (icon != null) Icon(icon, contentDescription = null) else Spacer(Modifier.size(24.dp))
        },
        onClick = onClick,
        enabled = enabled,
    )
}
