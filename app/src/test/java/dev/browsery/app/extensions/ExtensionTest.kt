package dev.browsery.app.extensions

import dev.browsery.app.settings.SettingsData
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionTest {
    @Test fun statusMapping() {
        assertEquals(ExtStatus.ERROR, ExtensionCatalog.statusOf(true, true, "x"))
        assertEquals(ExtStatus.NOT_INSTALLED, ExtensionCatalog.statusOf(false, false, null))
        assertEquals(ExtStatus.ENABLED, ExtensionCatalog.statusOf(true, true, null))
        assertEquals(ExtStatus.DISABLED, ExtensionCatalog.statusOf(true, false, null))
    }

    @Test fun persistedChoicesRoundTrip() {
        val id = ExtensionCatalog.LINK_CLEAN.id
        val d = SettingsData().withExtension(id, true).withExtensionPrivate(id, true)
        assertTrue(id in d.extensionsEnabled)
        assertTrue(id in d.extensionsPrivate)
        val off = d.withExtension(id, false).withExtensionPrivate(id, false)
        assertTrue(off.extensionsEnabled.isEmpty())
        assertTrue(off.extensionsPrivate.isEmpty())
    }

    @Test fun nothingIsEnabledByDefault() {
        val d = SettingsData()
        assertTrue(d.extensionsEnabled.isEmpty())
        assertTrue(d.extensionsPrivate.isEmpty())
    }

    @Test fun catalogLookupAndUri() {
        assertNotNull(ExtensionCatalog.byId("linkclean@browsery.dev"))
        assertNull(ExtensionCatalog.byId("unknown@x"))
        assertEquals("resource://android/assets/extensions/linkclean/", ExtensionCatalog.LINK_CLEAN.uri)
    }

    /** Katalog (id dan akses situs yang ditampilkan ke pengguna) harus sama dengan manifest yang dikemas. */
    @Test fun catalogMatchesBundledManifest() {
        val manifest = File("src/main/assets/${ExtensionCatalog.LINK_CLEAN.assetDir}/manifest.json")
        assertTrue("manifest tidak ditemukan: ${manifest.absolutePath}", manifest.exists())
        val text = manifest.readText()
        assertTrue(text.contains("\"id\": \"${ExtensionCatalog.LINK_CLEAN.id}\""))
        ExtensionCatalog.LINK_CLEAN.hostAccess.forEach { assertTrue(it, text.contains("\"$it\"")) }
        assertFalse("manifest tidak boleh meminta izin API", text.contains("\"permissions\""))
        assertTrue(File("src/main/assets/${ExtensionCatalog.LINK_CLEAN.assetDir}/clean.js").exists())
    }
}
