package dev.browsery.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PageHeader(stringResource(R.string.app_title), onClose)
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.fillMaxWidth().glassSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.app_wallpaper), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(wp.info ?: stringResource(R.string.app_none))
                    if (wp.busy) Text(stringResource(R.string.app_busy), style = MaterialTheme.typography.bodySmall)
                    wp.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                            Text(stringResource(R.string.app_pick))
                        }
                        OutlinedButton(onClick = { wp.reset() }, enabled = wp.sharp != null) { Text(stringResource(R.string.app_reset)) }
                    }
                    Text(stringResource(R.string.app_local_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(Modifier.fillMaxWidth().glassSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_liquid))
                            Text(stringResource(R.string.app_liquid_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = wp.liquid, onCheckedChange = { wp.updateLiquid(it) })
                    }
                    Text(stringResource(R.string.app_coverage) + ": " + wp.coverage + "%")
                    Slider(value = wp.coverage.toFloat(), onValueChange = { wp.updateCoverage(it.toInt()) }, valueRange = 25f..50f)
                    Text(stringResource(R.string.app_blur) + ": " + wp.blurLevel + "%")
                    Slider(value = wp.blurLevel.toFloat(), onValueChange = { wp.updateBlur(it.toInt()) }, valueRange = 0f..100f)
                    Text(stringResource(R.string.app_speed) + ": " + wp.speed + "%")
                    Slider(value = wp.speed.toFloat(), onValueChange = { wp.updateSpeed(it.toInt()) }, valueRange = 0f..100f)
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
