/// Feature gate for the petroglyph wobble experiment (issue #555).
///
/// On in every configuration since #727: the valence fan picker is built out
/// of the wobbled silhouettes, so a Release build that fell back to the smooth
/// `SVGView` path would ship a different picker than the one that was
/// designed. Android already bakes the wobble into its internal-testing
/// releases (decision log, 2026-07-14); this is iOS catching up.
///
/// Kept as a flag rather than deleted so the experiment can still be switched
/// off in one place. Deleting it means removing the Wobble folder and
/// reverting the flag-gated call sites.
enum WobbleFlags {
    static let isEnabled = true
}
