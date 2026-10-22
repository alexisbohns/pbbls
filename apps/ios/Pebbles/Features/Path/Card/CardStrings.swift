import Foundation

/// The held card's own copy (#1146 §Strings). Dotted keys, as `domain.*.name`,
/// because "Moment" and "Your pebble" are already literal keys with other uses.
/// Reused copy (`Visibility.label` / `explanation`, `Valence.headline`, "Edit
/// moment", "Edit pebble", "Delete", "Make it a pebble", "Share", "Back",
/// "More") keeps its own keys. New pattern, named in the E1a PR.
enum CardStrings {
    static let turn = LocalizedStringResource(
        "card.turn", defaultValue: "Turn", comment: "Held card footer: small caps over where the turn leads")
    static let turnToPebble = LocalizedStringResource(
        "card.turn.toPebble", defaultValue: "Pebble", comment: "Held card footer: the turn leads to the pebble (verso)")
    static let turnToMoment = LocalizedStringResource(
        "card.turn.toMoment", defaultValue: "Moment", comment: "Held card footer: the turn leads to the moment (recto)")
    static let turnActionToPebble = LocalizedStringResource(
        "card.turn.action.toPebble", defaultValue: "Turn over to your pebble",
        comment: "VoiceOver: the held card's turn button on the recto")
    static let turnActionToMoment = LocalizedStringResource(
        "card.turn.action.toMoment", defaultValue: "Turn over to the moment",
        comment: "VoiceOver: the held card's turn button on the verso")
    static let versoHeading = LocalizedStringResource(
        "card.verso.heading", defaultValue: "Your pebble", comment: "VoiceOver: the verso's heading")
    static let domainsA11y = LocalizedStringResource(
        "card.domains.a11y", defaultValue: "Life domains", comment: "VoiceOver: before the verso's domain names")
    static let collectionsA11y = LocalizedStringResource(
        "card.collections.a11y", defaultValue: "Collections", comment: "VoiceOver: before the band's collection names")
    static let placeKicker = LocalizedStringResource(
        "bare.place.kicker", defaultValue: "Not a pebble yet", comment: "Bare moment: the kicker over its card's place")
    static let placeBody = LocalizedStringResource(
        "bare.place.body",
        defaultValue: """
        Its card prints here once you say what it meant: a feeling and its weight, \
        the parts of your life it touched, who was there.
        """,
        comment: "Bare moment: the sentence in its card's place")
}
