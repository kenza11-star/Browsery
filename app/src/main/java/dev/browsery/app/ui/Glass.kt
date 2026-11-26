package dev.browsery.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.browsery.app.auth.AuthManager
import dev.browsery.app.browser.TabManager
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.download.DownloadManager
import dev.browsery.app.extensions.ExtensionManager
import dev.browsery.app.settings.AppSettings

/**
 * Sistem desain "glass". Catatan jujur: konten web (GeckoView) adalah surface native, sehingga blur latar
 * sungguhan di atas halaman web tidak mungkin. Efek kaca hanya di atas latar Compose: lapisan tembus
 * pandang + tepi gradien tipis + bercak cahaya lembut. Tanpa RenderEffect blur agar tetap ringan.
 */
object Glass {
    val Card: Shape = RoundedCornerShape(20.dp)
}

/** Latar gradien lembut + dua bercak cahaya. Digambar sekali (tanpa blur), murah untuk GPU. */
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

/** Permukaan kaca: isi tembus pandang dengan kilau diagonal dan tepi gradien 1dp. */
@Composable
fun Modifier.glassSurface(shape: Shape = Glass.Card): Modifier {
    val dark = MaterialTheme.colorScheme.onBackground.luminance() > 0.5f
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
 * Akar aplikasi: latar kaca di belakang, halaman berganti dengan transisi fade + geser/skala halus.
 * Tema membuat warna background transparan agar latar ini terlihat di semua halaman non-web.
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
    var page by remember { mutableStateOf(Page.Browser) }
    val s by settings.state.collectAsState()
    BackHandler(enabled = page != Page.Browser) { page = Page.Browser }
    Box(Modifier.fillMaxSize()) {
        GlassBackground()
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState == Page.Browser) {
                    (fadeIn(tween(220, easing = FastOutSlowInEasing)) + scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = 1.03f)) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it / 12 })
                } else {
                    (fadeIn(tween(240, delayMillis = 40, easing = FastOutSlowInEasing)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 12 }) togetherWith
                        (fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 0.97f))
                }
            },
            label = "page",
            modifier = Modifier.fillMaxSize(),
        ) { p ->
            when (p) {
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
    }
}
