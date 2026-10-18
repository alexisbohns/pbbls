package app.pbbls.android

import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.EmotionRef
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.PathPebble
import app.pbbls.android.core.model.PathSoul
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.features.path.spine.logic.PersonsOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * The Spine's fixture week, the web's `spine-fixture-week.ts` transcribed (#1122,
 * spec §Testing): the design brief's Monday 5 to Sunday 11 October 2026, and to
 * it, as the #720 lesson asks (a fixture takes every branch the product takes):
 *
 * - Friday is left empty: a *still* day;
 * - Bruno's Sunday pebble has no glyph: an uncarved stone;
 * - a fourteen-person picnic on Thursday: the people line's overflow;
 * - an untitled photo on Saturday: a moment from the split with no name;
 * - two moments that are not pebbles yet (#1050): a named one on Wednesday, set
 *   as type, and an untitled photo on Sunday morning: blank stones.
 *
 * Times are Paris local (summer time). Photos are the web's `public/sandbox/`
 * files; previews never fetch them, so [ratios] primes what the probe would
 * learn. Data only: the previews compose it.
 */
internal object SpineFixtureWeek {
    val weekStart: LocalDate = LocalDate.of(2026, 10, 5)
    val zone: ZoneId = ZoneId.of("Europe/Paris")

    /** A Sunday evening of the fixture week: every day of it is in the past but today. */
    val today: LocalDate = LocalDate.of(2026, 10, 11)

    /** The weeks the fixture's links turn to, as the roll would name them: the calendar's neighbours here. */
    val previousNumber = 40
    val nextNumber = 42

    private const val VIEW_BOX = "0 0 200 200"

    private fun stroke(
        d: String,
        width: Double,
    ) = GlyphStroke(d = d, width = width)

    private val identityStrokes =
        listOf(
            stroke(
                "M48.67,134.33 Q62.53,106.07 70.265,93.8 Q78,81.53 83.6,80.6 Q89.2,79.67 98.66499999999999,86.2 Q108.13,92.73 113.865,93.4 Q119.6,94.07 129.2,79.27 Q138.8,64.47 155.865,47.67 Q172.93,30.87 180.66500000000002,54.47 Q188.4,78.07 200,96.735 L211.6,115.4",
                6.0,
            ),
            stroke(
                "M152.67,64.2 Q153.47,72.2 155.87,76.6 Q158.27,81 159.335,81 Q160.4,81 163.465,75.4 Q166.53,69.8 172,73.13499999999999 Q177.47,76.47 180.4,76.6 L183.33,76.73",
                6.0,
            ),
            stroke("M72.13,41 Q81.47,42.33 84,39.8 L86.53,37.27", 6.0),
            stroke("M36.67,69.53 Q47.6,70.33 50.135000000000005,72.33 Q52.67,74.33 60.535000000000004,69.66499999999999 L68.4,65", 6.0),
            stroke("M105.47,123.67 L107.07,152.47", 6.0),
            stroke("M101.2,129 Q107.33,133 109.86500000000001,131.135 L112.4,129.27", 6.0),
            stroke("M95.6,135.67 Q104.67,140.47 106.67,140.47 Q108.67,140.47 111.47,138.33499999999998 L114.27,136.2", 6.0),
            stroke("M94,145.27 Q91.6,145.53 98.13499999999999,149.4 Q104.67,153.27 110.935,150.335 L117.2,147.4", 6.0),
            stroke("M161.2,128.47 L161.47,154.6", 6.0),
            stroke("M157.47,132.73 Q162.8,138.07 164.4,134.6 L166,131.13", 6.0),
            stroke("M152.13,140.73 Q156.93,145 160.265,144.735 Q163.6,144.47 165.735,142.6 L167.87,140.73", 6.0),
            stroke("M149.2,151.4 Q156.67,152.2 160.26999999999998,154.2 Q163.87,156.2 169.2,152.865 L174.53,149.53", 6.0),
        )

    private val travelStrokes =
        listOf(
            stroke(
                "M48.67,134.33 Q62.53,106.07 70.265,93.8 Q78,81.53 83.6,80.6 Q89.2,79.67 98.66499999999999,86.2 Q108.13,92.73 113.865,93.4 Q119.6,94.07 129.2,79.27 Q138.8,64.47 155.865,47.67 Q172.93,30.87 180.66500000000002,54.47 Q188.4,78.07 200,96.735 L211.6,115.4",
                6.0,
            ),
            stroke(
                "M152.67,64.2 Q153.47,72.2 155.87,76.6 Q158.27,81 159.335,81 Q160.4,81 163.465,75.4 Q166.53,69.8 172,73.13499999999999 Q177.47,76.47 180.4,76.6 L183.33,76.73",
                6.0,
            ),
            stroke("M72.13,41 Q81.47,42.33 84,39.8 L86.53,37.27", 6.0),
            stroke("M36.67,69.53 Q47.6,70.33 50.135000000000005,72.33 Q52.67,74.33 60.535000000000004,69.66499999999999 L68.4,65", 6.0),
            stroke("M105.47,123.67 L107.07,152.47", 6.0),
            stroke("M101.2,129 Q107.33,133 109.86500000000001,131.135 L112.4,129.27", 6.0),
            stroke("M95.6,135.67 Q104.67,140.47 106.67,140.47 Q108.67,140.47 111.47,138.33499999999998 L114.27,136.2", 6.0),
            stroke("M94,145.27 Q91.6,145.53 98.13499999999999,149.4 Q104.67,153.27 110.935,150.335 L117.2,147.4", 6.0),
            stroke("M161.2,128.47 L161.47,154.6", 6.0),
            stroke("M157.47,132.73 Q162.8,138.07 164.4,134.6 L166,131.13", 6.0),
            stroke("M152.13,140.73 Q156.93,145 160.265,144.735 Q163.6,144.47 165.735,142.6 L167.87,140.73", 6.0),
            stroke("M149.2,151.4 Q156.67,152.2 160.26999999999998,154.2 Q163.87,156.2 169.2,152.865 L174.53,149.53", 6.0),
        )

    private val friendsStrokes =
        listOf(
            stroke(
                "M15.44,87.11 Q29.89,100.44 42.335,106 Q54.78,111.56 68.33500000000001,111.225 Q81.89,110.89 100.78,103.66499999999999 Q119.67,96.44 128,94.775 Q136.33,93.11 150.885,93.11 Q165.44,93.11 169.32999999999998,94.66499999999999 Q173.22,96.22 177.11,100.11 L181,104",
                6.0,
            ),
            stroke(
                "M54.11,164.22 Q54.33,96.22 57.665,87.22 Q61,78.22 65.11,77.33 Q69.22,76.44 74.66499999999999,94 Q80.11,111.56 84.11,138.89 L88.11,166.22",
                6.0,
            ),
            stroke(
                "M119.22,158.67 Q121,87.78 124,75.78 Q127,63.78 130.33499999999998,64.78 Q133.67,65.78 137.225,72.225 Q140.78,78.67 144.445,102.89 Q148.11,127.11 148.775,141.11 L149.44,155.11",
                6.0,
            ),
            stroke(
                "M59,56.22 Q69.44,56 69.66499999999999,60.22 Q69.89,64.44 66.555,65.555 Q63.22,66.67 62.445,60.89 Q61.67,55.11 67.33500000000001,44.775 L73,34.44",
                6.0,
            ),
            stroke(
                "M136.33,41.78 Q127.89,46.22 126.33500000000001,49 Q124.78,51.78 130.445,52.225 Q136.11,52.67 137.11,48.78 Q138.11,44.89 130.445,39 Q122.78,33.11 118.445,32.555 L114.11,32",
                6.0,
            ),
        )

    private val familyStrokes =
        listOf(
            stroke("M42.78,151.33 Q71.44,143.33 94,140.775 Q116.56,138.22 130.335,138.555 Q144.11,138.89 158.11,144 L172.11,149.11", 6.0),
            stroke("M58.11,129.78 L64.56,143.56", 6.0),
            stroke(
                "M88.11,139.78 Q83.44,119.11 84.44,111.555 Q85.44,104 89,103.445 Q92.56,102.89 95.225,104.225 Q97.89,105.56 102,111.445 L106.11,117.33",
                6.0,
            ),
            stroke(
                "M93.44,104.22 Q85.44,101.33 83.88499999999999,95.66499999999999 Q82.33,90 88.66499999999999,88.78 Q95,87.56 96.33500000000001,93.67 Q97.67,99.78 92.555,101.225 L87.44,102.67",
                6.0,
            ),
            stroke(
                "M148.11,136.89 Q143.22,112.89 139.11,107.445 Q135,102 131.78,101.11 Q128.56,100.22 128.56,95.22 Q128.56,90.22 121.33500000000001,88.775 Q114.11,87.33 113.22,92.445 Q112.33,97.56 118.66499999999999,98.225 Q125,98.89 122.445,106.445 L119.89,114",
                6.0,
            ),
            stroke(
                "M107.67,138.22 Q105.44,135.56 104.775,129.67000000000002 Q104.11,123.78 107.22,123.78 Q110.33,123.78 110.66499999999999,129.33499999999998 L111,134.89",
                6.0,
            ),
            stroke(
                "M126.78,135.56 Q124.78,132.44 124.67,123.66499999999999 Q124.56,114.89 128.115,114.33500000000001 Q131.67,113.78 132.89,117.11 Q134.11,120.44 133.22000000000003,129.22 L132.33,138",
                6.0,
            ),
            stroke("M49.67,66.67 Q61.89,69.11 65.11,64.89 L68.33,60.67", 6.0),
            stroke("M127.22,38.22 Q132.33,42.89 133.775,42.89 Q135.22,42.89 137.22,40.555 L139.22,38.22", 6.0),
        )

    private val hobbiesStrokes =
        listOf(
            stroke(
                "M80.3,79.73 Q72.23,80.22 70.03,84.00999999999999 Q67.83,87.8 67.955,90.36500000000001 Q68.08,92.93 71.99000000000001,92.93 Q75.9,92.93 78.95500000000001,91.1 Q82.01,89.27 83.355,86.82499999999999 Q84.7,84.38 84.21000000000001,79.125 Q83.72,73.87 78.1,61.400000000000006 Q72.48,48.93 72.48,44.775 Q72.48,40.62 75.045,38.91 Q77.61,37.2 84.33500000000001,36.71 Q91.06,36.22 95.33500000000001,40.620000000000005 Q99.61,45.02 100.345,51.255 Q101.08,57.49 94.47999999999999,57.245000000000005 Q87.88,57 87.755,54.435 Q87.63,51.87 90.445,50.644999999999996 L93.26,49.42",
                4.0,
            ),
            stroke(
                "M168.3,122.76 Q158.77,120.8 157.18,126.78999999999999 Q155.59,132.78 160.965,133.025 Q166.34,133.27 169.4,130.58 Q172.46,127.89 172.46,123.97999999999999 Q172.46,120.07 166.47,106.99 Q160.48,93.91 165.61,93.545 Q170.74,93.18 181.255,86.45500000000001 Q191.77,79.73 195.315,88.53 Q198.86,97.33 198.98000000000002,103.685 Q199.1,110.04 197.265,111.875 Q195.43,113.71 191.275,114.32 Q187.12,114.93 186.755,111.265 Q186.39,107.6 189.935,105.4 L193.48,103.2",
                4.0,
            ),
            stroke(
                "M130.17,82.18 Q123.32,81.44 122.22,86.82 Q121.12,92.2 126.86500000000001,92.69 Q132.61,93.18 137.745,76.555 Q142.88,59.93 147.155,57.730000000000004 Q151.43,55.53 151.8,63.355000000000004 L152.17,71.18",
                4.0,
            ),
            stroke(
                "M104.74,130.09 Q97.66,131.56 97.9,138.525 Q98.14,145.49 102.53999999999999,145.49 Q106.94,145.49 109.50999999999999,139.5 Q112.08,133.51 111.83500000000001,127.155 Q111.59,120.8 107.92500000000001,110.78 Q104.26,100.76 108.045,101.49000000000001 Q111.83,102.22 115.375,108.945 L118.92,115.67",
                4.0,
            ),
            stroke(
                "M64.66,130.09 Q56.1,131.31 55,134 Q53.9,136.69 57.32,136.69 Q60.74,136.69 61.11,132.78 Q61.48,128.87 52.31,113.1 Q43.14,97.33 47.42,97.33 Q51.7,97.33 55.735,101.245 L59.77,105.16",
                4.0,
            ),
        )

    private val partnerStrokes =
        listOf(
            stroke(
                "M111.83,155.51 Q82.26,140.6 73.09,132.29 Q63.92,123.98 58.91,111.635 Q53.9,99.29 54.144999999999996,87.68 Q54.39,76.07 60.99,69.835 Q67.59,63.6 77.49000000000001,60.91 Q87.39,58.22 94.35499999999999,58.345 Q101.32,58.47 106.945,60.915 Q112.57,63.36 116.6,66.78 Q120.63,70.2 126.255,86.09 Q131.88,101.98 132.49,114.2 Q133.1,126.42 129.68,134.365 Q126.26,142.31 123.08000000000001,145.365 Q119.9,148.42 118.065,148.42 Q116.23,148.42 115.375,145.61 Q114.52,142.8 114.03,131.19 Q113.54,119.58 120.755,97.455 Q127.97,75.33 130.78,71.42 Q133.59,67.51 141.29000000000002,61.400000000000006 Q148.99,55.29 152.29000000000002,53.825 Q155.59,52.36 161.09,52.6 Q166.59,52.84 171.35500000000002,57.120000000000005 Q176.12,61.4 179.79000000000002,70.81 Q183.46,80.22 183.57999999999998,90.36500000000001 Q183.7,100.51 181.99,105.275 Q180.28,110.04 176,117.255 Q171.72,124.47 163.655,132.655 Q155.59,140.84 148.865,145 Q142.14,149.16 129.555,152.45999999999998 L116.97,155.76",
                4.0,
            ),
            stroke(
                "M105.72,48.44 Q97.17,50.4 87.515,49.91 Q77.86,49.42 74.435,44.29 Q71.01,39.16 70.89,31.945 Q70.77,24.73 76.515,22.045 Q82.26,19.36 89.96000000000001,18.869999999999997 Q97.66,18.38 98.39,30.11 Q99.12,41.84 91.545,42.94 Q83.97,44.04 82.99000000000001,41.474999999999994 Q82.01,38.91 82.745,34.51 L83.48,30.11",
                4.0,
            ),
            stroke(
                "M149.72,28.89 Q133.83,32.8 129.8,36.345 Q125.77,39.89 125.28,41.845 Q124.79,43.8 140.19,44.165 Q155.59,44.53 166.59,38.175 Q177.59,31.82 167.445,31.82 Q157.3,31.82 149.11,35.245000000000005 L140.92,38.67",
                4.0,
            ),
            stroke("M31.66,74.11 L43.63,74.36", 4.0),
            stroke("M41.19,55.29 L47.79,64.09", 4.0),
        )

    /** The brief's six glyphs, 200 × 200, engine stroke width. */
    val glyphs: Map<String, Glyph> =
        listOf(
            Glyph(id = "glyph-identity", name = "identity", strokes = identityStrokes, viewBox = VIEW_BOX),
            Glyph(id = "glyph-travel", name = "travel", strokes = travelStrokes, viewBox = VIEW_BOX),
            Glyph(id = "glyph-friends", name = "friends", strokes = friendsStrokes, viewBox = VIEW_BOX),
            Glyph(id = "glyph-family", name = "family", strokes = familyStrokes, viewBox = VIEW_BOX),
            Glyph(id = "glyph-hobbies", name = "hobbies", strokes = hobbiesStrokes, viewBox = VIEW_BOX),
            Glyph(id = "glyph-partner", name = "partner", strokes = partnerStrokes, viewBox = VIEW_BOX),
        ).associateBy { it.id }

    private fun soul(
        id: String,
        name: String,
        glyph: String,
    ) = SoulWithGlyph(id = "soul-$id", name = name, glyphId = "glyph-$glyph", glyph = glyphs.getValue("glyph-$glyph"))

    private val neighbours: List<SoulWithGlyph> =
        listOf(
            soul("ana", "Ana", "friends"),
            soul("bea", "Béa", "hobbies"),
            soul("cyril", "Cyril", "identity"),
            soul("dora", "Dora", "travel"),
            soul("ezra", "Ezra", "family"),
            soul("felix", "Félix", "partner"),
            soul("gaby", "Gaby", "friends"),
            soul("hugo", "Hugo", "hobbies"),
            soul("ines", "Inès", "travel"),
        )

    /** The five named souls of the brief, then the nine neighbours of the picnic. */
    val souls: List<SoulWithGlyph> =
        listOf(
            soul("mara", "Mara", "partner"),
            soul("elio", "Elio", "family"),
            soul("nadia", "Nadia", "friends"),
            soul("jules", "Jules", "hobbies"),
            soul("bruno", "Bruno", "travel"),
        ) + neighbours

    val soulsById: Map<String, SoulWithGlyph> = souls.associateBy { it.id }

    private val neighbourKeys = neighbours.map { it.id.removePrefix("soul-") }

    /** The web's `EMOTIONS` rows the fixture names, by slug. */
    private val emotions: Map<String, EmotionRef> =
        listOf(
            EmotionRef(id = "ed9fbe9f-6a93-22bb-d5d5-35f42164c120", slug = "proud", name = "Pride"),
            EmotionRef(id = "3ffd6ca6-e033-6926-fa1f-ddf8f90bfd8c", slug = "joyful", name = "Joy"),
            EmotionRef(id = "8a851a45-d1c5-92fd-fd50-098c4e86eaef", slug = "angry", name = "Anger"),
            EmotionRef(id = "21d79e4c-9482-40d3-bf73-53adfb452e5a", slug = "peaceful", name = "Peaceful"),
            EmotionRef(id = "8b2993f3-4043-7bdf-388d-c0e3d5c96f02", slug = "scared", name = "Fear"),
            EmotionRef(id = "075ec40d-f27c-033d-a498-850947e4e887", slug = "sad", name = "Sadness"),
        ).associateBy { it.slug }

    /**
     * The real `emotion_categories` palettes as the web's `sandbox-palettes.ts`
     * carries them, by category; [palettes] joins a pebble to its category by slug.
     */
    private fun category(
        primary: String,
        secondary: String,
        light: String,
        surface: String,
        shaded: String,
        dark: String,
    ): EmotionPalette =
        requireNotNull(
            EmotionPalette.fromHex(
                primaryHex = primary,
                secondaryHex = secondary,
                lightHex = light,
                surfaceHex = surface,
                darkHex = dark,
                shadedHex = shaded,
            ),
        )

    private val categories: Map<String, EmotionPalette> =
        mapOf(
            "fear" to category("#7B5E99FF", "#AE91CCFF", "#F2EFF5FF", "#7B5E991A", "#56426BFF", "#19131FFF"),
            "sadness" to category("#59658AFF", "#8C98BDFF", "#EEF0F3FF", "#59658A1A", "#3E4761FF", "#12141CFF"),
            "pride" to category("#A9478AFF", "#EA91CEFF", "#F6EDF3FF", "#A9478A1A", "#763261FF", "#220E1CFF"),
            "joy" to category("#A15C08FF", "#CF8C39FF", "#FAF6EAFF", "#A15C081A", "#714006FF", "#201202FF"),
            "peace" to category("#487C5AFF", "#80BF96FF", "#EDF2EEFF", "#487C5A1A", "#32573FFF", "#0E1912FF"),
            "anger" to category("#8E4242FF", "#C17575FF", "#F4ECECFF", "#8E42421A", "#632E2EFF", "#1C0D0DFF"),
            "shame" to category("#868686FF", "#B9B9B9FF", "#F3F3F3FF", "#8686861A", "#5E5E5EFF", "#1B1B1BFF"),
        )

    private val categoryBySlug: Map<String, String> =
        mapOf(
            "proud" to "pride",
            "joyful" to "joy",
            "angry" to "anger",
            "peaceful" to "peace",
            "scared" to "fear",
            "sad" to "sadness",
            "ashamed" to "shame",
        )

    /** The pebble's palette by its emotion's slug; null for a moment with no pebble. */
    val palettes: (PathMoment) -> EmotionPalette? = { moment ->
        moment.pebble
            ?.emotion
            ?.slug
            ?.let { slug -> categoryBySlug[slug]?.let(categories::get) }
    }

    private class PebbleSpec(
        val intensity: Int,
        val positiveness: Int,
        val emotion: String,
        val souls: List<String>,
        /** Null for Bruno's uncarved stone. */
        val glyph: String?,
    )

    private fun moment(
        n: Int,
        name: String,
        at: String,
        photo: String? = null,
        pebble: PebbleSpec? = null,
    ): PathMoment {
        val id = "spine-fixture-" + n.toString().padStart(2, '0')
        val happened = OffsetDateTime.parse("2026-10-$at:00+02:00")
        val glyph = pebble?.glyph?.let { glyphs.getValue("glyph-$it") }
        return PathMoment(
            id = id,
            name = name.ifEmpty { null },
            happenedAt = happened,
            createdAt = happened,
            firstSnapPath = photo?.let { prefix(it) },
            pebble =
                pebble?.let { p ->
                    PathPebble(
                        // The backfilled shape (D9): the pebble keeps its moment's id.
                        id = id,
                        intensity = p.intensity,
                        positiveness = p.positiveness,
                        emotion = emotions.getValue(p.emotion),
                        glyph = glyph?.let { PebbleGlyph(id = it.id, name = it.name, strokes = it.strokes, viewBox = it.viewBox) },
                        souls = p.souls.map { key -> soulsById.getValue("soul-$key").let { PathSoul(it.id, it.name, it.glyphId) } },
                    )
                },
        )
    }

    private fun prefix(file: String) = "fixture/snap-$file"

    val entry: WeekRollEntry =
        WeekRollEntry(
            weekStart = weekStart,
            moments =
                listOf(
                    moment(1, "Flag march with Nadia", "05T10:15", "flag-march.jpg", PebbleSpec(3, 1, "proud", listOf("nadia"), "friends")),
                    moment(
                        2,
                        "Elio lost his first tooth",
                        "05T19:40",
                        pebble = PebbleSpec(2, 1, "joyful", listOf("elio", "mara"), "family"),
                    ),
                    moment(3, "Rent went up again", "06T08:02", pebble = PebbleSpec(2, -1, "angry", emptyList(), "identity")),
                    moment(
                        4,
                        "Forest walk, all of us",
                        "06T16:30",
                        "forest-crew.jpg",
                        PebbleSpec(3, 1, "peaceful", listOf("mara", "elio", "bruno"), "travel"),
                    ),
                    moment(5, "Couldn’t sleep", "07T03:10", pebble = PebbleSpec(1, -1, "scared", emptyList(), "identity")),
                    moment(
                        6,
                        "Coffee by the fountain",
                        "07T12:05",
                        "fountain.jpg",
                        PebbleSpec(1, 0, "peaceful", listOf("jules"), "friends"),
                    ),
                    moment(
                        7,
                        "Rooftop birthday",
                        "08T21:00",
                        "rooftop-group.jpg",
                        PebbleSpec(3, 1, "joyful", listOf("nadia", "jules", "mara"), "hobbies"),
                    ),
                    moment(
                        12,
                        "Picnic on the square, the whole street",
                        "08T13:10",
                        pebble = PebbleSpec(2, 1, "joyful", listOf("mara", "elio", "nadia", "jules", "bruno") + neighbourKeys, "friends"),
                    ),
                    moment(8, "Said no to the extra project", "10T09:20", pebble = PebbleSpec(2, 0, "proud", emptyList(), "identity")),
                    moment(
                        9,
                        "Mara on the seawall",
                        "10T17:45",
                        "seawall-portrait.jpg",
                        PebbleSpec(2, 1, "joyful", listOf("mara"), "partner"),
                    ),
                    moment(13, "", "10T19:05", "fountain.jpg", PebbleSpec(1, 0, "peaceful", listOf("mara"), "partner")),
                    moment(10, "Grandma’s call went quiet", "11T14:00", pebble = PebbleSpec(2, -1, "sad", emptyList(), "family")),
                    moment(11, "Bruno asleep on my feet", "11T22:30", pebble = PebbleSpec(1, 0, "peaceful", listOf("bruno"), null)),
                    // Not pebbles yet (#1050): blank stones, one set as type, one on a card.
                    moment(14, "Bread from the corner bakery", "07T18:20"),
                    moment(15, "", "11T09:10", "forest-crew.jpg"),
                ),
        )

    /** The fourteen-person picnic alone: the people line's overflow. */
    val picnic: PathMoment = entry.moments.first { it.id == "spine-fixture-12" }

    /** Width / height of the sandbox photos, what the ratio probe would learn, by storage prefix. */
    val ratios: Map<String, Float> =
        mapOf(
            prefix("flag-march.jpg") to 1200f / 1200f,
            prefix("forest-crew.jpg") to 1200f / 800f,
            prefix("fountain.jpg") to 1200f / 800f,
            prefix("rooftop-group.jpg") to 1200f / 675f,
            prefix("seawall-portrait.jpg") to 1200f / 1800f,
        )

    /** Soul ids in persons-of-the-week order, the order the people lines name them in. */
    val personsOrder: List<String> =
        PersonsOfWeek.of(entry.moments, weekStart, zone, Locale.ENGLISH).map { it.soul.id }
}
