package app.pbbls.android.theme

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Mirrors `apps/ios/PebblesTests/LocalizationTests.swift`'s coverage suite:
 * every `ReferenceSlugs` entry must have an en AND fr string-resource entry,
 * and an unmapped slug must fall back to the DB name rather than crash.
 *
 * JVM unit tests here can't load Android resources through the framework (no
 * Robolectric, per `apps/android/CLAUDE.md`), so parity is checked by parsing
 * the `strings.xml` files directly — the Gradle test task's working directory
 * is the module root (`apps/android/app`), so paths are relative to that.
 */
class LocalizationParityTest {
    private fun stringKeys(relativePath: String): Set<String> {
        val file = File(relativePath)
        check(file.exists()) { "Missing resource file: ${file.absolutePath}" }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("name") }.toSet()
    }

    private val enKeys by lazy { stringKeys("src/main/res/values/strings.xml") }
    private val frKeys by lazy { stringKeys("src/main/res/values-fr/strings.xml") }

    private fun referenceKeys(): List<String> =
        ReferenceSlugs.emotions.map { "emotion_${it}_name" } +
            ReferenceSlugs.domains.map { "domain_${it}_name" } +
            ReferenceSlugs.emotionCategories.map { "emotionCategory_${it}_name" }

    @Test
    fun everyReferenceSlugHasAnEnglishStringEntry() {
        referenceKeys().forEach { key ->
            assertTrue("values/strings.xml missing key: $key", enKeys.contains(key))
        }
    }

    @Test
    fun everyReferenceSlugHasAFrenchStringEntry() {
        referenceKeys().forEach { key ->
            assertTrue("values-fr/strings.xml missing key: $key", frKeys.contains(key))
        }
    }

    @Test
    fun resourceIdIsNullForAnUnmappedSlug() {
        assertNull(ReferenceStrings.resourceId(ReferenceType.EMOTION, "not-a-real-slug-xyz"))
        assertNull(ReferenceStrings.resourceId(ReferenceType.DOMAIN, "not-a-real-slug-xyz"))
        assertNull(ReferenceStrings.resourceId(ReferenceType.EMOTION_CATEGORY, "not-a-real-slug-xyz"))
    }

    @Test
    fun everyMappedSlugResolvesToAResourceId() {
        ReferenceSlugs.emotions.forEach {
            assertTrue("emotion.$it unmapped", ReferenceStrings.resourceId(ReferenceType.EMOTION, it) != null)
        }
        ReferenceSlugs.domains.forEach {
            assertTrue("domain.$it unmapped", ReferenceStrings.resourceId(ReferenceType.DOMAIN, it) != null)
        }
        ReferenceSlugs.emotionCategories.forEach {
            val resolved = ReferenceStrings.resourceId(ReferenceType.EMOTION_CATEGORY, it)
            assertTrue("emotionCategory.$it unmapped", resolved != null)
        }
    }
}
