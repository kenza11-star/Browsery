package dev.browsery.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import dev.browsery.app.wallpaper.LiquidBand
import dev.browsery.app.wallpaper.WallpaperController
import kotlin.math.max

private tailrec fun Context.owner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.owner()
    else -> null
}

private fun DrawScope.drawCover(img: ImageBitmap) {
    val scale = max(size.width / img.width, size.height / img.height)
    val sw = (size.width / scale).toInt().coerceIn(1, img.width)
    val sh = (size.height / scale).toInt().coerceIn(1, img.height)
    drawImage(
        image = img,
        srcOffset = IntOffset((img.width - sw) / 2, (img.height - sh) / 2),
        srcSize = IntSize(sw, sh),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
    )
}

/**
 * Wallpaper tajam + (opsional) satu pita cairan yang berisi salinan buram wallpaper.
 * Di luar mask wallpaper tetap tajam. Animasi memakai waktu frame nyata, berhenti saat aplikasi tidak terlihat,
 * saat "kurangi animasi" sistem aktif, atau saat dimatikan. Hanya gambar ulang (bukan recomposition) tiap frame.
 */
@Composable
fun WallpaperLayer(wp: WallpaperController, liquidVisible: Boolean, scrim: Float, modifier: Modifier = Modifier) {
    val sharp = wp.sharp ?: return
    val blurred = wp.blurred
    val ctx = LocalContext.current
    var resumed by remember { mutableStateOf(true) }
    DisposableEffect(ctx) {
        val owner = ctx.owner()
        val obs = LifecycleEventObserver { _, _ -> resumed = owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    val reduced = remember(ctx) {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val showLiquid = liquidVisible && wp.liquid && blurred != null
    val running = showLiquid && wp.animate && resumed && !reduced
    var phase by remember { mutableFloatStateOf(1.2f) }
    val fps = wp.fpsCap()
    val speed = wp.speed
    LaunchedEffect(running, fps, speed) {
        if (!running) return@LaunchedEffect
        val minNs = 1_000_000_000L / fps
        val rate = 0.12f + speed / 100f * 0.55f
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = now - last
                if (dt >= minNs - 2_000_000L) {
                    phase += dt / 1_000_000_000f * rate
                    last = now
                }
            }
        }
    }
    val path = remember { Path() }
    val coverage = wp.coverage / 100f
    Modifier.fillMaxSize()
    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxSize().drawBehind {
            drawCover(sharp)
            if (showLiquid && blurred != null) {
                val band = LiquidBand(size.width, size.height, coverage)
                val p = phase
                path.rewind()
                val n = 48
                val x0 = -size.width * 0.1f
                val span = size.width * 1.2f
                for (i in 0..n) {
                    val x = x0 + span * i / n
                    if (i == 0) path.moveTo(x, band.top(x, p)) else path.lineTo(x, band.top(x, p))
                }
                for (i in n downTo 0) {
                    val x = x0 + span * i / n
                    path.lineTo(x, band.bottom(x, p))
                }
                path.close()
                clipPath(path) {
                    drawCover(blurred)
                    drawRect(Color.White.copy(alpha = 0.05f))
                }
                drawPath(
                    path,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f)), startY = 0f, endY = size.height),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
            drawRect(Color.Black.copy(alpha = scrim), topLeft = Offset.Zero)
        },
    ) {}
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
private val Double.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
