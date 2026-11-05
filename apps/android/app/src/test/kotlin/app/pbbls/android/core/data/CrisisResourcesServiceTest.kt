package app.pbbls.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CrisisResourcesServiceTest {
    private val canonical = File("src/main/assets/crisis-resources.json").readText()

    @Test
    fun `decodes the bundled list`() {
        val service = CrisisResourcesService(builtIn = { canonical })
        assertEquals(7, service.resources.countries.size)
        assertEquals("https://findahelpline.com", service.resources.worldwide.url)
    }

    @Test
    fun `reads the asset once`() {
        var reads = 0
        val service =
            CrisisResourcesService(
                builtIn = {
                    reads++
                    canonical
                },
            )
        service.resources
        service.resources
        assertEquals(1, reads)
    }
}
