/// Feature gate for the petroglyph wobble experiment (issue #555).
///
/// Debug-only by construction: the spike evaluates the hand-carved look across
/// every pebble surface in dev builds, and compiles to a constant `false` in
/// Release so it can never ship by accident. Deleting the experiment means
/// removing this folder and reverting the six flag-gated call sites.
enum WobbleFlags {
    #if DEBUG
    static let isEnabled = true
    #else
    static let isEnabled = false
    #endif
}
