package app.pbbls.android.testing

import app.pbbls.android.core.model.Collection
import app.pbbls.android.core.model.CollectionMode
import app.pbbls.android.core.model.DomainRef
import app.pbbls.android.core.model.EmotionRef
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.Log
import app.pbbls.android.core.model.LogPlatform
import app.pbbls.android.core.model.LogSpecies
import app.pbbls.android.core.model.LogStatus
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.MomentPebble
import app.pbbls.android.core.model.MomentSnap
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.PathPebble
import app.pbbls.android.core.model.PebbleCollection
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.SoulRef
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.features.path.WeekRollBuilder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Minimal, valid domain values for the Robolectric UI tests (#857). Each takes
 * only what a test asserts on; the rest is the least a screen needs to render.
 *
 * The moment builders default to a fixed date for the decoder and ViewModel
 * tests; a Robolectric Path journey passes `happenedAt = UiFixtures.thisWeek()`,
 * because the pager opens on the current week and a moment outside it is not
 * on screen to tap.
 */
object UiFixtures {
    val joy = EmotionRef(id = "emotion-joy", slug = "joy", name = "Joy")

    fun glyph(
        id: String = "glyph-1",
        name: String? = "Spiral",
    ) = Glyph(
        id = id,
        name = name,
        strokes = listOf(GlyphStroke(d = "M10 10 L90 90", width = 6.0)),
        viewBox = "0 0 100 100",
    )

    fun soul(
        id: String = "soul-1",
        name: String = "Mo",
    ) = SoulWithGlyph(id = id, name = name, glyphId = "glyph-1", glyph = glyph())

    fun collection(
        id: String = "collection-1",
        name: String = "Summer",
    ) = Collection(id = id, name = name, mode = CollectionMode.STACK, pebbleCount = 0)

    /** A community glyph the user does not own — the tile a store tap opens as a detail. */
    fun communityGlyph(name: String = "Spiral") =
        GlyphGridItem(
            glyph = glyph(id = "glyph-community", name = name),
            price = 3,
            owned = false,
            createdAt = OffsetDateTime.now(),
            acquiredAt = null,
        )

    fun log(
        id: String = "00000000-0000-0000-0000-000000000001",
        species: LogSpecies = LogSpecies.ANNOUNCEMENT,
        title: String = "Pebbles on Android",
    ) = Log(
        id = id,
        species = species,
        platform = LogPlatform.ANDROID,
        status = LogStatus.SHIPPED,
        titleEn = title,
        summaryEn = "$title, in short.",
        published = true,
        publishedAt = OffsetDateTime.now(),
        createdAt = OffsetDateTime.now(),
        reactionCount = 0,
    )

    fun pebbleGlyph(
        id: String = "glyph-1",
        source: String = "domain",
    ) = PebbleGlyph(
        id = id,
        name = null,
        strokes = listOf(GlyphStroke(d = "M 20 100 Q 100 20 180 100", width = 6.0)),
        viewBox = "0 0 200 200",
        source = source,
    )

    /**
     * Monday of the current week at ten, in the device zone, [weeksBack] weeks ago: when a
     * Path journey wants a moment on the timeline. Not `OffsetDateTime.now()`: on the Spine
     * a moment sits under its day's seam, so a Sunday-night "now" lands after six still days
     * and below the fold of a phone-sized screen — which is what "now" is in UTC while it is
     * already Monday in Paris. The first day of the week is at the top in every zone.
     */
    fun thisWeek(weeksBack: Long = 0): OffsetDateTime =
        WeekRollBuilder
            .weekStart(LocalDate.now())
            .minusWeeks(weeksBack)
            .atTime(10, 0)
            .atZone(ZoneId.systemDefault())
            .toOffsetDateTime()

    fun pathMoment(
        id: String = "moment-1",
        name: String? = "Morning walk",
        happenedAt: OffsetDateTime = OffsetDateTime.parse("2026-10-12T06:40:00Z"),
        firstSnapPath: String? = null,
        pebble: PathPebble? = pathPebble(),
    ) = PathMoment(id, name, happenedAt, happenedAt, firstSnapPath, pebble)

    fun pathPebble(
        id: String = "pebble-1",
        valence: Valence = Valence.HIGHLIGHT_MEDIUM,
        visibility: Visibility = Visibility.SECRET,
        glyph: PebbleGlyph? = pebbleGlyph(),
    ) = PathPebble(id, valence.intensity, valence.positiveness, visibility, joy, glyph)

    fun momentDetail(
        id: String = "moment-1",
        name: String? = "Morning walk",
        description: String? = null,
        snaps: List<MomentSnap> = emptyList(),
        pebble: MomentPebble? = momentPebble(),
    ) = MomentDetail(
        id = id,
        name = name,
        description = description,
        happenedAt = OffsetDateTime.parse("2026-10-12T06:40:00Z"),
        createdAt = OffsetDateTime.parse("2026-10-12T07:00:00Z"),
        snaps = snaps,
        pebble = pebble,
    )

    fun momentPebble(
        id: String = "pebble-1",
        valence: Valence = Valence.HIGHLIGHT_MEDIUM,
        visibility: Visibility = Visibility.SECRET,
        souls: List<SoulRef> = emptyList(),
        collections: List<PebbleCollection> = emptyList(),
    ) = MomentPebble(
        id = id,
        intensity = valence.intensity,
        positiveness = valence.positiveness,
        visibility = visibility,
        emotion = joy,
        glyph = pebbleGlyph(),
        souls = souls,
        domains = listOf(DomainRef(id = "domain-1", slug = "health", name = "Health")),
        collections = collections,
    )
}
