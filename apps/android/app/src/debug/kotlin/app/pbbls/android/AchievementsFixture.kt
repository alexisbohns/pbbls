package app.pbbls.android

import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionFamily
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.EmotionWithPalette
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.Completion
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.Coverage
import app.pbbls.android.core.model.achievements.DomainEntry
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionSeat
import app.pbbls.android.core.model.achievements.EmotionsFamilyGroup
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerRing
import app.pbbls.android.core.model.achievements.ExplorerSection
import app.pbbls.android.core.model.achievements.FirstFiveItem
import app.pbbls.android.core.model.achievements.FirstStep
import app.pbbls.android.core.model.achievements.FirstSubject
import app.pbbls.android.core.model.achievements.MonthDone
import app.pbbls.android.core.model.achievements.MonthState
import app.pbbls.android.core.model.achievements.Needs
import app.pbbls.android.core.model.achievements.NextLevel
import app.pbbls.android.core.model.achievements.NextStep
import app.pbbls.android.core.model.achievements.PebbleSnapshot
import app.pbbls.android.core.model.achievements.ShelfItem
import app.pbbls.android.core.model.achievements.SixFirst
import app.pbbls.android.core.model.achievements.StackLevel
import app.pbbls.android.core.model.achievements.StoneKind
import app.pbbls.android.core.model.achievements.SubjectKind
import app.pbbls.android.core.model.achievements.TwentyFive
import app.pbbls.android.features.achievements.AchievementLookups
import java.time.Instant
import java.time.LocalDate

/**
 * The achievements board's page (#1307) as the canonical design fixture serves it
 * (`packages/supabase/scripts/oracles/achievements/design/design-fixture.json`, shared with iOS),
 * already mapped: `AchievementsFixtureTest` decodes that file and asserts it maps to [page]. The
 * emotions, their families and palettes are the real reference rows (`reference/emotions.json`,
 * `reference/emotion-categories.json`); the domains are `design/domains.json`'s. Data only: change
 * the JSON first, then this, never the other way round.
 */
internal object AchievementsFixture {
    /** `reference/emotion-categories.json`: slug, name and palette. */
    private val categories: Map<String, EmotionFamily> =
        listOf(
            family("anger", "Anger", "#8E4242FF", "#C17575FF", "#F4ECECFF", "#8E42421A", "#1C0D0DFF", "#632E2EFF"),
            family("fear", "Fear", "#7B5E99FF", "#AE91CCFF", "#F2EFF5FF", "#7B5E991A", "#19131FFF", "#56426BFF"),
            family("joy", "Joy", "#A15C08FF", "#CF8C39FF", "#FAF6EAFF", "#A15C081A", "#201202FF", "#714006FF"),
            family("peace", "Peaceful", "#487C5AFF", "#80BF96FF", "#EDF2EEFF", "#487C5A1A", "#0E1912FF", "#32573FFF"),
            family("pride", "Pride", "#A9478AFF", "#EA91CEFF", "#F6EDF3FF", "#A9478A1A", "#220E1CFF", "#763261FF"),
            family("sadness", "Sadness", "#59658AFF", "#8C98BDFF", "#EEF0F3FF", "#59658A1A", "#12141CFF", "#3E4761FF"),
            family("shame", "Shame", "#868686FF", "#B9B9B9FF", "#F3F3F3FF", "#8686861A", "#1B1B1BFF", "#5E5E5EFF"),
        ).associateBy { it.slug }

    private val categoryIds: Map<String, String> =
        mapOf(
            "anger" to "f094a725-61aa-4488-8bc0-1c814159b536",
            "fear" to "1dada583-3ac8-4964-a976-76974eeac8fd",
            "joy" to "b9ff1de0-6846-4254-956e-7c7570f9b28b",
            "peace" to "eed68af7-491c-4736-a02d-ea2cd314e201",
            "pride" to "57b334d9-f1e7-4865-ab20-1ca6ce794a51",
            "sadness" to "528f2356-d960-444b-a437-ec4340f78e45",
            "shame" to "f5316645-9221-4597-9772-677245060877",
        )

