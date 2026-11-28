package dev.browsery.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.browsery.app.BrowseryApp
import dev.browsery.app.auth.AuthManager
import dev.browsery.app.browser.TabManager
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.download.DownloadManager
import dev.browsery.app.engine.ThumbnailStore
import dev.browsery.app.extensions.ExtensionManager
import dev.browsery.app.settings.AppSettings
import kotlinx.coroutines.delay

/**
 * Sistem desain "glass". Catatan jujur: konten web (GeckoView) adalah surface native, sehingga blur latar
 * sungguhan di atas halaman web tidak mungkin. Efek kaca hanya di atas latar Compose: lapisan tembus
 * pandang + tepi gradien tipis. Tanpa RenderEffect blur agar tetap ringan.
 */
object Glass {
    val Card: Shape = RoundedCornerShape(20.dp)
}

/** Latar gradien lembut + dua bercak cahaya (dipakai saat tidak ada wallpaper). Digambar sekali, tanpa blur. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.onBackground.luminance() > 0.5f
    val primary = MaterialTheme.colorScheme.primary
    val base = if (dark) listOf(Color(0xFF14161B), Color(0xFF1B1F2A)) else listOf(Color(0xFFF4F7FC), Color(0xFFE9EFFA))
    val blobA = primary.copy(alpha = if (dark) 0.22f else 0.18f)
    val blobB = Color(0xFF9B7BFF).copy(alpha = if (dark) 0.16f else 0.14f)
    Box(
        modifier = modifier.fillMaxSize()
            .background(Brush.verticalGradient(base))
            .drawBehind {
                val ca = Offset(size.width * 0.15f, size.height * 0.1f)
                val cb = Offset(size.width * 0.95f, size.height * 0.85f)
                drawCircle(Brush.radialGradient(listOf(blobA, Color.Transparent), center = ca, radius = size.width * 0.7f), radius = size.width * 0.7f, center = ca)
                drawCircle(Brush.radialGradient(listOf(blobB, Color.Transparent), center = cb, radius = size.width * 0.8f), radius = size.width * 0.8f, center = cb)
            },
    )
}

/** Saat true (halaman web tampil), permukaan kaca dibuat rata: tanpa kilau dan tanpa gradien tepi. */
val LocalFlatGlass = compositionLocalOf { false }

/** Permukaan kaca: isi tembus pandang dengan kilau diagonal dan tepi gradien 1dp (rata bila LocalFlatGlass). */
@Composable
fun Modifier.glassSurface(shape: Shape = Glass.Card): Modifier {
    val dark = MaterialTheme.colorScheme.onBackground.luminance() > 0.5f
    if (LocalFlatGlass.current) {
        val ink = MaterialTheme.colorScheme.onBackground
        return this
            .clip(shape)
            .background(ink.copy(alpha = 0.09f))
            .border(1.dp, ink.copy(alpha = 0.12f), shape)
    }
    val fill = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.62f)
    val edgeTop = if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.95f)
    val edgeBottom = if (dark) Color.White.copy(alpha = 0.05f) else Color(0x1A1A3A7A)
    val sheen = Color.White.copy(alpha = if (dark) 0.07f else 0.35f)
    return this
        .clip(shape)
        .background(fill)
        .background(Brush.linearGradient(listOf(sheen, Color.Transparent)))
        .border(1.dp, Brush.verticalGradient(listOf(edgeTop, edgeBottom)), shape)
}

/** Umpan balik tekan: skala pegas halus; klik tetap dijalankan di onTap. */
@Composable
fun Modifier.pressScale(onClick: () -> Unit, pressed: Float = 0.97f): Modifier {
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (down) pressed else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(onClick) {
            detectTapGestures(
                onPress = {
                    down = true
                    tryAwaitRelease()
                    down = false
                },
                onTap = { onClick() },
            )
        }
}

/**
 * Akar aplikasi. Latar: wallpaper (bila dipilih) atau gradien. Halaman web TIDAK dianimasikan/transformasi
 * (surface native Gecko): hanya halaman non-web yang fade/geser, agar tidak patah-patah.
 */
