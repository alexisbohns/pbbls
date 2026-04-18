import Foundation

/// One stroke within a carved glyph. Mirrors the web `MarkStroke` shape so
/// glyphs are interoperable between web and iOS.
///
/// - `d`: SVG path string ("M x,y L x,y …" or with quadratic Beziers for smoothed strokes).
/// - `width`: stroke width in the glyph's 200x200 coordinate space. iOS-carved
///   strokes always use 6 per the issue constraint (no user-facing slider).
struct GlyphStroke: Codable, Hashable {
    let d: String
    let width: Double
}
