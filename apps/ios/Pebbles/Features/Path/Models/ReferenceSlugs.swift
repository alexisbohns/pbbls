import Foundation

/// Compile-time mirror of the emotion and domain slugs seeded in
/// `20260411000000_reference_tables.sql`. When a new reference row is added
/// server-side, this list MUST be updated in the same change — otherwise the
/// coverage test in `LocalizationTests` fails.
enum ReferenceSlugs {
    static let emotions: [String] = [
        "joy", "sadness", "anger", "fear", "disgust", "surprise",
        "love", "pride", "shame", "guilt", "anxiety", "nostalgia",
        "gratitude", "serenity", "excitement", "awe"
    ]

    static let domains: [String] = [
        "zoe", "asphaleia", "philia", "time", "eudaimonia"
    ]
}
