package dev.browsery.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.browsery.app.R
import dev.browsery.app.settings.AppSettings
import dev.browsery.app.settings.CookiePolicy
import dev.browsery.app.settings.TrackingLevel

@Composable
fun PrivacyPage(settings: AppSettings, onClose: () -> Unit) {
    val s by settings.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PageHeader(stringResource(R.string.privacy_title), onClose)
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Heading(stringResource(R.string.privacy_tracking))
                Note(stringResource(R.string.privacy_tracking_desc))
                ChipRow {
                    TrackingLevel.entries.forEach { l ->
                        FilterChip(
                            selected = s.trackingLevel == l,
                            onClick = { settings.update { it.copy(trackingLevel = l) } },
                            label = {
                                Text(
                                    stringResource(
                                        when (l) {
                                            TrackingLevel.OFF -> R.string.tracking_off
                                            TrackingLevel.STANDARD -> R.string.tracking_standard
                                            TrackingLevel.STRICT -> R.string.tracking_strict
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                Heading(stringResource(R.string.privacy_cookies))
                Note(stringResource(R.string.privacy_cookies_desc))
                ChipRow {
                    CookiePolicy.entries.forEach { c ->
                        FilterChip(
                            selected = s.cookiePolicy == c,
                            onClick = { settings.update { it.copy(cookiePolicy = c) } },
                            label = {
                                Text(
                                    stringResource(
                                        when (c) {
                                            CookiePolicy.ALL -> R.string.cookie_all
                                            CookiePolicy.NON_TRACKERS -> R.string.cookie_non_trackers
                                            CookiePolicy.ISOLATE -> R.string.cookie_isolate
                                            CookiePolicy.FIRST_PARTY -> R.string.cookie_first_party
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                SwitchRow(
                    title = stringResource(R.string.privacy_https_only),
                    desc = stringResource(R.string.privacy_https_only_desc),
                    checked = s.httpsOnly,
                ) { v -> settings.update { it.copy(httpsOnly = v) } }
                SwitchRow(
                    title = stringResource(R.string.privacy_gpc),
                    desc = stringResource(R.string.privacy_gpc_desc),
                    checked = s.globalPrivacyControl,
                ) { v -> settings.update { it.copy(globalPrivacyControl = v) } }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                Heading(stringResource(R.string.privacy_exceptions))
                if (s.siteExceptions.isEmpty()) {
                    Note(stringResource(R.string.privacy_exceptions_empty))
                }
                s.siteExceptions.sorted().forEach { host ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(host, modifier = Modifier.weight(1f))
                        TextButton(onClick = { settings.update { it.withException(host, false) } }) {
                            Text(stringResource(R.string.privacy_remove))
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                Heading(stringResource(R.string.privacy_limits_title))
                Note(stringResource(R.string.privacy_limits))
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