    /** `reference/emotions.json`: id, slug, name, emoji and family. */
    private val emotions: Map<String, EmotionWithPalette> =
        listOf(
            emotion("50a68890-f6bb-e67a-6655-e84ff0b71f49", "amazed", "Awe", "🤩", "joy"),
            emotion("f14ca000-32c7-4cf3-9aa5-ad5dc84c5285", "amused", "Amused", "😂", "joy"),
            emotion("8a851a45-d1c5-92fd-fd50-098c4e86eaef", "angry", "Anger", "😡", "anger"),
            emotion("25d98464-bc3d-417f-ae12-a3a996e5dee1", "annoyed", "Annoyed", "😒", "anger"),
            emotion("b4216f65-f0a6-2a26-ca1a-9dfa839fbbeb", "anxious", "Anxiety", "😰", "fear"),
            emotion("0c98e05e-cf64-3b6f-929f-8bb6d0449b5f", "ashamed", "Shame", "🫣", "shame"),
            emotion("70e70f6a-237b-4e2d-8f4c-8043df889b36", "brave", "Brave", "🫡", "pride"),
            emotion("5ff5cd1c-6739-6143-537f-85a2dbbd8841", "calm", "Calm", "🙂", "peace"),
            emotion("3e14e406-90e8-41fd-a967-30fa545c47dc", "confident", "Confident", "😌", "pride"),
            emotion("b0230f83-c4e4-4cdc-bf9f-2d650509658c", "content", "Content", "😀", "peace"),
            emotion("f76b1274-04ab-4a9c-858d-4da26f8d124d", "disappointed", "Disappointed", "😕", "sadness"),
            emotion("8a79a265-5dcd-4d42-b8de-8c1ff5d7e505", "discouraged", "Discouraged", "😫", "sadness"),
            emotion("332cabe9-c76d-d737-bba6-f2d7024de808", "disgusted", "Disgust", "🤢", "anger"),
            emotion("ce828ce7-78ad-4a39-bfe6-0c843cfb2838", "drained", "Drained", "😪", "sadness"),
            emotion("52301a19-4b27-4432-9d0b-281f6341bb95", "embarrassed", "Embarrassed", "😬", "shame"),
            emotion("98bdc543-6b26-e1c8-da1d-50ae8b9973ff", "excited", "Excitement", "😇", "joy"),
            emotion("fab5fa1f-ad54-4705-a205-f8a0b2b31806", "frustrated", "Frustrated", "😤", "anger"),
            emotion("b5c925f9-ed7d-5b87-2dc2-d3857e736235", "grateful", "Gratitude", "🥰", "peace"),
            emotion("648b1092-820c-b19a-8a27-07072b068724", "guilty", "Guilt", "😓", "shame"),
            emotion("f1b09d24-3c2e-414b-a751-fc91da51fcdb", "happy", "Happy", "🤗", "joy"),
            emotion("b4102799-3d5f-45f2-bfa9-28d72fb89bec", "hopeful", "Hopeful", "🥹", "peace"),
            emotion("5d5e346a-c5c5-4fcb-99e9-2951213c3ff3", "hopeless", "Hopeless", "😞", "sadness"),
            emotion("59f68361-3443-4352-a119-b0daaf864bca", "indifferent", "Indifferent", "😑", "shame"),
            emotion("df630582-0846-4ebf-be75-166595513840", "irritated", "Irritated", "🙄", "anger"),
            emotion("c32faae1-46ac-411b-987f-e32e572259bb", "jealous", "Jealous", "😠", "anger"),
            emotion("3ffd6ca6-e033-6926-fa1f-ddf8f90bfd8c", "joyful", "Joy", "🥳", "joy"),
            emotion("f99d4e11-5a81-101b-07a3-6fc89a7aa1d9", "lonely", "Loneliness", "🥺", "sadness"),
            emotion("afd28421-b994-475c-ad49-75ecae570ee5", "overwhelmed", "Overwhelmed", "😣", "fear"),
            emotion("6b22ff42-67d4-ea3d-f35b-f71e49ae94cb", "passionate", "Passion", "😍", "joy"),
            emotion("21d79e4c-9482-40d3-bf73-53adfb452e5a", "peaceful", "Peaceful", "☺️", "peace"),
            emotion("ed9fbe9f-6a93-22bb-d5d5-35f42164c120", "proud", "Pride", "😎", "pride"),
            emotion("0312c804-7656-4f33-950c-a70a5b08b842", "relieved", "Relieved", "😮‍💨", "peace"),
            emotion("075ec40d-f27c-033d-a498-850947e4e887", "sad", "Sadness", "😢", "sadness"),
            emotion("776d210e-29f3-4f14-acb3-9787518aa53d", "satisfied", "Satisfied", "😊", "peace"),
            emotion("8b2993f3-4043-7bdf-388d-c0e3d5c96f02", "scared", "Fear", "😱", "fear"),
            emotion("93694d2b-41a7-4dfc-8582-7cce835ca9fe", "stressed", "Stressed", "😖", "fear"),
            emotion("a9b0625b-8a77-06cf-ba9c-a348ab58b12c", "surprised", "Surprise", "😯", "peace"),
            emotion("2d45e136-cee7-4bf1-b5fa-a3a79f26feab", "worried", "Worried", "😟", "fear"),
        ).associateBy { it.id }

    /** `design/domains.json`: the board's 18 domain ids, in slug order. */
    private val domains: Map<String, Domain> =
        listOf(
            Domain(id = "d0000000-0000-4000-8000-000000000001", slug = "community", name = "Community", label = ""),
            Domain(id = "d0000000-0000-4000-8000-000000000002", slug = "currentevents", name = "Events", label = ""),
            Domain(id = "d0000000-0000-4000-8000-000000000003", slug = "dating", name = "Dating", label = ""),
            Domain(id = "d0000000-0000-4000-8000-000000000004", slug = "education", name = "Education", label = ""),
            Domain(id = "d0000000-0000-4000-8000-000000000005", slug = "family", name = "Family", label = ""),
            Domain(id = "d0000000-0000-4000-8000-000000000006", slug = "fitness", name = "Sport", label = ""),
            Domain(id = "d0000000-0000-4000-8000-000000000007", slug = "friends", name = "Friends", label = ""),
            Domain(id = "d0000000-0000-4000-8000-000000000008", slug = "health", name = "Health", label = ""),
            Domain(id = "d0000000-0000-4000-8000-000000000009", slug = "hobbies", name = "Passions", label = ""),
            Domain(id = "d0000000-0000-4000-8000-00000000000a", slug = "identity", name = "Identity", label = ""),
            Domain(id = "d0000000-0000-4000-8000-00000000000b", slug = "money", name = "Finance", label = ""),
            Domain(id = "d0000000-0000-4000-8000-00000000000c", slug = "partner", name = "Partner", label = ""),
            Domain(id = "d0000000-0000-4000-8000-00000000000d", slug = "selfcare", name = "Self Care", label = ""),
            Domain(id = "d0000000-0000-4000-8000-00000000000e", slug = "spirituality", name = "Spirituality", label = ""),
            Domain(id = "d0000000-0000-4000-8000-00000000000f", slug = "tasks", name = "Tasks", label = ""),
            Domain(id = "d0000000-0000-4000-8000-000000000010", slug = "travel", name = "Travel", label = ""),
            Domain(id = "d0000000-0000-4000-8000-000000000011", slug = "weather", name = "Weather", label = ""),
            Domain(id = "d0000000-0000-4000-8000-000000000012", slug = "work", name = "Work", label = ""),
        ).associateBy { it.id }