@Composable
fun GlassAppRoot(
    tabs: TabManager,
    db: AppDatabase,
    downloads: DownloadManager,
    settings: AppSettings,
    extensions: ExtensionManager,
    auth: AuthManager,
) {
    val ctx = LocalContext.current
    val wp = (ctx.applicationContext as BrowseryApp).wallpaper
    var page by remember { mutableStateOf(Page.Browser) }
    var appearance by remember { mutableStateOf(false) }
    val s by settings.state.collectAsState()
    BackHandler(enabled = page != Page.Browser) { page = Page.Browser }
    BackHandler(enabled = page == Page.Settings && appearance) { appearance = false }
    // Permintaan refresh rate ke sistem (0 = biarkan sistem memilih).
    LaunchedEffect(wp.refresh) {
        val act = ctx.findActivity() ?: return@LaunchedEffect
        val lp = act.window.attributes
        lp.preferredRefreshRate = wp.refresh.toFloat()
        act.window.attributes = lp
    }
    val ctl = tabs.state.active?.let { tabs.controllerOf(it.id) }
    val isHome = ctl == null || ctl.state.started != true
    val webView = page == Page.Browser && !isHome
    // Warna bar mengikuti tepi halaman web (rata, tanpa gradien); diambil sekali setelah halaman selesai dimuat.
    val loading = ctl?.state?.isLoading == true
    val shownUrl = ctl?.state?.url
    LaunchedEffect(webView, shownUrl, loading) {
        if (webView && !loading) {
            delay(450)
            ThumbnailStore.captureActive()
        }
    }
    val sysDark = MaterialTheme.colorScheme.onBackground.luminance() > 0.5f
    val fallback = if (sysDark) Color(0xFF1C1B20) else Color(0xFFF6F6F8)
    val edge = if (webView && ThumbnailStore.version >= 0) ThumbnailStore.edgeColors(ctl?.session) else null
    val topC by animateColorAsState(edge?.first ?: fallback, tween(260), label = "barTop")
    val botC by animateColorAsState(edge?.second ?: fallback, tween(260), label = "barBottom")
    Box(Modifier.fillMaxSize()) {
        if (wp.sharp != null && (page != Page.Browser || isHome)) {
            WallpaperLayer(
                wp,
                liquidVisible = page == Page.Browser || page == Page.Tabs,
                scrim = when (page) {
                    Page.Browser -> 0.16f
                    Page.Tabs -> 0.24f
                    else -> 0.5f
                },
            )
        } else if (webView) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(topC, size = Size(size.width, size.height / 2f))
                    drawRect(botC, topLeft = Offset(0f, size.height / 2f), size = Size(size.width, size.height / 2f))
                },
            )
        } else {
            GlassBackground()
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState == Page.Browser) {
                    EnterTransition.None togetherWith fadeOut(tween(140))
                } else {
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 14 } togetherWith ExitTransition.None
                }
            },
            label = "page",
            modifier = Modifier.fillMaxSize(),
        ) { p ->
            when (p) {
                Page.Browser -> {
                    val content: @Composable () -> Unit = {
                        BrowserPage(tabs, db, settings, bottomBar = s.toolbarBottom, go = { target ->
                            // Ambil gambar mini halaman nyata sebelum pindah (hanya memori).
                            if (target != Page.Browser) ThumbnailStore.captureActive()
                            page = target
                        })
                    }
                    if (webView) {
                        // Warna teks/ikon bar disesuaikan dengan terang-gelapnya warna halaman.
                        val lum = (topC.luminance() + botC.luminance()) / 2f
                        val light = lum < 0.5f
                        val fg = if (light) Color(0xFFEDEDF2) else Color(0xFF1B1B1F)
                        val panel = if (light) Color(0xFF2A2A30) else Color(0xFFF2F2F5)
                        val cs = MaterialTheme.colorScheme
                        val scheme = remember(fg, cs) {
                            cs.copy(
                                onSurface = fg,
                                onBackground = fg,
                                onSurfaceVariant = fg.copy(alpha = 0.72f),
                                surface = panel,
                                surfaceContainer = panel,
                                surfaceContainerHigh = panel,
                                surfaceContainerHighest = panel,
                            )
                        }
                        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
                            CompositionLocalProvider(LocalFlatGlass provides true) { content() }
                        }
                    } else {
                        content()
                    }
                }
                Page.Tabs -> TabsPage(tabs, onClose = { page = Page.Browser })
                Page.Bookmarks -> BookmarksPage(db, tabs, onClose = { page = Page.Browser })
                Page.History -> HistoryPage(db, tabs, onClose = { page = Page.Browser })
                Page.Downloads -> DownloadsPage(downloads, onClose = { page = Page.Browser })
                Page.Settings ->
                    if (appearance) {
                        AppearancePage(settings, onClose = { appearance = false })
                    } else {
                        SettingsPage(settings, db, downloads, onClose = { page = Page.Browser }, onAppearance = { appearance = true })
                    }
                Page.Privacy -> PrivacyPage(settings, onClose = { page = Page.Browser })
                Page.Extensions -> ExtensionsPage(extensions, onClose = { page = Page.Browser })
                Page.Account -> AccountPage(auth, onClose = { page = Page.Browser })
            }
        }
        // Dialog izin ekstensi: selalu tersedia agar setiap permintaan dari engine punya jawaban pengguna.
        ExtensionPromptHost(extensions)
    }
}
