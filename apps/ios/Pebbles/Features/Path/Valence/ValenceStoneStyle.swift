import SwiftUI

/// The headline word's fill for the picked valence.
///
/// Until #975 this type also drew the valence stones themselves (a wash, an
/// ink and a mesh per polarity). The picker draws lit `StoneView`s since, so
/// those members had no caller and were deleted with their hex tables (#1143,
/// iOS half): iOS carries no red valence stop. A later port of the mesh starts
/// from the retuned tables in
/// `docs/superpowers/specs/2026-10-20-ios-new-path-foundations-design.md` §B3.1,
/// never from git history.
enum ValenceStoneStyle {
    /// Since #974 the stones are lit brand-coloured stone (`StoneTones.starting`:
    /// dark, shaded and primary bodies), so the word takes the brand tone its
    /// stone wears. Lowlight stays the system foreground: at headline size the
    /// near-black `shaded` reads as disabled in light mode and vanishes in dark mode.
    static func headlineInk(for polarity: ValencePolarity) -> AnyShapeStyle {
        switch polarity {
        case .lowlight:  return AnyShapeStyle(Color.pebbles.onSurface)
        // The neutral stone's body is the mid `shaded` tone.
        case .neutral:   return AnyShapeStyle(Color.pebbles.onPrimaryContainer)
        case .highlight: return AnyShapeStyle(Color.pebbles.primary)
        }
    }
}
