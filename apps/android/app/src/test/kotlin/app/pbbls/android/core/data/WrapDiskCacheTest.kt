package app.pbbls.android.core.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

class WrapDiskCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val paris = ZoneId.of("Europe/Paris")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun cache(
        root: File,
        dispatcher: CoroutineDispatcher,
    ) = WrapDiskCache(root, dispatcher)

    @Test
    fun `a page reads back for its owner and zone only`() =
        runTest {
            val root = folder.newFolder("path-wraps")
            val cache = cache(root, StandardTestDispatcher(testScheduler))
            cache.write("u1", paris, "week-2026-10-05", """{"count":11}""")

            assertEquals("""{"count":11}""", cache.read("u1", paris, "week-2026-10-05"))
            assertNull("another user never reads it", cache.read("u2", paris, "week-2026-10-05"))
            assertNull("another zone never reads it", cache.read("u1", tokyo, "week-2026-10-05"))
            assertNull(cache.read("u1", paris, "week-2026-10-12"))
        }

    @Test
    fun `a kept page knows when it was kept, a missing one does not`() =
        runTest {
            val root = folder.newFolder("path-wraps")
            val cache = cache(root, StandardTestDispatcher(testScheduler))
            assertNull(cache.keptAt("u1", paris, "achievements-page"))
            cache.write("u1", paris, "achievements-page", "{}")
            assertNotNull(cache.keptAt("u1", paris, "achievements-page"))
            assertNull(cache.keptAt("u1", tokyo, "achievements-page"))
        }

    @Test
    fun `a rewrite replaces the page, and a format bump drops the old tree`() =
        runTest {
            val root = folder.newFolder("path-wraps")
            File(root, "v0/u1").mkdirs()
            val cache = cache(root, StandardTestDispatcher(testScheduler))
            cache.write("u1", paris, "strip", "a")
            cache.write("u1", paris, "strip", "b")

            assertEquals("b", cache.read("u1", paris, "strip"))
            assertFalse("the old format's files are gone", File(root, "v0").exists())
            assertTrue(File(root, "v${WrapDiskCache.SCHEMA_VERSION}").exists())
        }

    @Test
    fun `clearing on sign-out removes every user's wraps`() =
        runTest {
            val root = folder.newFolder("path-wraps")
            val cache = cache(root, StandardTestDispatcher(testScheduler))
            cache.write("u1", paris, "strip", "a")
            cache.write("u2", tokyo, "strip", "b")

            cache.clearAll()

            assertNull(cache.read("u1", paris, "strip"))
            assertNull(cache.read("u2", tokyo, "strip"))
        }

    @Test
    fun `a page name cannot climb out of the tree`() =
        runTest {
            val root = folder.newFolder("path-wraps")
            val cache = cache(root, StandardTestDispatcher(testScheduler))
            cache.write("../u1", paris, "../../escape", "x")

            assertFalse(File(folder.root, "escape.json").exists())
            assertEquals("x", cache.read("../u1", paris, "../../escape"))
        }
}
