package app.pbbls.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CrisisResourcesTest {
    private val canonical = File("../../../packages/shared/crisis-resources.json").readText()

    @Test
    fun `the bundled asset is byte-identical to the canonical file`() {
        assertEquals(canonical, File("src/main/assets/crisis-resources.json").readText())
    }

    @Test
    fun `decodes seven countries`() {
        assertEquals(7, decodeCrisisResources(canonical).countries.size)
    }

    @Test
    fun `default country follows the region else FR`() {
        val resources = decodeCrisisResources(canonical)
        assertEquals("GB", resources.defaultCountry("gb")?.code)
        assertEquals("FR", resources.defaultCountry("XX")?.code)
        assertEquals("FR", resources.defaultCountry(null)?.code)
    }
}
