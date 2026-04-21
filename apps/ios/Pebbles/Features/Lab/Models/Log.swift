import Foundation

/// Mirrors the `public.logs` table (read via `v_logs_with_counts`).
/// Bilingual content lives in twin columns — `title_en` is required, `title_fr`
/// is optional and falls back to EN when the current locale is French and
/// no translation exists.
struct Log: Identifiable, Decodable, Hashable {
    let id: UUID
    let species: LogSpecies
    let platform: LogPlatform
    let status: LogStatus

    let titleEn: String
    let titleFr: String?
    let summaryEn: String
    let summaryFr: String?
    let bodyMdEn: String?
    let bodyMdFr: String?

    let coverImagePath: String?
    let externalUrl: String?

    let published: Bool
    let publishedAt: Date?
    let createdAt: Date

    let reactionCount: Int

    private enum CodingKeys: String, CodingKey {
        case id
        case species
        case platform
        case status
        case titleEn = "title_en"
        case titleFr = "title_fr"
        case summaryEn = "summary_en"
        case summaryFr = "summary_fr"
        case bodyMdEn = "body_md_en"
        case bodyMdFr = "body_md_fr"
        case coverImagePath = "cover_image_path"
        case externalUrl = "external_url"
        case published
        case publishedAt = "published_at"
        case createdAt = "created_at"
        case reactionCount = "reaction_count"
    }

    // MARK: - Localized accessors

    func title(for locale: Locale) -> String {
        locale.prefersFrench ? (titleFr ?? titleEn) : titleEn
    }

    func summary(for locale: Locale) -> String {
        locale.prefersFrench ? (summaryFr ?? summaryEn) : summaryEn
    }

    func body(for locale: Locale) -> String? {
        locale.prefersFrench ? (bodyMdFr ?? bodyMdEn) : bodyMdEn
    }

    /// Returns a copy with `reactionCount` shifted by `delta`, clamped at zero.
    /// Used by views doing optimistic reaction updates — everything else on
    /// the row stays identical, so list diffs don't thrash.
    func withAdjustedCount(by delta: Int) -> Log {
        Log(
            id: id,
            species: species,
            platform: platform,
            status: status,
            titleEn: titleEn,
            titleFr: titleFr,
            summaryEn: summaryEn,
            summaryFr: summaryFr,
            bodyMdEn: bodyMdEn,
            bodyMdFr: bodyMdFr,
            coverImagePath: coverImagePath,
            externalUrl: externalUrl,
            published: published,
            publishedAt: publishedAt,
            createdAt: createdAt,
            reactionCount: max(0, reactionCount + delta)
        )
    }
}

enum LogSpecies: String, Decodable {
    case announcement
    case feature
}

enum LogPlatform: String, Decodable {
    case web, ios, android, all
}

enum LogStatus: String, Decodable {
    case backlog
    case planned
    case inProgress = "in_progress"
    case shipped
}

private extension Locale {
    var prefersFrench: Bool {
        language.languageCode?.identifier == "fr"
    }
}
