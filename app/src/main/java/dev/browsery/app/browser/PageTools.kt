package dev.browsery.app.browser

import org.mozilla.geckoview.GeckoSessionSettings

/** Mode situs desktop per tab (tidak disimpan; tab baru selalu seluler). */
fun BrowserController.isDesktop(): Boolean =
    session.settings.userAgentMode == GeckoSessionSettings.USER_AGENT_MODE_DESKTOP

fun BrowserController.setDesktop(on: Boolean) {
    if (on) {
        session.settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        session.settings.viewportMode = GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
    } else {
        session.settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode = GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    }
    reload()
}
