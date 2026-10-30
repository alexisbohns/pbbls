package app.pbbls.android

import app.pbbls.android.core.model.Domain
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
 * The achievements board's page (#1307; `final/index.html`, `NOTES.md`) as the server would send
 * it, already mapped. Every value is written out as the server would serve it (the levels, the
 * done flags, the families' order): nothing here is derived. The families and their palettes are
 * `SpineFixtureWeek`'s. The 38 emotions' family split is a fixture's, not the live table's: the
 * previews need every family run, not the real membership. Data only.
 */
internal object AchievementsFixture {
    private val SPLIT: List<Pair<String, List<String>>> =
        listOf(
            "joy" to listOf("amazed", "amused", "excited", "grateful", "happy", "hopeful", "joyful", "passionate", "surprised"),
            "pride" to listOf("brave", "confident", "proud", "relieved", "satisfied"),
            "anger" to listOf("angry", "annoyed", "disgusted", "frustrated", "irritated", "jealous"),
            "fear" to listOf("anxious", "overwhelmed", "scared", "stressed", "worried"),
            "shame" to listOf("ashamed", "embarrassed", "guilty"),
            "sadness" to listOf("disappointed", "discouraged", "drained", "hopeless", "lonely", "sad"),
            "peace" to listOf("calm", "content", "indifferent", "peaceful"),
        )
    private val DOMAINS =
        listOf(
            "family",
            "friends",
            "partner",
            "work",
            "health",
            "hobbies",
            "travel",
            "money",
            "identity",
            "education",
            "selfcare",
            "fitness",
            "tasks",
            "currentevents",
            "community",
            "dating",
            "spirituality",
            "weather",
        )
    private const val MET_DOMAINS = 14

    private val emotions: Map<String, EmotionWithPalette> =
        SPLIT
            .flatMap { (family, slugs) ->
                slugs.map { slug ->
                    EmotionWithPalette(
                        id = "emotion-$slug",
                        slug = slug,
                        name = slug.replaceFirstChar { it.uppercase() },
                        emoji = "",
                        categoryId = "category-$family",
                        categorySlug = family,
                        categoryName = family.replaceFirstChar { it.uppercase() },
                        palette = SpineFixtureWeek.families.getValue(family).palette,
                    )
                }
            }.associateBy { it.id }

    private val domains: Map<String, Domain> =
        DOMAINS.associate { slug -> "domain-$slug" to Domain(id = "domain-$slug", slug = slug, name = slug, label = "") }

    val lookups: AchievementLookups = AchievementLookups(SpineFixtureWeek.families::get, emotions::get, domains::get)

    private var nextMoment = 0

    private fun pebble(
        name: String,
        day: String,
        emotion: String,
        valence: Valence,
    ) = PebbleSnapshot("moment-${nextMoment++}", name, Instant.parse("${day}T18:00:00Z"), "emotion-$emotion", valence)

    private val first = pebble("Mara moves in", "2026-03-02", "joyful", Valence.HIGHLIGHT_MEDIUM)

    /** Which emotions were met, by seat: 23 of 38, every family touched. */
    private val metSlugs =
        setOf(
            "amazed",
            "excited",
            "grateful",
            "happy",
            "hopeful",
            "joyful",
            "surprised",
            "brave",
            "proud",
            "relieved",
            "angry",
            "frustrated",
            "anxious",
            "scared",
            "worried",
            "ashamed",
            "disappointed",
            "lonely",
            "sad",
            "calm",
            "content",
            "peaceful",
            "satisfied",
        )
    private val newSlugs = setOf("sad", "peaceful")
    private val heavy = setOf("sadness", "fear", "shame", "anger")

    private val seats: List<EmotionSeat> =
        SPLIT
            .flatMap { (family, slugs) -> slugs.map { it to family } }
            .mapIndexed { seat, (slug, family) ->
                val met = slug in metSlugs
                val day = "2026-0${3 + seat % 7}-1${seat % 9}"
                val valence = if (family in heavy) Valence.LOWLIGHT_MEDIUM else Valence.HIGHLIGHT_MEDIUM
                EmotionSeat(
                    seat = seat,
                    emotionId = "emotion-$slug",
                    family = family,
                    met = met,
                    on = if (met) LocalDate.parse(day) else null,
                    pebble = if (met) pebble("A $slug evening", day, slug, valence) else null,
                    isNew = slug in newSlugs,
                )
            }

    /** The sheet's order as the server would send it: families on the wheel, met seats first. */
    private val families: List<EmotionsFamilyGroup> =
        SPLIT.map { (family, _) ->
            val own = seats.filter { it.family == family }
            EmotionsFamilyGroup(family, met = own.filter { it.met }, notYet = own.filterNot { it.met })
        }

    private fun month(
        m: Int,
        state: MonthState,
        family: String?,
        moments: Int,
        pebbles: Int,
        fullWeeks: Int,
        filled: Int,
        level: Int,
        progress: Float,
        done: MonthDone,
    ) = AchievementMonth(m, state, family, moments, pebbles, fullWeeks, filled, level, progress, done)

    private val none = MonthDone(moments = false, pebbles = false, weeks = false)
    private val inner = MonthDone(moments = true, pebbles = false, weeks = true)
    private val rim = MonthDone(moments = true, pebbles = true, weeks = true)

    private val year2026 =
        AchievementYear(
            year = 2026,
            current = true,
            daysTarget = 14,
            months =
                listOf(
                    month(1, MonthState.QUIET, null, 0, 0, 3, 0, 0, 0f, none),
                    month(2, MonthState.QUIET, null, 0, 0, 4, 0, 0, 0f, none),
                    month(3, MonthState.KEPT, "joy", 16, 12, 4, 4, 1, 0.86f, inner),
                    month(4, MonthState.KEPT, "sadness", 15, 12, 3, 3, 1, 0.86f, inner),
                    month(5, MonthState.KEPT, "peace", 17, 13, 4, 4, 1, 0.93f, inner),
                    month(6, MonthState.KEPT, "joy", 14, 10, 4, 4, 1, 0.71f, inner),
                    month(7, MonthState.KEPT, "pride", 16, 11, 4, 4, 1, 0.79f, inner),
                    month(8, MonthState.KEPT, "joy", 16, 14, 4, 4, 2, 0f, rim),
                    month(9, MonthState.KEPT, "fear", 15, 9, 3, 3, 1, 0.64f, inner),
                    month(10, MonthState.KEPT, "peace", 18, 13, 4, 4, 1, 0.93f, inner),
                    month(11, MonthState.QUIET, null, 1, 0, 0, 0, 0, 0.07f, none),
                    month(12, MonthState.UPCOMING, null, 0, 0, 0, 0, 0, 0f, none),
                ),
            listed = (11 downTo 3).toList(),
            nextStep = NextStep(month = 11, needs = Needs.MOMENTS, daysLeft = 13, reachable = true),
        )

    private val pebbleLevels =
        listOf(1, 10, 25, 50, 100).mapIndexed { i, t ->
            StackLevel(
                t,
                LocalDate.of(2026, 3 + i, 2),
                pebble("Pebble number $t", "2026-0${3 + i}-02", "happy", Valence.HIGHLIGHT_SMALL),
                i == 4,
            )
        }

    val page: AchievementsPage =
        AchievementsPage(
            since = LocalDate.of(2026, 3, 1),
            daysKept = 249,
            explorer =
                ExplorerSection(
                    firstPebble = first,
                    firstFive =
                        FirstStep.entries.map { step ->
                            FirstFiveItem(
                                step,
                                done = true,
                                on = LocalDate.of(2026, 3, 2),
                                subject = FirstSubject(SubjectKind.MOMENT, first.momentId, step.slug),
                                isNew = false,
                            )
                        },
                    sixFirsts =
                        StoneKind.entries.map { kind ->
                            val day = "2026-03-0${3 + kind.ordinal}"
                            SixFirst(
                                kind,
                                done = true,
                                on = LocalDate.parse(day),
                                pebble = pebble("The first ${kind.slug} one", day, "calm", Valence.NEUTRAL_MEDIUM),
                                isNew = false,
                            )
                        },
                    twentyFive =
                        TwentyFive(
                            25,
                            done = true,
                            on = LocalDate.of(2026, 4, 11),
                            pebble = pebble("A walk by the canal", "2026-04-11", "content", Valence.NEUTRAL_SMALL),
                            isNew = false,
                        ),
                    ringsDone = setOf(ExplorerRing.FIRST_FIVE, ExplorerRing.SIX_FIRSTS, ExplorerRing.TWENTY_FIVE),
                    completion =
                        Completion(
                            completedAt = Instant.parse("2026-03-07T10:00:00Z"),
                            offeredFrom = Instant.parse("2026-03-08T05:00:00Z"),
                            ready = true,
                            pulledAt = null,
                            pebble = pebble("The first highlight one", "2026-03-07", "calm", Valence.HIGHLIGHT_MEDIUM),
                        ),
                ),
            emotions =
                EmotionsSection(
                    count = 23,
                    of = 38,
                    seats = seats,
                    families = families,
                    since = LocalDate.of(2026, 3, 2),
                    completion = null,
                ),
            domains =
                DomainsSection(
                    count = MET_DOMAINS,
                    of = DOMAINS.size,
                    coverage = Coverage.MOST,
                    entered =
                        DOMAINS.take(MET_DOMAINS).asReversed().mapIndexed { i, slug ->
                            val day = "2026-0${3 + i % 7}-2${i % 8}"
                            DomainEntry(
                                "domain-$slug",
                                on = LocalDate.parse(day),
                                pebble = pebble("At the $slug table", day, "happy", Valence.HIGHLIGHT_LARGE),
                                isNew = slug == "currentevents",
                            )
                        },
                    notYet = DOMAINS.drop(MET_DOMAINS).map { "domain-$it" },
                    completion = null,
                ),
            pebbles =
                CountingSection(
                    137,
                    pebbleLevels,
                    NextLevel(250, 113, 0.248f),
                    listOf(1, 10, 25, 50, 100, 250, 500, 1000),
                    thenEvery = 500,
                    since = LocalDate.of(2026, 3, 2),
                ),
            glyphsCarved =
                CountingSection(
                    12,
                    listOf(StackLevel(10, LocalDate.of(2026, 6, 1), null, false)),
                    NextLevel(25, 13, 0.133f),
                    listOf(10, 25, 50, 75, 100),
                    thenEvery = null,
                    since = LocalDate.of(2026, 6, 1),
                ),
            glyphSales =
                CountingSection(
                    3,
                    listOf(StackLevel(1, LocalDate.of(2026, 7, 9), null, false)),
                    NextLevel(5, 2, 0.5f),
                    listOf(1, 5, 10, 25, 50),
                    thenEvery = null,
                    since = LocalDate.of(2026, 7, 9),
                ),
            years = listOf(year2026),
            seen = mapOf(AchievementCard.EXPLORER to Instant.parse("2026-10-02T08:14:05Z")),
            shelf =
                listOf(
                    ShelfItem("pebble-count-100", "pebble_count", 100, LocalDate.of(2026, 7, 2)),
                    ShelfItem("first-soul", "first_soul", null, LocalDate.of(2026, 3, 9)),
                ),
            shelfCount = 9,
        )
}