    val lookups: AchievementLookups = AchievementLookups(categories::get, emotions::get, domains::get)

    private fun family(
        slug: String,
        name: String,
        primary: String,
        secondary: String,
        light: String,
        surface: String,
        dark: String,
        shaded: String,
    ) = EmotionFamily(slug, name, requireNotNull(EmotionPalette.fromHex(primary, secondary, light, surface, dark, shaded)) { slug })

    private fun emotion(
        id: String,
        slug: String,
        name: String,
        emoji: String,
        family: String,
    ): EmotionWithPalette {
        val category = categories.getValue(family)
        return EmotionWithPalette(id, slug, name, emoji, categoryIds.getValue(family), family, category.name, category.palette)
    }

    private fun day(raw: String): LocalDate = LocalDate.parse(raw)

    /** A snapshot as the mapping makes it: an out-of-range or missing valence reads as medium even. */
    private fun pebble(
        momentId: String?,
        name: String?,
        happenedAt: String?,
        emotionId: String?,
        positiveness: Int?,
        intensity: Int?,
    ) = PebbleSnapshot(momentId, name, happenedAt?.let(Instant::parse), emotionId, Valence.fromOrDefault(positiveness ?: 0, intensity ?: 2))

    private fun seat(
        seat: Int,
        emotionId: String,
        family: String?,
        met: Boolean,
        on: String?,
        pebble: PebbleSnapshot?,
        isNew: Boolean,
    ) = EmotionSeat(seat, emotionId, family, met, on?.let(::day), pebble, isNew)

