package app.pbbls.android

import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.DomainRef
import app.pbbls.android.core.model.EmotionRef
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.MomentPebble
import app.pbbls.android.core.model.MomentSnap
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.PathPebble
import app.pbbls.android.core.model.PathSoul
import app.pbbls.android.core.model.PebbleCollection
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.SoulRef
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import java.time.OffsetDateTime

/**
 * The moment builders of the unit tests' `UiFixtures` (#1048), for previews:
 * screenshot sources cannot see `src/test`. Every value is fixed, so a render
 * never depends on the clock.
 */
internal object MomentPreviewFixtures {
    val joy = EmotionRef(id = "e1", slug = "joyful", name = "Joyful")

    private val fixedWhen: OffsetDateTime = OffsetDateTime.parse("2026-07-06T15:42:00+00:00")

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

    fun pathPebble(
        id: String = "pebble-1",
        valence: Valence = Valence.HIGHLIGHT_MEDIUM,
        visibility: Visibility = Visibility.SECRET,
        emotion: EmotionRef? = joy,
        glyph: PebbleGlyph? = pebbleGlyph(),
        souls: List<PathSoul> = emptyList(),
    ) = PathPebble(id, valence.intensity, valence.positiveness, visibility, emotion, glyph, souls)

    fun pathMoment(
        id: String = "moment-1",
        name: String? = "Morning walk",
        happenedAt: OffsetDateTime = fixedWhen,
        firstSnapPath: String? = null,
        pebble: PathPebble? = pathPebble(),
    ) = PathMoment(id, name, happenedAt, happenedAt, firstSnapPath, pebble)

    fun momentPebble(
        id: String = "pebble-1",
        valence: Valence = Valence.HIGHLIGHT_MEDIUM,
        visibility: Visibility = Visibility.SECRET,
        emotion: EmotionRef = joy,
        souls: List<SoulRef> = emptyList(),
        domains: List<DomainRef> = listOf(DomainRef(id = "d-work", slug = "work", name = "Work")),
        collections: List<PebbleCollection> = emptyList(),
    ) = MomentPebble(
        id = id,
        intensity = valence.intensity,
        positiveness = valence.positiveness,
        visibility = visibility,
        emotion = emotion,
        glyph = pebbleGlyph(),
        souls = souls,
        domains = domains,
        collections = collections,
    )

    fun momentDetail(
        id: String = "moment-1",
        name: String? = "Morning walk",
        description: String? = null,
        happenedAt: OffsetDateTime = fixedWhen,
        snaps: List<MomentSnap> = emptyList(),
        pebble: MomentPebble? = momentPebble(),
    ) = MomentDetail(
        id = id,
        name = name,
        description = description,
        happenedAt = happenedAt,
        createdAt = happenedAt,
        snaps = snaps,
        pebble = pebble,
    )
}

/**
 * A fixed [ReferenceDataServicing] for previews that read the ambient
 * reference data: the read page's souls take their glyphs from it (A-15),
 * and Edit pebble's pickers list from it. Nothing loads.
 */
internal class PreviewReferenceData(
    override val domains: List<Domain> = emptyList(),
    override val souls: List<SoulWithGlyph> = emptyList(),
    override val collections: List<PebbleCollection> = emptyList(),
) : ReferenceDataServicing {
    override val hasLoaded: Boolean = true

    override suspend fun load() = Unit

    override suspend fun refreshSouls() = Unit

    override suspend fun refreshCollections() = Unit

    override suspend fun createSoul(name: String): SoulWithGlyph? = null
}
