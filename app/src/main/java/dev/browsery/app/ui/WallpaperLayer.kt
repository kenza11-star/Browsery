package dev.browsery.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.ImageDecoder
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import dev.browsery.app.wallpaper.Kind
import dev.browsery.app.wallpaper.LiquidBand
import dev.browsery.app.wallpaper.WallpaperController
import dev.browsery.app.wallpaper.sampleSizeFor
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

@RequiresApi(28)
private fun loadGif(file: File, sw: Int, sh: Int): AnimatedImageDrawable? = try {
    val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { dec, info, _ ->
        dec.setTargetSampleSize(sampleSizeFor(info.size.width, info.size.height, sw, sh))
    }
    d as? AnimatedImageDrawable
} catch (e: Exception) {
    null
}

/**
 * Wallpaper + (opsional) satu pita cairan. Foto: isi pita = salinan buram, di luar pita tetap tajam.
 * GIF: animasi mengikuti waktu sumber (API 28+). Video: MediaPlayer senyap berulang di TextureView.
 * Untuk GIF/video isi pita = kaca tembus pandang tanpa blur. Animasi hanya jalan saat terlihat (beranda),
 * aplikasi RESUMED, "kurangi animasi" sistem mati, dan kualitas bukan Hemat. Hanya gambar ulang tiap frame.
 */
@Composable
fun WallpaperLayer(wp: WallpaperController, liquidVisible: Boolean, scrim: Float, modifier: Modifier = Modifier) {
    val sharp = wp.sharp ?: return
    val blurred = wp.blurred
    val kind = wp.kind
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
    val motionOk = liquidVisible && resumed && !reduced
    val showLiquid = liquidVisible && wp.liquid && (kind != Kind.IMAGE || blurred != null)
    val running = showLiquid && wp.animate && motionOk
    val mediaPlays = motionOk && wp.mediaMayAnimate()

    var phase by remember { mutableFloatStateOf(1.2f) }
    val fps = wp.fpsCap()
    val speed = wp.speed
    LaunchedEffect(running, fps, speed) {
        if (!running) return@LaunchedEffect
        val minNs = 1_000_000_000L / fps
        val rate = 0.12f + speed / 100f * 0.55f
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = now - last
                if (dt >= minNs - 2_000_000L) {
                    phase += dt / 1_000_000_000f * rate
                    last = now
                }
            }
        }
    }

    // GIF animasi (API 28+): dimuat di thread latar, digerakkan callback drawable.
    val dm = ctx.resources.displayMetrics
    val gif by produceState<AnimatedImageDrawable?>(null, wp.version, kind) {
        value = if (kind == Kind.GIF && Build.VERSION.SDK_INT >= 28) {
            withContext(Dispatchers.Default) {
                loadGif(wp.mediaFile, minOf(dm.widthPixels, dm.heightPixels), maxOf(dm.widthPixels, dm.heightPixels))
            }
        } else {
            null
        }
    }
    var tick by remember { mutableIntStateOf(0) }
    val gifDrawable = gif
    DisposableEffect(gifDrawable, mediaPlays) {
        if (gifDrawable != null && Build.VERSION.SDK_INT >= 28) {
            val handler = Handler(Looper.getMainLooper())
            gifDrawable.callback = object : Drawable.Callback {
                override fun invalidateDrawable(who: Drawable) { tick++ }
                override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) { handler.postAtTime(what, who, `when`) }
                override fun unscheduleDrawable(who: Drawable, what: Runnable) { handler.removeCallbacks(what, who) }
            }
            gifDrawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            if (mediaPlays) gifDrawable.start()
        }
        onDispose {
            if (gifDrawable != null && Build.VERSION.SDK_INT >= 28) {
                gifDrawable.stop()
                gifDrawable.callback = null
            }
        }
    }

    val path = remember { Path() }
    val coverage = wp.coverage / 100f
    val videoOn = kind == Kind.VIDEO && mediaPlays
    Box(modifier = modifier.fillMaxSize()) {
        if (videoOn) {
            VideoWallpaper(wp.mediaFile.path, onError = { wp.fail("Video tidak dapat diputar di perangkat ini.") }, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier.fillMaxSize().drawBehind {
                val t = tick
                if (!videoOn) {
                    val g = gifDrawable
                    if (kind == Kind.GIF && g != null && t >= 0 && Build.VERSION.SDK_INT >= 28) {
                        drawIntoCanvas { c ->
                            val n = c.nativeCanvas
                            val dw = g.intrinsicWidth.toFloat().coerceAtLeast(1f)
                            val dh = g.intrinsicHeight.toFloat().coerceAtLeast(1f)
                            val sc = max(size.width / dw, size.height / dh)
                            n.save()
                            n.translate((size.width - dw * sc) / 2f, (size.height - dh * sc) / 2f)
                            n.scale(sc, sc)
                            g.setBounds(0, 0, dw.toInt(), dh.toInt())
                            g.draw(n)
                            n.restore()
                        }
                    } else {
                        drawCover(sharp)
                    }
                }
                if (showLiquid) {
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
                        if (kind == Kind.IMAGE && blurred != null) {
                            drawCover(blurred)
                            drawRect(Color.White.copy(alpha = 0.05f))
                        } else {
                            drawRect(Color.White.copy(alpha = 0.14f))
                        }
                    }
                    drawPath(
                        path,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f)), startY = 0f, endY = size.height),
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
                drawRect(Color.Black.copy(alpha = scrim))
            },
        ) {}
    }
}

/** Video senyap berulang pada TextureView (jalur hardware MediaPlayer). Dilepas saat keluar komposisi. */
@Composable
private fun VideoWallpaper(path: String, onError: () -> Unit, modifier: Modifier) {
    val holder = remember(path) { VideoHolder(path, onError) }
    DisposableEffect(holder) { onDispose { holder.release() } }
    AndroidView(modifier = modifier, factory = { ctx -> TextureView(ctx).also { holder.attach(it) } })
}

private class VideoHolder(private val path: String, private val onError: () -> Unit) {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var view: TextureView? = null
    private var vw = 0
    private var vh = 0

    fun attach(v: TextureView) {
        view = v
        v.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) { start(st) }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) { fit() }
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { release(); return true }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    private fun start(st: SurfaceTexture) {
        try {
            val s = Surface(st)
            surface = s
            val m = MediaPlayer()
            player = m
            m.setDataSource(path)
            m.setSurface(s)
            m.isLooping = true
            m.setVolume(0f, 0f)
            m.setOnVideoSizeChangedListener { _, w, h -> vw = w; vh = h; fit() }
            m.setOnErrorListener { _, _, _ -> onError(); true }
            m.setOnPreparedListener { it.start() }
            m.prepareAsync()
        } catch (e: Exception) {
            onError()
        }
    }

    /** center-crop: TextureView meregangkan video ke seluruh view, matriks mengembalikan rasio. */
    private fun fit() {
        val v = view ?: return
        if (vw <= 0 || vh <= 0 || v.width <= 0 || v.height <= 0) return
        val vr = vw.toFloat() / vh
        val wr = v.width.toFloat() / v.height
        val sx = if (vr > wr) vr / wr else 1f
        val sy = if (vr > wr) 1f else wr / vr
        val m = android.graphics.Matrix()
        m.setScale(sx, sy, v.width / 2f, v.height / 2f)
        v.setTransform(m)
    }

    fun release() {
        try {
            player?.release()
        } catch (e: Exception) {
            // sudah dilepas
        }
        player = null
        surface?.release()
        surface = null
    }
}