    private val seats: List<EmotionSeat> =
        listOf(
            seat(
                0,
                "50a68890-f6bb-e67a-6655-e84ff0b71f49",
                "joy",
                true,
                "2026-07-18",
                pebble(
                    "a0000000-0000-4000-8000-0000000003f3",
                    "Night sky at Sarlat",
                    "2026-07-18T10:00:00Z",
                    "50a68890-f6bb-e67a-6655-e84ff0b71f49",
                    1,
                    3,
                ),
                false,
            ),
            seat(
                1,
                "f14ca000-32c7-4cf3-9aa5-ad5dc84c5285",
                "joy",
                true,
                "2026-04-19",
                pebble(
                    "a0000000-0000-4000-8000-0000000003ed",
                    "Bruno vs the hose",
                    "2026-04-19T10:00:00Z",
                    "f14ca000-32c7-4cf3-9aa5-ad5dc84c5285",
                    1,
                    1,
                ),
                false,
            ),
            seat(
                2,
                "98bdc543-6b26-e1c8-da1d-50ae8b9973ff",
                "joy",
                true,
                "2026-06-02",
                pebble(
                    "a0000000-0000-4000-8000-0000000003f0",
                    "Tickets to Lisbon",
                    "2026-06-02T10:00:00Z",
                    "98bdc543-6b26-e1c8-da1d-50ae8b9973ff",
                    1,
                    2,
                ),
                false,
            ),
            seat(
                3,
                "f1b09d24-3c2e-414b-a751-fc91da51fcdb",
                "joy",
                true,
                "2026-03-02",
                pebble(
                    "a0000000-0000-4000-8000-000000000001",
                    "Mara moves in",
                    "2026-03-02T10:00:00Z",
                    "f1b09d24-3c2e-414b-a751-fc91da51fcdb",
                    1,
                    2,
                ),
                false,
            ),
            seat(
                4,
                "3ffd6ca6-e033-6926-fa1f-ddf8f90bfd8c",
                "joy",
                true,
                "2026-03-21",
                pebble(
                    "a0000000-0000-4000-8000-0000000003ea",
                    "Spring at the lake",
                    "2026-03-21T10:00:00Z",
                    "3ffd6ca6-e033-6926-fa1f-ddf8f90bfd8c",
                    1,
                    2,
                ),
                false,
            ),
            seat(5, "6b22ff42-67d4-ea3d-f35b-f71e49ae94cb", "joy", false, null, null, false),
            seat(
                6,
                "70e70f6a-237b-4e2d-8f4c-8043df889b36",
                "pride",
                true,
                "2026-08-22",
                pebble(
                    "a0000000-0000-4000-8000-0000000003f5",
                    "Told dad",
                    "2026-08-22T10:00:00Z",
                    "70e70f6a-237b-4e2d-8f4c-8043df889b36",
                    0,
                    3,
                ),
                false,
            ),
            seat(
                7,
                "3e14e406-90e8-41fd-a967-30fa545c47dc",
                "pride",
                true,
                "2026-06-14",
                pebble(
                    "a0000000-0000-4000-8000-0000000003f1",
                    "First talk on stage",
                    "2026-06-14T10:00:00Z",
                    "3e14e406-90e8-41fd-a967-30fa545c47dc",
                    1,
                    3,
                ),
                false,
            ),
            seat(
                8,
                "ed9fbe9f-6a93-22bb-d5d5-35f42164c120",
                "pride",
                true,
                "2026-03-28",
                pebble(
                    "a0000000-0000-4000-8000-0000000003eb",
                    "Shipped the bakery site",
                    "2026-03-28T10:00:00Z",
                    "ed9fbe9f-6a93-22bb-d5d5-35f42164c120",
                    1,
                    2,
                ),
                false,
            ),
            seat(9, "8a851a45-d1c5-92fd-fd50-098c4e86eaef", "anger", false, null, null, false),
            seat(
                10,
                "25d98464-bc3d-417f-ae12-a3a996e5dee1",
                "anger",
                true,
                "2026-03-12",
                pebble(
                    "a0000000-0000-4000-8000-000000000006",
                    "Late train, again",
                    "2026-03-12T10:00:00Z",
                    "25d98464-bc3d-417f-ae12-a3a996e5dee1",
                    -1,
                    1,
                ),
                false,
            ),
            seat(11, "332cabe9-c76d-d737-bba6-f2d7024de808", "anger", false, null, null, false),
            seat(
                12,
                "fab5fa1f-ad54-4705-a205-f8a0b2b31806",
                "anger",
                true,
                "2026-05-15",
                pebble(
                    "a0000000-0000-4000-8000-000000000021",
                    "Rent went up",
                    "2026-05-15T10:00:00Z",
                    "fab5fa1f-ad54-4705-a205-f8a0b2b31806",
                    -1,
                    2,
                ),
                false,
            ),
            seat(
                13,
                "df630582-0846-4ebf-be75-166595513840",
                "anger",
                true,
                "2026-09-17",
                pebble(
                    "a0000000-0000-4000-8000-0000000003f7",
                    "Neighbour’s drill",
                    "2026-09-17T10:00:00Z",
                    "df630582-0846-4ebf-be75-166595513840",
                    -1,
                    1,
                ),
                false,
            ),
            seat(14, "c32faae1-46ac-411b-987f-e32e572259bb", "anger", false, null, null, false),
            seat(
                15,
                "b4216f65-f0a6-2a26-ca1a-9dfa839fbbeb",
                "fear",
                true,
                "2026-04-03",
                pebble(
                    "a0000000-0000-4000-8000-000000000011",
                    "Waiting for the scan",
                    "2026-04-03T10:00:00Z",
                    "b4216f65-f0a6-2a26-ca1a-9dfa839fbbeb",
                    -1,
                    3,
                ),
                false,
            ),
            seat(16, "afd28421-b994-475c-ad49-75ecae570ee5", "fear", false, null, null, false),
            seat(17, "8b2993f3-4043-7bdf-388d-c0e3d5c96f02", "fear", false, null, null, false),
            seat(18, "93694d2b-41a7-4dfc-8582-7cce835ca9fe", "fear", false, null, null, false),
            seat(19, "2d45e136-cee7-4bf1-b5fa-a3a79f26feab", "fear", false, null, null, false),
            seat(20, "0c98e05e-cf64-3b6f-929f-8bb6d0449b5f", "shame", false, null, null, false),
            seat(
                21,
                "52301a19-4b27-4432-9d0b-281f6341bb95",
                "shame",
                true,
                "2026-10-01",
                pebble(
                    "a0000000-0000-4000-8000-000000000077",
                    "Wrong name at the party",
                    "2026-10-01T10:00:00Z",
                    "52301a19-4b27-4432-9d0b-281f6341bb95",
                    -1,
                    1,
                ),
                false,
            ),
            seat(22, "648b1092-820c-b19a-8a27-07072b068724", "shame", false, null, null, false),
            seat(23, "59f68361-3443-4352-a119-b0daaf864bca", "shame", false, null, null, false),
            seat(
                24,
                "f76b1274-04ab-4a9c-858d-4da26f8d124d",
                "sadness",
                true,
                "2026-06-30",
                pebble(
                    "a0000000-0000-4000-8000-0000000003f2",
                    "Lisbon cancelled",
                    "2026-06-30T10:00:00Z",
                    "f76b1274-04ab-4a9c-858d-4da26f8d124d",
                    -1,
                    2,
                ),
                false,
            ),
            seat(25, "8a79a265-5dcd-4d42-b8de-8c1ff5d7e505", "sadness", false, null, null, false),
            seat(
                26,
                "ce828ce7-78ad-4a39-bfe6-0c843cfb2838",
                "sadness",
                true,
                "2026-04-27",
                pebble(
                    "a0000000-0000-4000-8000-0000000003ee",
                    "Night shift week",
                    "2026-04-27T10:00:00Z",
                    "ce828ce7-78ad-4a39-bfe6-0c843cfb2838",
                    -1,
                    2,
                ),
                false,
            ),
            seat(27, "5d5e346a-c5c5-4fcb-99e9-2951213c3ff3", "sadness", false, null, null, false),
            seat(
                28,
                "f99d4e11-5a81-101b-07a3-6fc89a7aa1d9",
                "sadness",
                true,
                "2026-08-09",
                pebble(
                    "a0000000-0000-4000-8000-0000000003f4",
                    "Everyone away",
                    "2026-08-09T10:00:00Z",
                    "f99d4e11-5a81-101b-07a3-6fc89a7aa1d9",
                    -1,
                    2,
                ),
                false,
            ),
            seat(
                29,
                "075ec40d-f27c-033d-a498-850947e4e887",
                "sadness",
                true,
                "2026-10-06",
                pebble(
                    "a0000000-0000-4000-8000-0000000003f8",
                    "Grandma’s call went quiet",
                    "2026-10-06T10:00:00Z",
                    "075ec40d-f27c-033d-a498-850947e4e887",
                    -1,
                    3,
                ),
                true,
            ),
            seat(
                30,
                "5ff5cd1c-6739-6143-537f-85a2dbbd8841",
                "peace",
                true,
                "2026-03-04",
                pebble(
                    "a0000000-0000-4000-8000-0000000003e9",
                    "Sunday market",
                    "2026-03-04T10:00:00Z",
                    "5ff5cd1c-6739-6143-537f-85a2dbbd8841",
                    0,
                    1,
                ),
                false,
            ),
            seat(
                31,
                "b0230f83-c4e4-4cdc-bf9f-2d650509658c",
                "peace",
                true,
                "2026-04-11",
                pebble(
                    "a0000000-0000-4000-8000-0000000003ec",
                    "Bread that rose",
                    "2026-04-11T10:00:00Z",
                    "b0230f83-c4e4-4cdc-bf9f-2d650509658c",
                    1,
                    1,
                ),
                false,
            ),
            seat(
                32,
                "b5c925f9-ed7d-5b87-2dc2-d3857e736235",
                "peace",
                true,
                "2026-03-09",
                pebble(
                    "a0000000-0000-4000-8000-000000000005",
                    "Elio’s first word",
                    "2026-03-09T10:00:00Z",
                    "b5c925f9-ed7d-5b87-2dc2-d3857e736235",
                    1,
                    3,
                ),
                false,
            ),
            seat(
                33,
                "b4102799-3d5f-45f2-bfa9-28d72fb89bec",
                "peace",
                true,
                "2026-05-06",
                pebble(
                    "a0000000-0000-4000-8000-0000000003ef",
                    "The offer letter",
                    "2026-05-06T10:00:00Z",
                    "b4102799-3d5f-45f2-bfa9-28d72fb89bec",
                    1,
                    2,
                ),
                false,
            ),
            seat(
                34,
                "21d79e4c-9482-40d3-bf73-53adfb452e5a",
                "peace",
                true,
                "2026-10-31",
                pebble(
                    "a0000000-0000-4000-8000-0000000003f9",
                    "Coffee by the fountain",
                    "2026-10-31T10:00:00Z",
                    "21d79e4c-9482-40d3-bf73-53adfb452e5a",
                    0,
                    2,
                ),
                true,
            ),
            seat(
                35,
                "0312c804-7656-4f33-950c-a70a5b08b842",
                "peace",
                true,
                "2026-09-03",
                pebble(
                    "a0000000-0000-4000-8000-0000000003f6",
                    "The scan was clear",
                    "2026-09-03T10:00:00Z",
                    "0312c804-7656-4f33-950c-a70a5b08b842",
                    1,
                    3,
                ),
                false,
            ),
            seat(36, "776d210e-29f3-4f14-acb3-9787518aa53d", "peace", false, null, null, false),
            seat(37, "a9b0625b-8a77-06cf-ba9c-a348ab58b12c", "peace", false, null, null, false),
        )

