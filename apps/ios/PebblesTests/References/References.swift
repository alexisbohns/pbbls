import Testing

/// Root of every reference-image suite, so CI can run them alone
/// (`-only-testing:PebblesTests/References`). Serialised: `ImageRenderer` work
/// runs on the main actor anyway, and one render at a time keeps Metal quiet.
@Suite(.serialized)
struct References {}
