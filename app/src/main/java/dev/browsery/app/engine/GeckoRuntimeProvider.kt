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

    private fun apply(rt: GeckoRuntime, d: SettingsData) {
        val etp = when (d.trackingLevel) {
            TrackingLevel.OFF -> ContentBlocking.EtpLevel.NONE
            TrackingLevel.STANDARD -> ContentBlocking.EtpLevel.DEFAULT
            TrackingLevel.STRICT -> ContentBlocking.EtpLevel.STRICT
        }
        val cookies = when (d.cookiePolicy) {
            CookiePolicy.ALL -> ContentBlocking.CookieBehavior.ACCEPT_ALL
            CookiePolicy.NON_TRACKERS -> ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
            CookiePolicy.ISOLATE -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
            CookiePolicy.FIRST_PARTY -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
        }
        val s = rt.settings
        s.contentBlocking.setEnhancedTrackingProtectionLevel(etp)
        s.contentBlocking.setCookieBehavior(cookies)
        s.setGlobalPrivacyControl(d.globalPrivacyControl)
        s.setAllowInsecureConnections(
            if (d.httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL,
        )
    }
}
