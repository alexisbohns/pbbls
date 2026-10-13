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
import app.pbbls.android.core.model.Pebble
import app.pbbls.android.core.model.PebbleCollection
import app.pbbls.android.core.model.PebbleDetail
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.SoulRef
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import java.time.OffsetDateTime

/**
 * Minimal, valid domain values for the Robolectric UI tests (#857). Each takes
 * only what a test asserts on; the rest is the least a screen needs to render.
 *
 * Pebbles default to NOW, not a fixed date: Path's pager opens on the current
 * week, and a pebble outside it is not on screen to tap.
 */
object UiFixtures {
    val joy = EmotionRef(id = "emotion-joy", slug = "joy", name = "Joy")

    fun pebble(
        id: String = "pebble-1",
        name: String = "Coffee with Mo",
        happenedAt: OffsetDateTime = OffsetDateTime.now(),
    ) = Pebble(
        id = id,
        name = name,
        happenedAt = happenedAt,
        createdAt = happenedAt,
        intensity = 2,
        positiveness = 1,
        emotion = joy,
    )

    fun pebbleDetail(
        id: String = "pebble-1",
        name: String = "Coffee with Mo",
    ) = PebbleDetail(
        id = id,
        name = name,
        happenedAt = OffsetDateTime.now(),
        intensity = 2,
        positiveness = 1,
        visibility = Visibility.PRIVATE,
        emotion = joy,
    )

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
