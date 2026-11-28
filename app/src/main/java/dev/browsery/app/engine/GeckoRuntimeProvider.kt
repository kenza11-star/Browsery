package dev.browsery.app.engine

import android.content.Context
import dev.browsery.app.settings.CookiePolicy
import dev.browsery.app.settings.SettingsData
import dev.browsery.app.settings.TrackingLevel
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/** GeckoRuntime hanya boleh dibuat satu kali per proses. */
object GeckoRuntimeProvider {
    @Volatile
    private var runtime: GeckoRuntime? = null

    @Volatile
    private var privacy: SettingsData? = null

    fun get(context: Context): GeckoRuntime =
        runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(context.applicationContext).also {
                runtime = it
                privacy?.let { p -> apply(it, p) }
            }
        }

    /** Menyimpan pengaturan privasi; diterapkan segera bila runtime sudah ada, atau saat runtime dibuat. */
    fun configure(data: SettingsData) {
        privacy = data
        runtime?.let { apply(it, data) }
    }

    // Konstanta engine dipakai langsung pada tiap cabang agar pemeriksaan IntDef (lint) dapat memverifikasinya.
    private fun apply(rt: GeckoRuntime, d: SettingsData) {
        val s = rt.settings
        val cb = s.contentBlocking
        when (d.trackingLevel) {
            TrackingLevel.OFF -> cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.NONE)
            TrackingLevel.STANDARD -> cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
            TrackingLevel.STRICT -> cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
        }
        when (d.cookiePolicy) {
            CookiePolicy.ALL -> cb.setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_ALL)
            CookiePolicy.NON_TRACKERS -> cb.setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
            CookiePolicy.ISOLATE ->
                cb.setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            CookiePolicy.FIRST_PARTY -> cb.setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY)
        }
        s.setGlobalPrivacyControl(d.globalPrivacyControl)
        if (d.webDark) {
            s.setPreferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
        } else {
            s.setPreferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
        }
        if (d.httpsOnly) {
            s.setAllowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)
        } else {
            s.setAllowInsecureConnections(GeckoRuntimeSettings.ALLOW_ALL)
        }
    }
}
