import Foundation
import os

/// One Settings row from `settings_links` (#1489): admin-edited, read directly
/// (a single-table select). FR falls back to EN, like `Guide`.
struct SettingsLink: Identifiable, Hashable {
    enum Section: String, CaseIterable { case legal, about, support }

    /// One section's rows, in the order Settings shows them.
    struct Group: Identifiable {
        let section: Section
        let links: [SettingsLink]
        var id: Section { section }
    }

    let key: String
    let section: Section
    let titleEn: String
    let titleFr: String?
    let url: URL

    var id: String { key }
    /// `https` opens in the in-app Safari sheet; `mailto` goes to the system.
    var opensInApp: Bool { url.scheme == "https" }

    func title(for locale: Locale) -> String { locale.prefersFrench ? (titleFr ?? titleEn) : titleEn }

    private struct Row: Decodable {
        let key: String?
        let section: String?
        let titleEn: String?
        let titleFr: String?
        let url: String?

        enum CodingKeys: String, CodingKey {
            case key, section, url
            case titleEn = "title_en", titleFr = "title_fr"
        }
    }

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "settings-links")
    private static let schemes: Set<String> = ["https", "mailto"]

    /// Rows this build cannot show (unknown section, other scheme, missing EN title) are dropped.
    static func decodeList(_ data: Data) throws -> [SettingsLink] {
        try JSONDecoder().decode([Row].self, from: data).compactMap { row in
            guard let key = row.key,
                  let section = row.section.flatMap(Section.init(rawValue:)),
                  let titleEn = row.titleEn, !titleEn.trimmingCharacters(in: .whitespaces).isEmpty,
                  let raw = row.url, let url = URL(string: raw), schemes.contains(url.scheme ?? "")
            else {
                logger.warning("settings link dropped: \(row.key ?? "nil", privacy: .public)")
                return nil
            }
            return SettingsLink(key: key, section: section, titleEn: titleEn, titleFr: row.titleFr, url: url)
        }
    }

    /// The seed, bundled: shown before the first fetch and whenever it fails.
    static let builtIn: [SettingsLink] = {
        guard let url = Bundle.main.url(forResource: "settings-links-default", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let links = try? decodeList(data)
        else {
            logger.error("built-in settings links missing or unreadable")
            return []
        }
        return links
    }()

    /// Non-empty sections in the fixed order legal, about, support.
    static func grouped(_ links: [SettingsLink]) -> [Group] {
        Section.allCases.compactMap { section in
            let rows = links.filter { $0.section == section }
            return rows.isEmpty ? nil : Group(section: section, links: rows)
        }
    }
}
