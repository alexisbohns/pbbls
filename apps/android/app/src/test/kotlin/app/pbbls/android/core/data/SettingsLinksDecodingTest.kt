package app.pbbls.android.core.data

import app.pbbls.android.core.model.SettingsLinkSection
import app.pbbls.android.core.model.decodeSettingsLinks
import app.pbbls.android.core.model.groupSettingsLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The `settings_links` decoder (#1489) over the database's own payloads, verbatim
 * (`packages/supabase/fixtures/`): never a hand-written row.
 */
class SettingsLinksDecodingTest {
    private fun fixture(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)).readText()

    @Test
    fun `reads the database payload`() {
        val links = decodeSettingsLinks(fixture("settings-links.json"))
        assertEquals(listOf("credits", "terms", "privacy", "legal-notice", "support"), links.map { it.key })
        val support = links.single { it.key == "support" }
        assertEquals("mailto:ciao@pbbls.app", support.url)
        assertEquals(SettingsLinkSection.SUPPORT, support.section)
        assertFalse(support.opensInBrowser)
        assertTrue(links.single { it.key == "credits" }.opensInBrowser)
    }

    @Test
    fun `titles follow the locale`() {
        val notice = decodeSettingsLinks(fixture("settings-links.json")).single { it.key == "legal-notice" }
        assertEquals("Legal Notice", notice.title(Locale.ENGLISH))
        assertEquals("Mentions légales", notice.title(Locale.FRANCE))
    }

    @Test
    fun `french falls back to english`() {
        val credits = decodeSettingsLinks(fixture("settings-links-edge.json")).single { it.key == "credits" }
        assertEquals(null, credits.titleFr)
        assertEquals("Credits", credits.title(Locale.FRENCH))
    }

    @Test
    fun `drops unknown sections and schemes`() {
        val links = decodeSettingsLinks(fixture("settings-links-edge.json"))
        assertEquals(listOf("credits"), links.map { it.key })
    }

    @Test
    fun `groups in the fixed order`() {
        val groups = groupSettingsLinks(decodeSettingsLinks(fixture("settings-links.json")))
        assertEquals(
            listOf(SettingsLinkSection.LEGAL, SettingsLinkSection.ABOUT, SettingsLinkSection.SUPPORT),
            groups.map { it.first },
        )
        assertEquals(listOf("terms", "privacy", "legal-notice"), groups.first().second.map { it.key })
    }
}
