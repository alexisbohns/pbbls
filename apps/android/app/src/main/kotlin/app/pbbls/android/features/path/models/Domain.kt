package app.pbbls.android.features.path.models

import app.pbbls.android.features.glyph.models.GlyphStroke
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reference-data domain — mirrors iOS `Domain.swift`. Loaded by the reference
 * data service for the create/edit form's domain picker. Never render [name]
 * directly — resolve through `ReferenceStrings.referenceName` (slug-keyed,
 * falls back to this DB name).
 *
 * [strokes] and [viewBox] come from the `v_domains_with_glyph` view, which
 * flattens each domain's default glyph into the row. Both default to `null` so
 * a projection that omits them (or a domain with no default glyph) still
 * decodes — the record flow's domain step leaves the glyph slot empty rather
 * than substituting a placeholder mark, which would read as data.
 *
 * [label] is the English description straight from the column; render it
 * through `ReferenceStrings.domainLabel`, which is the localized layer over it.
 */
@Serializable
data class Domain(
    val id: String,
    val slug: String,
    val name: String,
    val label: String,
    val strokes: List<GlyphStroke>? = null,
    @SerialName("view_box")
    val viewBox: String? = null,
)
