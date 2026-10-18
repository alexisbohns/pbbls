package app.pbbls.android.features.path.spine.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The web's `card-size.test.ts`, its phone rows (spec D4: no wide spread on Android). */
class CardSizeTest {
    // The column from the spec: 290 dp on a 390 dp phone.
    private val phone = 290f

    // The spec's sizing table, width × height, rounded there; ±1 here.
    private val table: List<Triple<String, Float, Map<Int, Pair<Float, Float>>>> =
        listOf(
            Triple("16:9", 16f / 9f, mapOf(1 to (290f to 233f), 2 to (290f to 233f), 3 to (290f to 233f))),
            Triple("3:2", 3f / 2f, mapOf(1 to (290f to 253f), 2 to (290f to 253f), 3 to (290f to 253f))),
            Triple("1:1", 1f, mapOf(1 to (290f to 318f), 2 to (290f to 318f), 3 to (290f to 318f))),
            Triple("2:3", 2f / 3f, mapOf(1 to (233f to 330f), 2 to (260f to 370f), 3 to (280f to 400f))),
            Triple("9:16", 9f / 16f, mapOf(1 to (211f to 330f), 2 to (234f to 370f), 3 to (251f to 400f))),
        )

    @Test
    fun `matches the spec's table on phone`() {
        for ((label, ratio, rows) in table) {
            for (intensity in 1..3) {
                val size = CardSizes.size(intensity, ratio, phone)
                val (width, height) = rows.getValue(intensity)
                assertTrue("$label i$intensity width ${size.width}", abs(size.width - width) <= 1f)
                assertTrue("$label i$intensity height ${size.height}", abs(size.height - height) <= 1f)
            }
        }
    }

    @Test
    fun `never lets a fitted card exceed its cap`() {
        for ((_, ratio, _) in table) {
            for (intensity in 1..3) {
                val size = CardSizes.size(intensity, ratio, phone)
                assertTrue(size.height <= size.cap + 1e-4f)
            }
        }
    }

    @Test
    fun `floors a very tall photo at 180 and lets it exceed its cap`() {
        val size = CardSizes.size(1, 0.4f, phone)
        assertEquals(CardSizes.MIN_WIDTH, size.width)
        assertTrue(size.height > size.cap)
    }

    @Test
    fun `never floors past the column itself`() {
        assertEquals(160f, CardSizes.size(1, 0.3f, 160f).width)
    }

    @Test
    fun `holds a square while the ratio is unknown`() {
        // D14: the card is square until the photo's ratio is known.
        val square = CardSizes.size(2, 1f, phone)
        assertEquals(square, CardSizes.size(2, null, phone))
        assertEquals(square, CardSizes.size(2, Float.NaN, phone))
        assertEquals(square, CardSizes.size(2, 0f, phone))
        assertEquals(square, CardSizes.size(2, Float.POSITIVE_INFINITY, phone))
    }

    @Test
    fun `selects the cap by intensity`() {
        assertEquals(listOf(330f, 370f, 400f), (1..3).map { CardSizes.cap(it) })
    }

    @Test
    fun `thickens the painted edge with intensity`() {
        assertEquals(listOf(2f, 3.5f, 5.5f), (1..3).map { CardSizes.edge(it) })
    }

    @Test
    fun `fixes the chrome and the pending ratio`() {
        assertEquals(123f, CardSizes.CHROME_VERTICAL)
        assertEquals(95f, CardSizes.CHROME_HORIZONTAL)
        assertEquals(1f, CardSizes.PENDING_RATIO)
    }
}
