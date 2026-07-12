package app.pbbls.android.features.glyph.models

import kotlinx.serialization.Serializable

/**
 * One stroke within a carved glyph — mirrors iOS `GlyphStroke.swift` and the
 * web `MarkStroke` shape so glyphs are interoperable across surfaces.
 *
 * [d] is an SVG path string (`M x,y L x,y …`, or quadratic Béziers for
 * smoothed strokes). [width] is the stroke width in the glyph's 200×200
 * coordinate space.
 */
@Serializable
data class GlyphStroke(
    val d: String,
    val width: Double,
)