    private val bySeat = seats.associateBy { it.seat }

    private fun group(
        family: String?,
        met: List<Int>,
        notYet: List<Int>,
    ) = EmotionsFamilyGroup(family, met.map(bySeat::getValue), notYet.map(bySeat::getValue))

    private val explorer =
        ExplorerSection(
            firstPebble =
                pebble(
                    "a0000000-0000-4000-8000-000000000001",
                    "Mara moves in",
                    "2026-03-02T10:00:00Z",
                    "f1b09d24-3c2e-414b-a751-fc91da51fcdb",
                    1,
                    2,
                ),
            firstFive =
                listOf(
                    FirstFiveItem(
                        FirstStep.MOMENT,
                        done = true,
                        on = day("2026-03-01"),
                        subject = FirstSubject(SubjectKind.MOMENT, "a0000000-0000-4000-8000-000000000000", null),
                        isNew = false,
                    ),
                    FirstFiveItem(
                        FirstStep.PEBBLE,
                        done = true,
                        on = day("2026-03-02"),
                        subject = FirstSubject(SubjectKind.PEBBLE, "a0000000-0000-4000-8000-000000000001", "Mara moves in"),
                        isNew = false,
                    ),
                    FirstFiveItem(
                        FirstStep.COLLECTION,
                        done = true,
                        on = day("2026-04-10"),
                        subject = FirstSubject(SubjectKind.COLLECTION, "c0000000-0000-4000-8000-000000000001", "Elio, year one"),
                        isNew = false,
                    ),
                    FirstFiveItem(
                        FirstStep.SOUL,
                        done = true,
                        on = day("2026-03-02"),
                        subject = FirstSubject(SubjectKind.SOUL, "50000000-0000-4000-8000-000000000001", "Mara"),
                        isNew = false,
                    ),
                    FirstFiveItem(
                        FirstStep.GLYPH,
                        done = true,
                        on = day("2026-05-14"),
                        subject = FirstSubject(SubjectKind.GLYPH, "90000000-0000-4000-8000-000000000001", "carved for Bruno"),
                        isNew = false,
                    ),
                ),
            sixFirsts =
                listOf(
                    SixFirst(
                        StoneKind.SMALL,
                        done = true,
                        on = day("2026-03-02"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-000000000003",
                                "A slow morning",
                                "2026-03-02T10:00:00Z",
                                "5ff5cd1c-6739-6143-537f-85a2dbbd8841",
                                0,
                                1,
                            ),
                        isNew = false,
                    ),
                    SixFirst(
                        StoneKind.MEDIUM,
                        done = true,
                        on = day("2026-03-02"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-000000000001",
                                "Mara moves in",
                                "2026-03-02T10:00:00Z",
                                "f1b09d24-3c2e-414b-a751-fc91da51fcdb",
                                1,
                                2,
                            ),
                        isNew = false,
                    ),
                    SixFirst(
                        StoneKind.LARGE,
                        done = true,
                        on = day("2026-03-02"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-000000000002",
                                "Argument about money",
                                "2026-03-02T10:00:00Z",
                                "21d79e4c-9482-40d3-bf73-53adfb452e5a",
                                1,
                                3,
                            ),
                        isNew = false,
                    ),
                    SixFirst(
                        StoneKind.LOWLIGHT,
                        done = true,
                        on = day("2026-03-08"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-000000000004",
                                "Gym, finally",
                                "2026-03-08T10:00:00Z",
                                "5d5e346a-c5c5-4fcb-99e9-2951213c3ff3",
                                -1,
                                2,
                            ),
                        isNew = false,
                    ),
                    SixFirst(
                        StoneKind.NEUTRAL,
                        done = true,
                        on = day("2026-03-02"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-000000000003",
                                "A slow morning",
                                "2026-03-02T10:00:00Z",
                                "5ff5cd1c-6739-6143-537f-85a2dbbd8841",
                                0,
                                1,
                            ),
                        isNew = false,
                    ),
                    SixFirst(
                        StoneKind.HIGHLIGHT,
                        done = true,
                        on = day("2026-03-02"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-000000000001",
                                "Mara moves in",
                                "2026-03-02T10:00:00Z",
                                "f1b09d24-3c2e-414b-a751-fc91da51fcdb",
                                1,
                                2,
                            ),
                        isNew = false,
                    ),
                ),
            twentyFive =
                TwentyFive(
                    25,
                    done = true,
                    on = day("2026-04-21"),
                    pebble =
                        pebble(
                            "a0000000-0000-4000-8000-000000000019",
                            "Elio climbs the stairs",
                            "2026-04-21T10:00:00Z",
                            "50a68890-f6bb-e67a-6655-e84ff0b71f49",
                            1,
                            1,
                        ),
                    isNew = false,
                ),
            ringsDone = setOf(ExplorerRing.FIRST_FIVE, ExplorerRing.SIX_FIRSTS, ExplorerRing.TWENTY_FIVE),
            completion =
                Completion(
                    completedAt = Instant.parse("2026-03-08T18:00:00Z"),
                    offeredFrom = Instant.parse("2026-03-09T05:00:00Z"),
                    ready = false,
                    pulledAt = Instant.parse("2026-03-09T07:30:00Z"),
                    pebble =
                        pebble(
                            "a0000000-0000-4000-8000-000000000004",
                            "Gym, finally",
                            "2026-03-08T10:00:00Z",
                            "5d5e346a-c5c5-4fcb-99e9-2951213c3ff3",
                            -1,
                            2,
                        ),
                ),
        )

