import Foundation

/// The promise step's letter (#1510 D2), a `promise_letters` row. FR falls back to EN.
struct PromiseLetter: Decodable, Equatable, Sendable {
    let bodyEn: String
    let bodyFr: String?
    let signedByEn: String
    let signedByFr: String?

    enum CodingKeys: String, CodingKey {
        case bodyEn = "body_md_en", bodyFr = "body_md_fr"
        case signedByEn = "signed_by_en", signedByFr = "signed_by_fr"
    }

    func body(french: Bool) -> String { Self.pick(french: french, fr: bodyFr, en: bodyEn) }
    func signedBy(french: Bool) -> String { Self.pick(french: french, fr: signedByFr, en: signedByEn) }

    /// Plain paragraphs: the seeded letter uses no Markdown and the admin form says
    /// "one paragraph per blank line".
    func paragraphs(french: Bool) -> [String] {
        body(french: french).components(separatedBy: "\n\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    private static func pick(french: Bool, fr: String?, en: String) -> String {
        guard french, let fr, !fr.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return en }
        return fr
    }
}

/// Step 3's six tiles (D4), in grid order. The raw value is the wire value in
/// `profiles.onboarding_reasons`. Same icons as Android's `OnboardingReason`.
enum OnboardingReason: String, CaseIterable, Sendable {
    case noticeFeelings = "notice-feelings", keepPeople = "keep-people", hardSeason = "hard-season"
    case goodDays = "good-days", ownPatterns = "own-patterns", keepRecord = "keep-record"

    var label: LocalizedStringResource {
        switch self {
        case .noticeFeelings: return "Notice how I really feel"
        case .keepPeople: return "Keep the people who matter"
        case .hardSeason: return "Get through a hard season"
        case .goodDays: return "Remember the good days"
        case .ownPatterns: return "See my own patterns"
        case .keepRecord: return "Keep a record for later"
        }
    }

    var icon: PbblsIcon {
        switch self {
        case .noticeFeelings: return .heart
        case .keepPeople: return .people
        case .hardSeason: return .abstractDrizzle
        case .goodDays: return .sunshine
        case .ownPatterns: return .path
        case .keepRecord: return .case
        }
    }
}

/// Step 6's four paces (D11), in group order. The raw value is the wire value in `profiles.pace`.
enum OnboardingPace: String, CaseIterable, Sendable {
    case daily, fewAWeek = "few-a-week", whenItMatters = "when-it-matters", weeklyWrap = "weekly-wrap"

    var label: LocalizedStringResource {
        switch self {
        case .daily: return "Every day"
        case .fewAWeek: return "A few times a week"
        case .whenItMatters: return "Only when it matters"
        case .weeklyWrap: return "Wrapping the end of the week"
        }
    }

    var detail: LocalizedStringResource {
        switch self {
        case .daily: return "A small habit, evening or morning."
        case .fewAWeek: return "When there's something worth it."
        case .whenItMatters: return "No rhythm at all. Also fine."
        case .weeklyWrap: return "One look back, each Sunday."
        }
    }

    var icon: PbblsIcon {
        switch self {
        case .daily: return .sunshine
        case .fewAWeek: return .path
        case .whenItMatters: return .pebbleRound
        case .weeklyWrap: return .calendarDay
        }
    }
}

/// One first person (step 5). Always a glyph: `souls.glyph_id` is not null.
struct OnboardingSoulDraft: Equatable, Sendable {
    let name: String
    let glyphId: UUID
}

/// Everything the flow collected, as `complete_onboarding` takes it (D5). A skipped
/// step is nil and left out of the params, so the server sees its default; `souls` is
/// always sent, empty when step 5 was passed with "Later".
struct OnboardingAnswers: Equatable, Sendable {
    var displayName: String?
    var glyphId: UUID?
    var reasons: [OnboardingReason]?
    var pace: OnboardingPace?
    var souls: [OnboardingSoulDraft] = []

    var params: CompleteOnboardingParams { CompleteOnboardingParams(answers: self) }
}

struct CompleteOnboardingParams: Encodable, Sendable {
    let answers: OnboardingAnswers

    private enum CodingKeys: String, CodingKey {
        case displayName = "p_display_name", glyphId = "p_glyph_id", reasons = "p_reasons"
        case pace = "p_pace", souls = "p_souls"
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(answers.displayName, forKey: .displayName)
        try container.encodeIfPresent(answers.glyphId?.uuidString.lowercased(), forKey: .glyphId)
        try container.encodeIfPresent(answers.reasons?.map(\.rawValue), forKey: .reasons)
        try container.encodeIfPresent(answers.pace?.rawValue, forKey: .pace)
        try container.encode(
            answers.souls.map { SoulPayload(name: $0.name, glyphId: $0.glyphId.uuidString.lowercased()) },
            forKey: .souls
        )
    }
}

private struct SoulPayload: Encodable {
    let name: String
    let glyphId: String
    enum CodingKeys: String, CodingKey { case name, glyphId = "glyph_id" }
}
