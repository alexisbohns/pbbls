package app.pbbls.android.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The moment readers and writers against response bodies captured from
 * production on 2026-10-13 (#1048), pasted byte for byte: jsonb spacing, jsonb
 * key order, `+00:00` offsets, microsecond `created_at`, explicit nulls, and the
 * newline PostgREST puts between `path_moments` rows. The hand-written fixtures
 * in [PathMomentDecodingTest] and [MomentDetailDecodingTest] cover the tolerant
 * edges; this class proves the decoders accept what the server really sends.
 *
 * Every body is checked against its captured byte length first, so an editor
 * that trims the trailing spaces inside the raw strings fails here, not silently.
 * The ids belong to a throwaway account deleted at the end of the capture.
 */
class MomentProductionPayloadTest {
    // supabase-kt's default decoder configuration.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the bodies are the captured bytes`() {
        assertEquals(3288, PATH_MOMENTS.length)
        assertEquals(680, PATH_MOMENTS_AFTER_DELETE_PEBBLE.length)
        assertEquals(3363, V_MOMENTS_FULL_COMPLETED.length)
        assertEquals(349, V_MOMENTS_FULL_PHOTO_ONLY.length)
        assertEquals(251, V_MOMENTS_FULL_NAMED.length)
        assertEquals(2403, RESOLVE_PEBBLE_GLYPH.length)
        assertEquals(4, RESOLVE_PEBBLE_GLYPH_UNKNOWN_ID.length)
        assertEquals(38, SAVE_MOMENT_NAMED.length)
        assertEquals(38, COMPLETE_PEBBLE.length)
    }

    @Test
    fun `path_moments decodes newest first with its three kinds of row`() {
        val rows = json.decodeFromString<List<PathMoment>>(PATH_MOMENTS)
        assertEquals(listOf(COMPLETED_ID, PHOTO_ONLY_ID, NAMED_ID), rows.map { it.id })
        assertEquals(listOf(true, false, false), rows.map { it.isCompleted })
    }

    @Test
    fun `path_moments photo-only row has a null name and a snap prefix`() {
        val row = json.decodeFromString<List<PathMoment>>(PATH_MOMENTS)[1]
        assertNull(row.name)
        assertEquals(SNAP_PREFIX, row.firstSnapPath)
        assertNull(row.pebble)
        assertEquals(Valence.NEUTRAL_MEDIUM, row.valence)
    }

    @Test
    fun `path_moments uncompleted rows have no pebble and no photo`() {
        val rows = json.decodeFromString<List<PathMoment>>(PATH_MOMENTS)
        assertEquals("Morning coffee on the balcony", rows[2].name)
        assertNull(rows[2].pebble)
        assertNull(rows[2].firstSnapPath)
        assertNull(rows[0].firstSnapPath)
    }

    @Test
    fun `path_moments completed row carries its pebble and derived glyph`() {
        val pebble = requireNotNull(json.decodeFromString<List<PathMoment>>(PATH_MOMENTS)[0].pebble)
        assertEquals(PEBBLE_ID, pebble.id)
        assertEquals(2, pebble.intensity)
        assertEquals(0, pebble.positiveness)
        assertEquals(Valence.NEUTRAL_MEDIUM, pebble.valence)
        assertEquals(Visibility.PRIVATE, pebble.visibility)
        assertEquals(EmotionRef(id = EMOTION_ID, slug = "amazed", name = "Awe"), pebble.emotion)
        assertCommunityGlyph(requireNotNull(pebble.glyph))
    }

    @Test
    fun `path_moments timestamps parse to the right instants`() {
        val rows = json.decodeFromString<List<PathMoment>>(PATH_MOMENTS)
        assertEquals(Instant.parse("2026-10-12T18:05:00Z"), rows[0].happenedAt.toInstant())
        assertEquals(Instant.parse("2026-10-13T00:20:25.314887Z"), rows[0].createdAt.toInstant())
        assertEquals(Instant.parse("2026-10-12T07:15:00Z"), rows[1].happenedAt.toInstant())
        assertEquals(Instant.parse("2026-10-13T00:20:24.890253Z"), rows[2].createdAt.toInstant())
    }

    @Test
    fun `after delete_pebble the completed moment is back to a blank stone`() {
        val rows = json.decodeFromString<List<PathMoment>>(PATH_MOMENTS_AFTER_DELETE_PEBBLE)
        assertEquals(listOf(COMPLETED_ID, PHOTO_ONLY_ID, NAMED_ID), rows.map { it.id })
        assertTrue(rows.all { it.pebble == null })
        assertEquals("Evening walk with Maya", rows[0].name)
    }

    @Test
    fun `v_moments_full completed moment decodes every relation`() {
        val moment = json.decodeFromString<List<MomentDetail>>(V_MOMENTS_FULL_COMPLETED).single()
        assertEquals(COMPLETED_ID, moment.id)
        assertEquals("Long loop by the river.", moment.description)
        assertTrue(moment.snaps.isEmpty())
        assertEquals(Instant.parse("2026-10-13T00:20:25.314887Z"), moment.createdAt.toInstant())
        val pebble = requireNotNull(moment.pebble)
        assertEquals(PEBBLE_ID, pebble.id)
        assertEquals(Visibility.PRIVATE, pebble.visibility)
        assertEquals(Valence.NEUTRAL_MEDIUM, pebble.valence)
        assertEquals(EmotionRef(id = EMOTION_ID, slug = "amazed", name = "Awe"), pebble.emotion)
        assertEquals(listOf(SoulRef(id = "42fd1cba-e62c-480c-bda1-02697596d3b2", name = "Maya")), pebble.souls)
        assertEquals(
            listOf(DomainRef(id = "55a005c8-e2af-4f40-ad19-bd6914bee484", slug = "community", name = "Community")),
            pebble.domains,
        )
        // `glyph_id` arrives as an explicit null and is not part of the model.
        assertEquals(listOf(PebbleCollection(id = "da4ecafe-8b23-4135-91ea-2d900883c8f8", name = "Autumn walks")), pebble.collections)
        assertCommunityGlyph(requireNotNull(pebble.glyph))
    }

    @Test
    fun `v_moments_full emotion carries a colour the model leaves to the palette service`() {
        val emotion =
            json
                .parseToJsonElement(V_MOMENTS_FULL_COMPLETED)
                .jsonArray[0]
                .jsonObject["pebble"]!!
                .jsonObject["emotion"]!!
                .jsonObject
        assertEquals("#A15C08", emotion["color"]?.jsonPrimitive?.content)
    }

    @Test
    fun `v_moments_full and path_moments agree on the same moment`() {
        val fromView = json.decodeFromString<List<MomentDetail>>(V_MOMENTS_FULL_COMPLETED).single().toPathMoment()
        val fromPath = json.decodeFromString<List<PathMoment>>(PATH_MOMENTS)[0]
        assertEquals(fromPath, fromView)
    }

    @Test
    fun `v_moments_full photo-only moment has explicit nulls and one snap`() {
        val moment = json.decodeFromString<List<MomentDetail>>(V_MOMENTS_FULL_PHOTO_ONLY).single()
        assertEquals(PHOTO_ONLY_ID, moment.id)
        assertNull(moment.name)
        assertNull(moment.description)
        assertNull(moment.pebble)
        assertEquals(listOf(MomentSnap(id = SNAP_ID, storagePath = SNAP_PREFIX, sortOrder = 0)), moment.snaps)
        assertEquals(SNAP_PREFIX, moment.toPathMoment().firstSnapPath)
    }

    @Test
    fun `v_moments_full named moment has empty snaps and no pebble`() {
        val moment = json.decodeFromString<List<MomentDetail>>(V_MOMENTS_FULL_NAMED).single()
        assertEquals(NAMED_ID, moment.id)
        assertEquals("Morning coffee on the balcony", moment.name)
        assertEquals("First cold morning of the season.", moment.description)
        assertTrue(moment.snaps.isEmpty())
        assertNull(moment.pebble)
        assertEquals(Instant.parse("2026-10-12T06:40:00Z"), moment.happenedAt.toInstant())
    }

    @Test
    fun `resolve_pebble_glyph returns the bare glyph object`() {
        assertCommunityGlyph(json.decodeFromString<PebbleGlyph>(RESOLVE_PEBBLE_GLYPH))
    }

    @Test
    fun `resolve_pebble_glyph answers a literal null when there is no glyph`() {
        assertNull(json.decodeFromString<PebbleGlyph?>(RESOLVE_PEBBLE_GLYPH_UNKNOWN_ID))
    }

    @Test
    fun `save_moment and complete_pebble return a bare uuid string`() {
        assertEquals(NAMED_ID, json.decodeFromString<String>(SAVE_MOMENT_NAMED))
        assertEquals(PEBBLE_ID, json.decodeFromString<String>(COMPLETE_PEBBLE))
    }

    private fun assertCommunityGlyph(glyph: PebbleGlyph) {
        assertEquals("fcdf4f49-f94b-49ca-aba6-e6c6f6837e81", glyph.id)
        assertEquals("domain:community", glyph.name)
        assertEquals("domain", glyph.source)
        assertEquals("0 0 200 200", glyph.viewBox)
        assertEquals(12, glyph.strokes.size)
        assertTrue(glyph.strokes.all { it.width == 6.0 })
        assertTrue(glyph.strokes[0].d.startsWith("M54.53,138.07 Q52.4,133.27 52.935,124.73500000000001 "))
        assertEquals("M196.4,46.87 L211.07,38.6", glyph.strokes.last().d)
    }

    private companion object {
        const val COMPLETED_ID = "a951736e-9a07-4605-bf13-e779d6f0d43a"
        const val PHOTO_ONLY_ID = "1eb35b06-46a4-4068-a91b-5321c71e50e7"
        const val NAMED_ID = "ffd58f73-0339-4590-ad0e-84b894a266d8"
        const val PEBBLE_ID = "e4ee586b-c229-4a7e-ab7d-bc820e51e1a5"

        // Not RFC 4122 v4 (version nibble `e`): the decoder must not validate uuids.
        const val EMOTION_ID = "50a68890-f6bb-e67a-6655-e84ff0b71f49"
        const val SNAP_ID = "65f7be7f-d9ff-44c9-afea-d7848f82e089"
        const val SNAP_PREFIX = "40d05913-a631-489c-a04f-08ef063b9507/65f7be7f-d9ff-44c9-afea-d7848f82e089"

        // POST rpc/path_moments {} — HTTP 200.
        const val PATH_MOMENTS =
            """[{"id":"a951736e-9a07-4605-bf13-e779d6f0d43a","name":"Evening walk with Maya","happened_at":"2026-10-12T18:05:00+00:00","created_at":"2026-10-13T00:20:25.314887+00:00","first_snap_path":null,"pebble":{"id": "e4ee586b-c229-4a7e-ab7d-bc820e51e1a5", "glyph": {"id": "fcdf4f49-f94b-49ca-aba6-e6c6f6837e81", "name": "domain:community", "source": "domain", "strokes": [{"d": "M54.53,138.07 Q52.4,133.27 52.935,124.73500000000001 Q53.47,116.2 56.67,107.66499999999999 Q59.87,99.13 61.47,98.72999999999999 Q63.07,98.33 66.535,104.6 Q70,110.87 74.66499999999999,127.135 Q79.33,143.4 79.72999999999999,125.135 Q80.13,106.87 82.66499999999999,100.2 Q85.2,93.53 87.33500000000001,91.93 Q89.47,90.33 90.8,90.33 Q92.13,90.33 95.865,98.19999999999999 Q99.6,106.07 101.865,121.39999999999999 Q104.13,136.73 106.265,123.13 Q108.4,109.53 115.065,100.6 Q121.73,91.67 123.6,92.2 Q125.47,92.73 127.735,98.86500000000001 Q130,105 134.265,123.535 Q138.53,142.07 139.865,129.935 Q141.2,117.8 143.46499999999997,113.265 Q145.73,108.73 149.065,105.80000000000001 Q152.4,102.87 155.60000000000002,103.135 Q158.8,103.4 162,107.4 Q165.2,111.4 168,125.265 Q170.8,139.13 172.135,127.8 Q173.47,116.47 176.26999999999998,111.935 Q179.07,107.4 184,103 Q188.93,98.6 192.4,98.065 Q195.87,97.53 199.2,111.66499999999999 L202.53,125.8", "width": 6}, {"d": "M59.33,71.67 Q50,76.47 50,80.07 Q50,83.67 55.864999999999995,84.07 Q61.73,84.47 64.39999999999999,78.87 Q67.07,73.27 64,73.535 L60.93,73.8", "width": 6}, {"d": "M86,57.53 Q86.53,70.87 89.465,72.33500000000001 Q92.4,73.8 95.065,69.13499999999999 Q97.73,64.47 98,60.07 Q98.27,55.67 97.33500000000001,54.335 Q96.4,53 94.265,52.735 Q92.13,52.47 91.19999999999999,54.335 L90.27,56.2", "width": 6}, {"d": "M119.07,76.2 Q126.53,73.8 129.2,68.065 Q131.87,62.33 122.67,61.93 Q113.47,61.53 112.8,65.66499999999999 Q112.13,69.8 113.72999999999999,69.935 L115.33,70.07", "width": 6}, {"d": "M154.8,90.6 Q148.4,85.53 147.735,80.465 Q147.07,75.4 153.735,74.33500000000001 Q160.4,73.27 160.935,78.6 Q161.47,83.93 157.87,85.265 L154.27,86.6", "width": 6}, {"d": "M192.4,86.33 Q187.07,81.27 183.2,81 Q179.33,80.73 179.2,83.66499999999999 Q179.07,86.6 182.535,88.065 Q186,89.53 190.265,89.4 Q194.53,89.27 198.4,85.66999999999999 Q202.27,82.07 200.53500000000003,77.8 Q198.8,73.53 196.4,72.33 Q194,71.13 190.135,71.4 L186.27,71.67", "width": 6}, {"d": "M22.53,63.13 L33.2,65.53", "width": 6}, {"d": "M33.47,50.87 L38.8,60.47", "width": 6}, {"d": "M46.27,42.6 L45.47,54.07", "width": 6}, {"d": "M178,30.07 L179.07,40.47", "width": 6}, {"d": "M190.27,46.07 L192.93,30.87", "width": 6}, {"d": "M196.4,46.87 L211.07,38.6", "width": 6}], "view_box": "0 0 200 200"}, "emotion": {"id": "50a68890-f6bb-e67a-6655-e84ff0b71f49", "name": "Awe", "slug": "amazed"}, "intensity": 2, "visibility": "private", "positiveness": 0}}, 
 {"id":"1eb35b06-46a4-4068-a91b-5321c71e50e7","name":null,"happened_at":"2026-10-12T07:15:00+00:00","created_at":"2026-10-13T00:20:25.256419+00:00","first_snap_path":"40d05913-a631-489c-a04f-08ef063b9507/65f7be7f-d9ff-44c9-afea-d7848f82e089","pebble":null}, 
 {"id":"ffd58f73-0339-4590-ad0e-84b894a266d8","name":"Morning coffee on the balcony","happened_at":"2026-10-12T06:40:00+00:00","created_at":"2026-10-13T00:20:24.890253+00:00","first_snap_path":null,"pebble":null}]"""

        // POST rpc/path_moments {} after rpc/delete_pebble on the completed moment — HTTP 200.
        const val PATH_MOMENTS_AFTER_DELETE_PEBBLE =
            """[{"id":"a951736e-9a07-4605-bf13-e779d6f0d43a","name":"Evening walk with Maya","happened_at":"2026-10-12T18:05:00+00:00","created_at":"2026-10-13T00:20:25.314887+00:00","first_snap_path":null,"pebble":null}, 
 {"id":"1eb35b06-46a4-4068-a91b-5321c71e50e7","name":null,"happened_at":"2026-10-12T07:15:00+00:00","created_at":"2026-10-13T00:20:25.256419+00:00","first_snap_path":"40d05913-a631-489c-a04f-08ef063b9507/65f7be7f-d9ff-44c9-afea-d7848f82e089","pebble":null}, 
 {"id":"ffd58f73-0339-4590-ad0e-84b894a266d8","name":"Morning coffee on the balcony","happened_at":"2026-10-12T06:40:00+00:00","created_at":"2026-10-13T00:20:24.890253+00:00","first_snap_path":null,"pebble":null}]"""

        // GET v_moments_full?select=id,name,description,happened_at,created_at,snaps,pebble&id=eq.<completed> — HTTP 200.
        // The soul and collection containment filters (`pebble=cs.{…}`) answered these same bytes.
        @Suppress("ktlint:standard:max-line-length") // one-line body, kept byte for byte
        const val V_MOMENTS_FULL_COMPLETED =
            """[{"id":"a951736e-9a07-4605-bf13-e779d6f0d43a","name":"Evening walk with Maya","description":"Long loop by the river.","happened_at":"2026-10-12T18:05:00+00:00","created_at":"2026-10-13T00:20:25.314887+00:00","snaps":[],"pebble":{"id": "e4ee586b-c229-4a7e-ab7d-bc820e51e1a5", "cards": [], "glyph": {"id": "fcdf4f49-f94b-49ca-aba6-e6c6f6837e81", "name": "domain:community", "source": "domain", "strokes": [{"d": "M54.53,138.07 Q52.4,133.27 52.935,124.73500000000001 Q53.47,116.2 56.67,107.66499999999999 Q59.87,99.13 61.47,98.72999999999999 Q63.07,98.33 66.535,104.6 Q70,110.87 74.66499999999999,127.135 Q79.33,143.4 79.72999999999999,125.135 Q80.13,106.87 82.66499999999999,100.2 Q85.2,93.53 87.33500000000001,91.93 Q89.47,90.33 90.8,90.33 Q92.13,90.33 95.865,98.19999999999999 Q99.6,106.07 101.865,121.39999999999999 Q104.13,136.73 106.265,123.13 Q108.4,109.53 115.065,100.6 Q121.73,91.67 123.6,92.2 Q125.47,92.73 127.735,98.86500000000001 Q130,105 134.265,123.535 Q138.53,142.07 139.865,129.935 Q141.2,117.8 143.46499999999997,113.265 Q145.73,108.73 149.065,105.80000000000001 Q152.4,102.87 155.60000000000002,103.135 Q158.8,103.4 162,107.4 Q165.2,111.4 168,125.265 Q170.8,139.13 172.135,127.8 Q173.47,116.47 176.26999999999998,111.935 Q179.07,107.4 184,103 Q188.93,98.6 192.4,98.065 Q195.87,97.53 199.2,111.66499999999999 L202.53,125.8", "width": 6}, {"d": "M59.33,71.67 Q50,76.47 50,80.07 Q50,83.67 55.864999999999995,84.07 Q61.73,84.47 64.39999999999999,78.87 Q67.07,73.27 64,73.535 L60.93,73.8", "width": 6}, {"d": "M86,57.53 Q86.53,70.87 89.465,72.33500000000001 Q92.4,73.8 95.065,69.13499999999999 Q97.73,64.47 98,60.07 Q98.27,55.67 97.33500000000001,54.335 Q96.4,53 94.265,52.735 Q92.13,52.47 91.19999999999999,54.335 L90.27,56.2", "width": 6}, {"d": "M119.07,76.2 Q126.53,73.8 129.2,68.065 Q131.87,62.33 122.67,61.93 Q113.47,61.53 112.8,65.66499999999999 Q112.13,69.8 113.72999999999999,69.935 L115.33,70.07", "width": 6}, {"d": "M154.8,90.6 Q148.4,85.53 147.735,80.465 Q147.07,75.4 153.735,74.33500000000001 Q160.4,73.27 160.935,78.6 Q161.47,83.93 157.87,85.265 L154.27,86.6", "width": 6}, {"d": "M192.4,86.33 Q187.07,81.27 183.2,81 Q179.33,80.73 179.2,83.66499999999999 Q179.07,86.6 182.535,88.065 Q186,89.53 190.265,89.4 Q194.53,89.27 198.4,85.66999999999999 Q202.27,82.07 200.53500000000003,77.8 Q198.8,73.53 196.4,72.33 Q194,71.13 190.135,71.4 L186.27,71.67", "width": 6}, {"d": "M22.53,63.13 L33.2,65.53", "width": 6}, {"d": "M33.47,50.87 L38.8,60.47", "width": 6}, {"d": "M46.27,42.6 L45.47,54.07", "width": 6}, {"d": "M178,30.07 L179.07,40.47", "width": 6}, {"d": "M190.27,46.07 L192.93,30.87", "width": 6}, {"d": "M196.4,46.87 L211.07,38.6", "width": 6}], "view_box": "0 0 200 200"}, "souls": [{"id": "42fd1cba-e62c-480c-bda1-02697596d3b2", "name": "Maya"}], "domains": [{"id": "55a005c8-e2af-4f40-ad19-bd6914bee484", "name": "Community", "slug": "community", "label": "Those who share my culture or values"}], "emotion": {"id": "50a68890-f6bb-e67a-6655-e84ff0b71f49", "name": "Awe", "slug": "amazed", "color": "#A15C08"}, "intensity": 2, "created_at": "2026-10-13T00:20:25.347859+00:00", "emotion_id": "50a68890-f6bb-e67a-6655-e84ff0b71f49", "updated_at": "2026-10-13T00:20:25.347859+00:00", "visibility": "private", "collections": [{"id": "da4ecafe-8b23-4135-91ea-2d900883c8f8", "name": "Autumn walks", "glyph_id": null}], "positiveness": 0}}]"""

        // Same select, id=eq.<photo-only> — HTTP 200.
        @Suppress("ktlint:standard:max-line-length") // one-line body, kept byte for byte
        const val V_MOMENTS_FULL_PHOTO_ONLY =
            """[{"id":"1eb35b06-46a4-4068-a91b-5321c71e50e7","name":null,"description":null,"happened_at":"2026-10-12T07:15:00+00:00","created_at":"2026-10-13T00:20:25.256419+00:00","snaps":[{"id": "65f7be7f-d9ff-44c9-afea-d7848f82e089", "sort_order": 0, "storage_path": "40d05913-a631-489c-a04f-08ef063b9507/65f7be7f-d9ff-44c9-afea-d7848f82e089"}],"pebble":null}]"""

        // Same select, id=eq.<named> — HTTP 200.
        @Suppress("ktlint:standard:max-line-length") // one-line body, kept byte for byte
        const val V_MOMENTS_FULL_NAMED =
            """[{"id":"ffd58f73-0339-4590-ad0e-84b894a266d8","name":"Morning coffee on the balcony","description":"First cold morning of the season.","happened_at":"2026-10-12T06:40:00+00:00","created_at":"2026-10-13T00:20:24.890253+00:00","snaps":[],"pebble":null}]"""

        // POST rpc/resolve_pebble_glyph {p_pebble_id:<pebble>} — HTTP 200.
        @Suppress("ktlint:standard:max-line-length") // one-line body, kept byte for byte
        const val RESOLVE_PEBBLE_GLYPH =
            """{"id": "fcdf4f49-f94b-49ca-aba6-e6c6f6837e81", "name": "domain:community", "source": "domain", "strokes": [{"d": "M54.53,138.07 Q52.4,133.27 52.935,124.73500000000001 Q53.47,116.2 56.67,107.66499999999999 Q59.87,99.13 61.47,98.72999999999999 Q63.07,98.33 66.535,104.6 Q70,110.87 74.66499999999999,127.135 Q79.33,143.4 79.72999999999999,125.135 Q80.13,106.87 82.66499999999999,100.2 Q85.2,93.53 87.33500000000001,91.93 Q89.47,90.33 90.8,90.33 Q92.13,90.33 95.865,98.19999999999999 Q99.6,106.07 101.865,121.39999999999999 Q104.13,136.73 106.265,123.13 Q108.4,109.53 115.065,100.6 Q121.73,91.67 123.6,92.2 Q125.47,92.73 127.735,98.86500000000001 Q130,105 134.265,123.535 Q138.53,142.07 139.865,129.935 Q141.2,117.8 143.46499999999997,113.265 Q145.73,108.73 149.065,105.80000000000001 Q152.4,102.87 155.60000000000002,103.135 Q158.8,103.4 162,107.4 Q165.2,111.4 168,125.265 Q170.8,139.13 172.135,127.8 Q173.47,116.47 176.26999999999998,111.935 Q179.07,107.4 184,103 Q188.93,98.6 192.4,98.065 Q195.87,97.53 199.2,111.66499999999999 L202.53,125.8", "width": 6}, {"d": "M59.33,71.67 Q50,76.47 50,80.07 Q50,83.67 55.864999999999995,84.07 Q61.73,84.47 64.39999999999999,78.87 Q67.07,73.27 64,73.535 L60.93,73.8", "width": 6}, {"d": "M86,57.53 Q86.53,70.87 89.465,72.33500000000001 Q92.4,73.8 95.065,69.13499999999999 Q97.73,64.47 98,60.07 Q98.27,55.67 97.33500000000001,54.335 Q96.4,53 94.265,52.735 Q92.13,52.47 91.19999999999999,54.335 L90.27,56.2", "width": 6}, {"d": "M119.07,76.2 Q126.53,73.8 129.2,68.065 Q131.87,62.33 122.67,61.93 Q113.47,61.53 112.8,65.66499999999999 Q112.13,69.8 113.72999999999999,69.935 L115.33,70.07", "width": 6}, {"d": "M154.8,90.6 Q148.4,85.53 147.735,80.465 Q147.07,75.4 153.735,74.33500000000001 Q160.4,73.27 160.935,78.6 Q161.47,83.93 157.87,85.265 L154.27,86.6", "width": 6}, {"d": "M192.4,86.33 Q187.07,81.27 183.2,81 Q179.33,80.73 179.2,83.66499999999999 Q179.07,86.6 182.535,88.065 Q186,89.53 190.265,89.4 Q194.53,89.27 198.4,85.66999999999999 Q202.27,82.07 200.53500000000003,77.8 Q198.8,73.53 196.4,72.33 Q194,71.13 190.135,71.4 L186.27,71.67", "width": 6}, {"d": "M22.53,63.13 L33.2,65.53", "width": 6}, {"d": "M33.47,50.87 L38.8,60.47", "width": 6}, {"d": "M46.27,42.6 L45.47,54.07", "width": 6}, {"d": "M178,30.07 L179.07,40.47", "width": 6}, {"d": "M190.27,46.07 L192.93,30.87", "width": 6}, {"d": "M196.4,46.87 L211.07,38.6", "width": 6}], "view_box": "0 0 200 200"}"""

        // POST rpc/resolve_pebble_glyph {p_pebble_id:<unknown uuid>} — HTTP 200, a 4-byte JSON null.
        const val RESOLVE_PEBBLE_GLYPH_UNKNOWN_ID =
            """null"""

        // POST rpc/save_moment {payload:{name, description, happened_at}} — HTTP 200.
        const val SAVE_MOMENT_NAMED =
            """"ffd58f73-0339-4590-ad0e-84b894a266d8""""

        // POST rpc/complete_pebble {payload:{moment_id, emotion_id, …}} — HTTP 200.
        const val COMPLETE_PEBBLE =
            """"e4ee586b-c229-4a7e-ab7d-bc820e51e1a5""""
    }
}