    private val emotionsSection =
        EmotionsSection(
            count = 23,
            of = 38,
            seats = seats,
            families =
                listOf(
                    group("joy", met = listOf(3, 4, 1, 2, 0), notYet = listOf(5)),
                    group("pride", met = listOf(8, 7, 6), notYet = listOf()),
                    group("anger", met = listOf(10, 12, 13), notYet = listOf(9, 11, 14)),
                    group("fear", met = listOf(15), notYet = listOf(16, 17, 18, 19)),
                    group("shame", met = listOf(21), notYet = listOf(20, 22, 23)),
                    group("sadness", met = listOf(26, 24, 28, 29), notYet = listOf(25, 27)),
                    group("peace", met = listOf(30, 32, 31, 33, 35, 34), notYet = listOf(36, 37)),
                ),
            since = day("2026-03-02"),
            completion = null,
        )

    private val domainsSection =
        DomainsSection(
            count = 14,
            of = 18,
            coverage = Coverage.MOST,
            entered =
                listOf(
                    DomainEntry(
                        "d0000000-0000-4000-8000-000000000002",
                        on = day("2026-10-01"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-0000000003fc",
                                "Flag march with Nadia",
                                "2026-10-01T10:00:00Z",
                                null,
                                0,
                                2,
                            ),
                        isNew = true,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-000000000001",
                        on = day("2026-09-12"),
                        pebble = pebble("a0000000-0000-4000-8000-0000000003fb", "Street dinner", "2026-09-12T10:00:00Z", null, 0, 2),
                        isNew = false,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-000000000011",
                        on = day("2026-07-18"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-0000000003f3",
                                "Night sky at Sarlat",
                                "2026-07-18T10:00:00Z",
                                "50a68890-f6bb-e67a-6655-e84ff0b71f49",
                                1,
                                3,
                            ),
                        isNew = false,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-00000000000d",
                        on = day("2026-07-04"),
                        pebble = pebble("a0000000-0000-4000-8000-0000000003fa", "A morning off", "2026-07-04T10:00:00Z", null, 0, 2),
                        isNew = false,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-00000000000a",
                        on = day("2026-06-14"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-0000000003f1",
                                "First talk on stage",
                                "2026-06-14T10:00:00Z",
                                "3e14e406-90e8-41fd-a967-30fa545c47dc",
                                1,
                                3,
                            ),
                        isNew = false,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-000000000010",
                        on = day("2026-06-02"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-0000000003f0",
                                "Tickets to Lisbon",
                                "2026-06-02T10:00:00Z",
                                "98bdc543-6b26-e1c8-da1d-50ae8b9973ff",
                                1,
                                2,
                            ),
                        isNew = false,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-00000000000b",
                        on = day("2026-05-15"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-000000000021",
                                "Rent went up",
                                "2026-05-15T10:00:00Z",
                                "fab5fa1f-ad54-4705-a205-f8a0b2b31806",
                                -1,
                                2,
                            ),
                        isNew = false,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-000000000007",
                        on = day("2026-04-19"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-0000000003ed",
                                "Bruno vs the hose",
                                "2026-04-19T10:00:00Z",
                                "f14ca000-32c7-4cf3-9aa5-ad5dc84c5285",
                                1,
                                1,
                            ),
                        isNew = false,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-000000000008",
                        on = day("2026-04-03"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-000000000011",
                                "Waiting for the scan",
                                "2026-04-03T10:00:00Z",
                                "b4216f65-f0a6-2a26-ca1a-9dfa839fbbeb",
                                -1,
                                3,
                            ),
                        isNew = false,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-000000000012",
                        on = day("2026-03-28"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-0000000003eb",
                                "Shipped the bakery site",
                                "2026-03-28T10:00:00Z",
                                "ed9fbe9f-6a93-22bb-d5d5-35f42164c120",
                                1,
                                2,
                            ),
                        isNew = false,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-00000000000f",
                        on = day("2026-03-12"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-000000000006",
                                "Late train, again",
                                "2026-03-12T10:00:00Z",
                                "25d98464-bc3d-417f-ae12-a3a996e5dee1",
                                -1,
                                1,
                            ),
                        isNew = false,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-000000000009",
                        on = day("2026-03-04"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-0000000003e9",
                                "Sunday market",
                                "2026-03-04T10:00:00Z",
                                "5ff5cd1c-6739-6143-537f-85a2dbbd8841",
                                0,
                                1,
                            ),
                        isNew = false,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-000000000005",
                        on = day("2026-03-02"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-000000000001",
                                "Mara moves in",
                                "2026-03-02T10:00:00Z",
                                "f1b09d24-3c2e-414b-a751-fc91da51fcdb",
                                1,
                                2,
                            ),
                        isNew = false,
                    ),
                    DomainEntry(
                        "d0000000-0000-4000-8000-00000000000c",
                        on = day("2026-03-02"),
                        pebble =
                            pebble(
                                "a0000000-0000-4000-8000-000000000001",
                                "Mara moves in",
                                "2026-03-02T10:00:00Z",
                                "f1b09d24-3c2e-414b-a751-fc91da51fcdb",
                                1,
                                2,
                            ),
                        isNew = false,
                    ),
                ),
            notYet =
                listOf(
                    "d0000000-0000-4000-8000-000000000003",
                    "d0000000-0000-4000-8000-000000000004",
                    "d0000000-0000-4000-8000-000000000006",
                    "d0000000-0000-4000-8000-00000000000e",
                ),
            completion = null,
        )

