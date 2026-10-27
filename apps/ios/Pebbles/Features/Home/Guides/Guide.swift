import Foundation
import os

/// One About Pebbles reading (the `guides` table, #1254). Read directly:
/// a single-table select of the published rows in sort order (spec D8).
/// FR falls back to EN field by field, like `Log`.
struct Guide: Decodable, Identifiable, Hashable {
    let id: UUID
    let slug: String
    let sortOrder: Int
    let illustration: GuideIllustration?
    let titleEn: String
    let titleFr: String?
    let summaryEn: String
    let summaryFr: String?
    let bodyMdEn: String
    let bodyMdFr: String?

    enum CodingKeys: String, CodingKey {
        case id, slug, illustration
        case sortOrder = "sort_order"
        case titleEn = "title_en", titleFr = "title_fr"
        case summaryEn = "summary_en", summaryFr = "summary_fr"
        case bodyMdEn = "body_md_en", bodyMdFr = "body_md_fr"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        slug = try c.decode(String.self, forKey: .slug)
        sortOrder = try c.decode(Int.self, forKey: .sortOrder)
        illustration = try c.decodeIfPresent(String.self, forKey: .illustration)
            .flatMap(GuideIllustration.init(rawValue:))
        titleEn = try c.decode(String.self, forKey: .titleEn)
        titleFr = try c.decodeIfPresent(String.self, forKey: .titleFr)
        summaryEn = try c.decode(String.self, forKey: .summaryEn)
        summaryFr = try c.decodeIfPresent(String.self, forKey: .summaryFr)
        bodyMdEn = try c.decode(String.self, forKey: .bodyMdEn)
        bodyMdFr = try c.decodeIfPresent(String.self, forKey: .bodyMdFr)
    }

    func title(for locale: Locale) -> String { locale.prefersFrench ? (titleFr ?? titleEn) : titleEn }
    func summary(for locale: Locale) -> String { locale.prefersFrench ? (summaryFr ?? summaryEn) : summaryEn }
    func body(for locale: Locale) -> String { locale.prefersFrench ? (bodyMdFr ?? bodyMdEn) : bodyMdEn }
}

extension Guide {
    /// For tests and fixtures.
    init(
        id: UUID = UUID(), slug: String, sortOrder: Int, illustration: GuideIllustration? = nil,
        titleEn: String, titleFr: String? = nil, summaryEn: String, summaryFr: String? = nil,
        bodyMdEn: String, bodyMdFr: String? = nil
    ) {
        self.id = id
        self.slug = slug
        self.sortOrder = sortOrder
        self.illustration = illustration
        self.titleEn = titleEn
        self.titleFr = titleFr
        self.summaryEn = summaryEn
        self.summaryFr = summaryFr
        self.bodyMdEn = bodyMdEn
        self.bodyMdFr = bodyMdFr
    }
}

/// The nine illustrations the clients draw from their own components
/// (`GuideIllustrationView`, Task 15); mirrors the column's CHECK.
enum GuideIllustration: String, Decodable, CaseIterable {
    case pebbles, stones, emotions, valence, domains, collections, souls, glyphs, karma
}

/// One bad row must not hide the About section: skipped rows log once each,
/// as `LossyLogArray` does.
struct LossyGuideArray: Decodable {
    let guides: [Guide]

    init(from decoder: Decoder) throws {
        var container = try decoder.unkeyedContainer()
        var accumulated: [Guide] = []
        if let count = container.count { accumulated.reserveCapacity(count) }
        var index = 0
        while !container.isAtEnd {
            do {
                accumulated.append(try container.decode(Guide.self))
            } catch {
                Self.logger.error(
                    "skipped guide row \(index, privacy: .public): \(String(reflecting: error), privacy: .public)"
                )
                // A throwing decode does not advance the container; swallow the element.
                _ = try? container.decode(SkippedElement.self)
            }
            index += 1
        }
        self.guides = accumulated
    }

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "home.guides")
}

private struct SkippedElement: Decodable {
    init(from decoder: Decoder) throws {
        _ = try decoder.singleValueContainer()
    }
}
