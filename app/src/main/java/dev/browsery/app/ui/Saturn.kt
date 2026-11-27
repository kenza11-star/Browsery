package dev.browsery.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/** Logo Browsery: planet bercincin (Saturnus). Digambar dengan Canvas, tanpa aset gambar. */
@Composable
fun SaturnLogo(modifier: Modifier = Modifier, ring: Color = Color.White, planet: Color = Color.White, edge: Color = Color(0xFF0E1A33)) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = center
        val rw = s * 0.92f
        val rh = s * 0.28f
        val tl = Offset(c.x - rw / 2f, c.y - rh / 2f)
        val sz = Size(rw, rh)
        val thin = s * 0.07f
        rotate(-20f, c) {
            drawArc(ring, 180f, 180f, false, tl, sz, style = Stroke(thin, cap = StrokeCap.Round))
            drawCircle(
                Brush.radialGradient(listOf(planet, planet.copy(alpha = 0.78f)), center = Offset(c.x - s * 0.08f, c.y - s * 0.1f), radius = s * 0.34f),
                radius = s * 0.27f,
                center = c,
            )
            drawArc(edge, 0f, 180f, false, tl, sz, style = Stroke(thin * 2.1f, cap = StrokeCap.Butt))
            drawArc(ring, 0f, 180f, false, tl, sz, style = Stroke(thin, cap = StrokeCap.Round))
        }
    }
}
