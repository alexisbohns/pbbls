import Foundation

/// The People onboarding's words (spec §2, E3), one accessor per key so the
/// views name no key twice. All keys live in `Localizable.xcstrings`.
enum PeopleOnboardingCopy {
    static var kicker: String { String(localized: "people.empty.kicker") }
    static var line1: String { String(localized: "people.empty.line1") }
    static var line2: String { String(localized: "people.empty.line2") }
    static var line3: String { String(localized: "people.empty.line3") }
    static var record: String { String(localized: "people.empty.record") }
    static var question: String { String(localized: "people.empty.question") }
    static var latest: String { String(localized: "people.empty.latest") }
    static var placeholder1: String { String(localized: "people.empty.placeholder1") }
    static var placeholder2: String { String(localized: "people.empty.placeholder2") }
    static var fewTitle: String { String(localized: "people.few.title") }
    static var fewMutual: String { String(localized: "people.few.mutual") }
    static var notNow: String { String(localized: "people.few.notNow") }
    static var who: String { String(localized: "people.prompt.who") }
    static var whoHint: String { String(localized: "people.prompt.whoHint") }

    /// "Not asked: {name}. Nothing in it points outward."
    static func notAsked(_ name: String) -> String {
        String(format: String(localized: "people.empty.notAsked"), name)
    }
}
