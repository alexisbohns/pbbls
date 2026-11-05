import Foundation
import os

/// A string in both product languages; anything but French reads English.
struct Localized: Codable, Equatable {
    let en: String
    let fr: String

    func text(for locale: Locale) -> String {
        locale.language.languageCode?.identifier == "fr" ? fr : en
    }
}

struct CrisisLine: Codable, Equatable {
    let label: Localized
    let number: String
    let tel: String
    let note: Localized?
}

struct CrisisCountry: Codable, Equatable {
    let code: String
    let name: Localized
    let lines: [CrisisLine]
}

struct CrisisWorldwide: Codable, Equatable {
    let label: Localized
    let url: String
}

/// The canonical crisis lines list (`packages/shared/crisis-resources.json`), bundled.
struct CrisisResources: Codable, Equatable {
    let version: Int
    let verifiedOn: String
    let worldwide: CrisisWorldwide
    let countries: [CrisisCountry]

    enum CodingKeys: String, CodingKey {
        case version
        case verifiedOn = "verified_on"
        case worldwide
        case countries
    }

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "crisis-resources")

    static func decode(_ data: Data) throws -> CrisisResources {
        try JSONDecoder().decode(CrisisResources.self, from: data)
    }

    static let bundled: CrisisResources = {
        guard let url = Bundle.main.url(forResource: "crisis-resources", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let resources = try? decode(data)
        else {
            logger.error("bundled crisis resources missing or unreadable")
            return CrisisResources(
                version: 0,
                verifiedOn: "",
                worldwide: CrisisWorldwide(
                    label: Localized(en: "Find a helpline anywhere", fr: "Trouver une ligne d'écoute partout"),
                    url: "https://findahelpline.com"
                ),
                countries: []
            )
        }
        return resources
    }()

    /// The device region when listed, otherwise FR (the home market).
    func defaultCountry(region: String?) -> CrisisCountry? {
        if let region, let found = countries.first(where: { $0.code == region.uppercased() }) {
            return found
        }
        return countries.first(where: { $0.code == "FR" }) ?? countries.first
    }
}
