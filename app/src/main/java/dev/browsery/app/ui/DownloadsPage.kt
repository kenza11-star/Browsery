package dev.browsery.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.data.DownloadEntity
import dev.browsery.app.download.DownloadManager
import dev.browsery.app.download.DownloadSettings
import dev.browsery.app.download.DownloadState
import dev.browsery.app.download.Format

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadsPage(dm: DownloadManager, onClose: () -> Unit) {
    val items by remember { dm.items }.collectAsState(initial = emptyList<DownloadEntity>())
    var conn by remember { mutableStateOf(dm.settings.connections) }
    var confirmId by remember { mutableStateOf<Long?>(null) }
    val hasFinished = items.any { it.state == DownloadState.COMPLETED.name || it.state == DownloadState.FAILED.name }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PageHeader(stringResource(R.string.dl_title), onClose) {
                TextButton(onClick = { dm.clearFinished() }, enabled = hasFinished) {
                    Text(stringResource(R.string.dl_clear))
                }
            }
            Text(
                stringResource(R.string.dl_connections),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DownloadSettings.ALLOWED.forEach { v ->
                    FilterChip(
                        selected = conn == v,
                        onClick = {
                            dm.settings.connections = v
                            conn = v
                        },
                        label = { Text(if (v == 0) stringResource(R.string.dl_conn_auto) else v.toString()) },
                    )
                }
            }
            if (items.isEmpty()) {
                Text(
                    stringResource(R.string.dl_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { e ->
                    DownloadRow(dm, e, onDelete = { confirmId = e.id }, modifier = Modifier.animateItem())
                }
            }
        }
    }
    confirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmId = null },
            shape = Glass.Card,
            text = { Text(stringResource(R.string.dl_confirm_delete)) },
            confirmButton = {
                TextButton(onClick = { confirmId = null; dm.remove(id) }) { Text(stringResource(R.string.dl_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmId = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun DownloadRow(dm: DownloadManager, e: DownloadEntity, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val state = runCatching { DownloadState.valueOf(e.state) }.getOrDefault(DownloadState.FAILED)
    val lv = dm.live[e.id]
    val done = lv?.downloaded ?: e.downloadedBytes
    val total = lv?.total ?: e.totalBytes
    val speed = lv?.speed ?: 0L

    val status = when (state) {
        DownloadState.QUEUED -> stringResource(R.string.dl_state_queued)
        DownloadState.RUNNING ->
            if (total > 0) {
                val eta = if (speed > 0) Format.eta((total - done) / speed) else ""
                stringResource(R.string.dl_running_known, Format.bytes(done), Format.bytes(total), Format.speed(speed), eta.ifEmpty { "?" })
            } else {
                stringResource(R.string.dl_running_unknown, Format.bytes(done), Format.speed(speed))
            }
        DownloadState.PAUSED -> stringResource(R.string.dl_state_paused, Format.bytes(done))
        DownloadState.COMPLETED -> stringResource(R.string.dl_state_done, Format.bytes(if (total > 0) total else done))
        DownloadState.FAILED -> stringResource(R.string.dl_state_failed, e.error.orEmpty())
    }

    Column(modifier = modifier.fillMaxWidth().glassSurface().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(e.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(status, style = MaterialTheme.typography.bodySmall)
        if (state == DownloadState.RUNNING || (state == DownloadState.PAUSED && total > 0)) {
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                DownloadState.QUEUED, DownloadState.RUNNING ->
                    TextButton(onClick = { dm.pause(e.id) }) { Text(stringResource(R.string.dl_pause)) }
                DownloadState.PAUSED ->
                    TextButton(onClick = { dm.resume(e.id) }) { Text(stringResource(R.string.dl_resume)) }
                DownloadState.FAILED ->
                    TextButton(onClick = { dm.resume(e.id) }) { Text(stringResource(R.string.dl_retry)) }
                DownloadState.COMPLETED ->
                    TextButton(onClick = {
                        if (!dm.open(e)) Toast.makeText(ctx, R.string.dl_cannot_open, Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.dl_open)) }
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.dl_delete)) }
        }
    }
}