    private val pebbles =
        CountingSection(
            count = 137,
            levels =
                listOf(
                    StackLevel(
                        1,
                        day("2026-03-02"),
                        pebble(
                            "a0000000-0000-4000-8000-000000000001",
                            "Mara moves in",
                            "2026-03-02T10:00:00Z",
                            "f1b09d24-3c2e-414b-a751-fc91da51fcdb",
                            1,
                            2,
                        ),
                        false,
                    ),
                    StackLevel(
                        10,
                        day("2026-03-19"),
                        pebble(
                            "a0000000-0000-4000-8000-00000000000a",
                            "Choir, first rehearsal",
                            "2026-03-19T10:00:00Z",
                            "70e70f6a-237b-4e2d-8f4c-8043df889b36",
                            1,
                            2,
                        ),
                        false,
                    ),
                    StackLevel(
                        25,
                        day("2026-04-21"),
                        pebble(
                            "a0000000-0000-4000-8000-000000000019",
                            "Elio climbs the stairs",
                            "2026-04-21T10:00:00Z",
                            "50a68890-f6bb-e67a-6655-e84ff0b71f49",
                            1,
                            1,
                        ),
                        false,
                    ),
                    StackLevel(
                        50,
                        day("2026-06-08"),
                        pebble(
                            "a0000000-0000-4000-8000-000000000032",
                            "Rooftop with Jules",
                            "2026-06-08T10:00:00Z",
                            "70e70f6a-237b-4e2d-8f4c-8043df889b36",
                            1,
                            1,
                        ),
                        false,
                    ),
                    StackLevel(
                        100,
                        day("2026-08-30"),
                        pebble(
                            "a0000000-0000-4000-8000-000000000064",
                            "Last swim of summer",
                            "2026-08-30T10:00:00Z",
                            "ed9fbe9f-6a93-22bb-d5d5-35f42164c120",
                            1,
                            3,
                        ),
                        false,
                    ),
                ),
            next = NextLevel(250, 113, 0.247f),
            ladder = listOf(1, 10, 25, 50, 100, 250, 500, 1000),
            thenEvery = 500,
            since = day("2026-03-02"),
        )

