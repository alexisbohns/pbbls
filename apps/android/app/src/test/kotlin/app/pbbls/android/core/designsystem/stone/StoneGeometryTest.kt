package app.pbbls.android.core.designsystem.stone

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The split-stone geometry against its oracles (spec D5).
 *
 * The first block copies the iOS assertions (`PebblesTests/HomeShortcutsTests.swift`).
 * The pinned points come from two oracles, named on each test:
 * - **iOS**: `ShortcutStones.swift` compiled as is with `swiftc` (2026-10-31),
 *   for the shipping quick actions row and bento;
 * - **`stones.js`** (mock kit, 2026-10-31 Type Bench) run under node, for the
 *   options iOS does not have yet (`n = 5`, `corner`, `gutter`, `fillet`,
 *   `lean`, other seeds, single stones). The kit returns each piece resampled
 *   every 4 units, so those pins read `resample(piece, 4)`. The kit's default
 *   call matched iOS to 9 decimals at 359 × 98, 359 × 84 and 300 × 120. When
 *   the iOS generalisation (#1382) lands, its tests must reproduce these values.
 */
class StoneGeometryTest {
    private val gutter = StoneOptions().gutter

    @Test
    fun `row pieces stay in their cells and the gutters stay open`() {
        for (size in listOf(Size(359f, 84f), Size(359f, 98f), Size(300f, 120f))) {
            val pieces = StoneGeometry.splitRow(size)
            val cells = StoneGeometry.rowCells(size)
            assertEquals(3, pieces.size)
            pieces.zip(cells).forEach { (piece, cell) ->
                assertTrue(piece.isNotEmpty())
                // The cuts lean, so a piece may reach a few points past its column, never into the next one.
                assertTrue(piece.minOf { it.x } >= cell.left - gutter - 0.5f)
                assertTrue(piece.maxOf { it.x } <= cell.right + gutter + 0.5f)
                assertTrue(piece.minOf { it.y } >= -0.5f && piece.maxOf { it.y } <= size.height + 0.5f)
            }
            for (i in 0 until 2) {
                val rightOfLeft = pieces[i].filter { abs(it.y - size.height / 2) < 1 }.maxOf { it.x }
                val leftOfRight = pieces[i + 1].filter { abs(it.y - size.height / 2) < 1 }.minOf { it.x }
                assertTrue("gutter $i at $size", leftOfRight - rightOfLeft > gutter - 1)
            }
        }
    }

    @Test
    fun `bento pieces stay in their cells`() {
        val size = Size(359f, 162f)
        // iOS ShortcutsBentoLayout.leadWidth(for: 359) = (359 × 0.45).rounded()
        val leadWidth = 162f
        for (order in listOf(
            listOf("camera", "quick", "pebble"),
            listOf("quick", "camera", "pebble"),
            listOf("pebble", "camera", "quick"),
        )) {
            val pieces = StoneGeometry.splitBento(size, leadWidth, order)
            val cells = StoneGeometry.bentoCells(size, leadWidth)
            assertEquals(3, pieces.size)
            pieces.zip(cells).forEach { (piece, cell) ->
                assertTrue(piece.size > 24)
                assertTrue(
                    piece.all {
                        it.x >= cell.left - 0.5f && it.x <= cell.right + 0.5f && it.y >= cell.top - 0.5f && it.y <= cell.bottom + 0.5f
                    },
                )
            }
        }
    }

    @Test
    fun `shapes are fixed`() {
        assertEquals(StoneGeometry.splitRow(Size(359f, 98f)), StoneGeometry.splitRow(Size(359f, 98f)))
        val order = listOf("quick", "camera", "pebble")
        assertEquals(
            StoneGeometry.splitBento(Size(359f, 162f), 162f, order),
            StoneGeometry.splitBento(Size(359f, 162f), 162f, order),
        )
        // The memo is a cache, never the source: a fresh computation agrees with it.
        val memoised = StoneGeometry.splitRow(Size(359f, 98f), n = 5, seed = "first-five")
        StoneGeometry.clearMemo()
        assertEquals(memoised, StoneGeometry.splitRow(Size(359f, 98f), n = 5, seed = "first-five"))
    }

    @Test
    fun `a single rock fills its box`() {
        for (size in listOf(Size(100f, 60f), Size(48f, 46f), Size(358f, 88f))) {
            val rock = StoneGeometry.rockOutline(size.width, size.height, StoneRandom("chip-0"), StoneOptions(corner = 16f))
            assertEquals(0f, rock.minOf { it.x }, 0.01f)
            assertEquals(size.width, rock.maxOf { it.x }, 0.01f)
            assertEquals(0f, rock.minOf { it.y }, 0.01f)
            assertEquals(size.height, rock.maxOf { it.y }, 0.01f)
        }
    }

    // iOS oracle: ShortcutStones.splitRow(width: 359, height: 98), raw ray points 0 and 90.
    @Test
    fun `the default row is the iOS quick actions row`() {
        val pieces = StoneGeometry.splitRow(Size(359f, 98f))
        val expected =
            listOf(
                Offset(1.678201563f, 27.030562500f) to Offset(112.127173016f, 70.760403841f),
                Offset(124.539493651f, 27.239596159f) to Offset(234.460506349f, 70.760403841f),
                Offset(246.872826984f, 27.239596159f) to Offset(358.140134507f, 71.293439670f),
            )
        pieces.zip(expected).forEach { (piece, pin) ->
            assertEquals(180, piece.size)
            assertNear(pin.first, piece[0])
            assertNear(pin.second, piece[90])
        }
    }

    // iOS oracle: ShortcutStones.splitBento(size: 359 × 162, leadWidth: 162, lead: .quick).
    @Test
    fun `the bento is the iOS quick actions bento`() {
        val pieces = StoneGeometry.splitBento(Size(359f, 162f), 162f, listOf("quick", "camera", "pebble"))
        val expected =
            listOf(
                Triple(611, Offset(1.619957294f, 37.018563639f), Offset(162.000000000f, 124.705928328f)),
                Triple(516, Offset(172.845970580f, 74.154029420f), Offset(349.931334163f, 9.249093163f)),
                Triple(515, Offset(172.847567704f, 87.847567704f), Offset(351.043617566f, 151.987064882f)),
            )
        pieces.zip(expected).forEach { (piece, pin) ->
            assertEquals(pin.first, piece.size)
            assertNear(pin.second, piece[0])
            assertNear(pin.third, piece[piece.size / 2])
        }
    }

    // stones.js oracle: PB_STONE.split(324, 6, 5, 'first-five', { corner: 3, gutter: 5, fillet: 2, lean: 0.9 }).
    @Test
    fun `the first five stepper matches the mock kit`() {
        val pieces =
            StoneGeometry.splitRow(
                Size(324f, 6f),
                n = 5,
                seed = "first-five",
                options = StoneOptions(corner = 3f, gutter = 5f, fillet = 2f, lean = 0.9f),
            )
        assertKit(
            pieces,
            listOf(
                Triple(31, Offset(25.448534057f, 1.039575948f), Offset(38.01038068f, 5.625219699f)),
                Triple(32, Offset(89.455524105f, 0.329673089f), Offset(103.194194435f, 5.905518185f)),
                Triple(32, Offset(154.437175973f, 0.005666142f), Offset(169.535398101f, 5.997930455f)),
                Triple(32, Offset(220.775078959f, 0.218637f), Offset(234.24931732f, 5.862970957f)),
                Triple(31, Offset(288.317516835f, 0.908516959f), Offset(300.325076281f, 5.551000442f)),
            ),
        )
    }

    // stones.js oracle: PB_STONE.split(358, 88, 3, 'days|pebbles|karma').
    @Test
    fun `the counters rock matches the mock kit`() {
        val pieces = StoneGeometry.splitRow(Size(358f, 88f), n = 3, seed = "days|pebbles|karma")
        assertKit(
            pieces,
            listOf(
                Triple(89, Offset(3.447631707f, 22.797117455f), Offset(111.820071771f, 61.668553067f)),
                Triple(92, Offset(124.647405869f, 22.480285633f), Offset(233.334295664f, 65.669408387f)),
                Triple(91, Offset(246.647405869f, 22.480285633f), Offset(356.503627747f, 68.850777701f)),
            ),
        )
    }

    // stones.js oracle: PB_STONE.d(48, 46, 'chip-0', { corner: 16 }) and PB_STONE.d(48, 48, 'settings', { corner: 16 }).
    @Test
    fun `single stones match the mock kit`() {
        val chip = StoneGeometry.resample(StoneGeometry.stone(Size(48f, 46f), "chip-0", StoneOptions(corner = 16f)), 4f)
        assertEquals(41, chip.size)
        assertNear(Offset(0.179844114f, 19.231008025f), chip[0])
        assertNear(Offset(47.993182714f, 26.220224975f), chip[20])
        val settings = StoneGeometry.resample(StoneGeometry.stone(Size(48f, 48f), "settings", StoneOptions(corner = 16f)), 4f)
        assertEquals(42, settings.size)
        assertNear(Offset(0.248499981f, 18.188913831f), settings[0])
        assertNear(Offset(47.884475858f, 30.448711661f), settings[21])
    }

    @Test
    fun `resampling keeps at least 24 points`() {
        val square = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))
        assertEquals(24, StoneGeometry.resample(square, 4f).size)
    }

    private fun assertKit(
        pieces: List<List<Offset>>,
        expected: List<Triple<Int, Offset, Offset>>,
    ) {
        assertEquals(expected.size, pieces.size)
        pieces.zip(expected).forEach { (piece, pin) ->
            val resampled = StoneGeometry.resample(piece, 4f)
            assertEquals(pin.first, resampled.size)
            assertNear(pin.second, resampled[0])
            assertNear(pin.third, resampled[resampled.size / 2])
        }
    }

    private fun assertNear(
        expected: Offset,
        actual: Offset,
    ) {
        assertEquals("x of $actual", expected.x, actual.x, 1e-3f)
        assertEquals("y of $actual", expected.y, actual.y, 1e-3f)
    }
}
