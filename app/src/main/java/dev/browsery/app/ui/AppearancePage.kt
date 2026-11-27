package dev.browsery.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import dev.browsery.app.wallpaper.Kind
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.browsery.app.BrowseryApp
import dev.browsery.app.R
import dev.browsery.app.wallpaper.Quality
import kotlin.math.roundToInt

/** Refresh rate yang didukung layar (hanya 60/90/120 yang ditawarkan). */
private fun supportedRates(ctx: Context): List<Int> {
    val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return emptyList()
    val d = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList()
    return d.supportedModes.map { it.refreshRate.roundToInt() }.filter { it == 60 || it == 90 || it == 120 }.distinct().sorted()
}

tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearancePage(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val wp = (ctx.applicationContext as BrowseryApp).wallpaper
    val rates = remember { supportedRates(ctx) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) wp.import(uri)
    }
    val kindIndex = when (wp.kind) {
        Kind.GIF -> 1
        Kind.VIDEO -> 2
        else -> 0
    }
    fun launchPicker(i: Int) {
        val type = when (i) {
            1 -> ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif")
            2 -> ActivityResultContracts.PickVisualMedia.VideoOnly
            else -> ActivityResultContracts.PickVisualMedia.ImageOnly
        }
        picker.launch(PickVisualMediaRequest(type))
    }
    val coverageValues = listOf(25, 50, 75)
    val coverageIndex = coverageValues.indices.minByOrNull { kotlin.math.abs(coverageValues[it] - wp.coverage) } ?: 1
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PageHeader(stringResource(R.string.app_header), onClose)
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.fillMaxWidth().glassSurface().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.app_background), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    GlassSegmented(
                        options = listOf(stringResource(R.string.app_seg_photo), stringResource(R.string.app_seg_gif), stringResource(R.string.app_seg_video)),
                        selected = kindIndex,
                        onSelect = { launchPicker(it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val img = wp.sharp
                    if (img != null) {
                        Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp))) {
                            Image(bitmap = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Column(Modifier.align(Alignment.CenterEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                RoundAction(Icons.Filled.Edit, stringResource(R.string.app_change)) { launchPicker(kindIndex) }
                                if (!wp.isDefault) RoundAction(Icons.Filled.Close, stringResource(R.string.app_reset)) { wp.reset() }
                            }
                        }
                    }
                    Text(wp.info ?: stringResource(R.string.app_none), style = MaterialTheme.typography.bodySmall)
                    if (wp.busy) Text(stringResource(R.string.app_busy), style = MaterialTheme.typography.bodySmall)
                    wp.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.app_local_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(Modifier.fillMaxWidth().glassSurface().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_liquid), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.app_liquid_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = wp.liquid, onCheckedChange = { wp.updateLiquid(it) })
                    }
                    Text(stringResource(R.string.app_coverage_title), style = MaterialTheme.typography.labelLarge)
                    GlassSegmented(
                        options = coverageValues.map { "$it %" },
                        selected = coverageIndex,
                        onSelect = { wp.updateCoverage(coverageValues[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SliderRow(stringResource(R.string.app_blur_title), wp.blurLevel) { wp.updateBlur(it) }
                    SliderRow(stringResource(R.string.app_speed_title), wp.speed) { wp.updateSpeed(it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.app_animate), modifier = Modifier.weight(1f))
                        Switch(checked = wp.animate, onCheckedChange = { wp.updateAnimate(it) })
                    }
                    Text(stringResource(R.string.app_quality))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Quality.entries.forEach { q ->
                            FilterChip(
                                selected = wp.quality == q,
                                onClick = { wp.updateQuality(q) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (q) {
                                                Quality.AUTO -> R.string.app_q_auto
                                                Quality.BATTERY -> R.string.app_q_battery
                                                Quality.HIGH -> R.string.app_q_high
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    if (rates.size > 1) {
                        Text(stringResource(R.string.app_refresh))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = wp.refresh == 0,
                                onClick = { wp.updateRefresh(0) },
                                label = { Text(stringResource(R.string.app_refresh_auto)) },
                            )
                            rates.forEach { hz ->
                                FilterChip(
                                    selected = wp.refresh == hz,
                                    onClick = { wp.updateRefresh(hz) },
                                    label = { Text(hz.toString() + " Hz") },
                                )
                            }
                        }
                        Text(stringResource(R.string.app_refresh_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.app_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("$value%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 0f..100f)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