    private val glyphsCarved =
        CountingSection(
            count = 12,
            levels =
                listOf(
                    StackLevel(10, day("2026-09-21"), null, false),
                ),
            next = NextLevel(25, 13, 0.133f),
            ladder = listOf(10, 25, 50, 75, 100),
            thenEvery = null,
            since = day("2026-09-21"),
        )

    private val glyphSales =
        CountingSection(
            count = 3,
            levels =
                listOf(
                    StackLevel(1, day("2026-10-02"), null, false),
                ),
            next = NextLevel(5, 2, 0.5f),
            ladder = listOf(1, 5, 10, 25, 50),
            thenEvery = null,
            since = day("2026-10-02"),
        )

    private val years =
        listOf(
            AchievementYear(
                year = 2026,
                current = true,
                daysTarget = 14,
                months =
                    listOf(
                        AchievementMonth(1, MonthState.QUIET, null, 0, 0, 3, 0, 0, 0.0f, MonthDone(false, false, false)),
                        AchievementMonth(2, MonthState.QUIET, null, 0, 0, 3, 0, 0, 0.0f, MonthDone(false, false, false)),
                        AchievementMonth(3, MonthState.KEPT, "peace", 17, 12, 4, 4, 1, 0.857f, MonthDone(true, false, true)),
                        AchievementMonth(4, MonthState.KEPT, "joy", 14, 11, 3, 3, 1, 0.786f, MonthDone(true, false, true)),
                        AchievementMonth(5, MonthState.KEPT, "joy", 16, 13, 4, 4, 1, 0.929f, MonthDone(true, false, true)),
                        AchievementMonth(6, MonthState.KEPT, "joy", 14, 12, 4, 4, 1, 0.857f, MonthDone(true, false, true)),
                        AchievementMonth(7, MonthState.KEPT, "joy", 14, 12, 3, 3, 1, 0.857f, MonthDone(true, false, true)),
                        AchievementMonth(8, MonthState.KEPT, "joy", 19, 17, 4, 4, 2, 0.0f, MonthDone(true, true, true)),
                        AchievementMonth(9, MonthState.KEPT, "peace", 16, 13, 3, 3, 1, 0.929f, MonthDone(true, false, true)),
                        AchievementMonth(10, MonthState.KEPT, "joy", 15, 13, 3, 3, 1, 0.929f, MonthDone(true, false, true)),
                        AchievementMonth(11, MonthState.KEPT, "peace", 2, 2, 0, 0, 0, 0.143f, MonthDone(false, false, false)),
                        AchievementMonth(12, MonthState.UPCOMING, null, 0, 0, 0, 0, 0, 0.0f, MonthDone(false, false, false)),
                    ),
                listed = listOf(11, 10, 9, 8, 7, 6, 5, 4, 3),
                nextStep = NextStep(11, Needs.MOMENTS, 12, true),
            ),
        )

    val page: AchievementsPage =
        AchievementsPage(
            since = day("2026-03-01"),
            daysKept = 249,
            explorer = explorer,
            emotions = emotionsSection,
            domains = domainsSection,
            pebbles = pebbles,
            glyphsCarved = glyphsCarved,
            glyphSales = glyphSales,
            years = years,
            seen =
                mapOf(
                    AchievementCard.EXPLORER to Instant.parse("2026-10-02T19:00:00Z"),
                    AchievementCard.EMOTIONS to Instant.parse("2026-10-02T19:00:00Z"),
                    AchievementCard.DOMAINS to Instant.parse("2026-10-02T19:00:00Z"),
                    AchievementCard.PEBBLES to Instant.parse("2026-10-02T19:00:00Z"),
                ),
            shelf =
                listOf(
                    ShelfItem("glyph-sales-1", "glyph_sales", 1, day("2026-10-02")),
                    ShelfItem("glyph-count-10", "glyph_count", 10, day("2026-09-21")),
                    ShelfItem("pebble-count-100", "pebble_count", 100, day("2026-08-30")),
                    ShelfItem("pebble-count-50", "pebble_count", 50, day("2026-06-08")),
                    ShelfItem("blue-pebble", "first_five", null, day("2026-05-14")),
                    ShelfItem("first-glyph", "first_glyph", null, day("2026-05-14")),
                ),
            shelfCount = 13,
        )
}
