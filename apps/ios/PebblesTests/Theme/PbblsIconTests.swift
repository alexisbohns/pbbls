import Foundation
import Testing
import UIKit
@testable import Pebbles

/// The iOS half of the icon contract (spec §7): the generated enum against the
/// real manifest, read in place through `#filePath` as `HomePayloads` does, and
/// every case against the compiled asset catalog.
@Suite("Pbbls icons")
struct PbblsIconTests {
    static let manifest = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // Theme
        .deletingLastPathComponent() // PebblesTests
        .deletingLastPathComponent() // apps/ios
        .deletingLastPathComponent() // apps
        .deletingLastPathComponent() // the repository
        .appendingPathComponent("packages/icons/icons.json")

    struct Row: Decodable {
        let name: String
        let status: String
        let variants: [String]
    }

    struct Manifest: Decodable {
        let icons: [Row]
    }

    static func rows() throws -> [Row] {
        try JSONDecoder().decode(Manifest.self, from: Data(contentsOf: manifest)).icons
    }

    /// The names the generator emits, in manifest order: every row that is not
    /// `exploring`, each followed by its twins.
    static func emitted(_ rows: [Row]) -> [String] {
        rows.filter { $0.status != "exploring" }.flatMap { row in
            [row.name] + row.variants.map { "\(row.name).\($0)" }
        }
    }

    @Test func everyCaseIsATemplateImageInTheBundle() throws {
        for icon in PbblsIcon.allCases {
            let image = try #require(UIImage(named: icon.assetName, in: .main, with: nil),
                                     Comment(rawValue: icon.assetName))
            #expect(image.renderingMode == .alwaysTemplate, Comment(rawValue: icon.assetName))
            #expect(image.size == CGSize(width: 24, height: 24), Comment(rawValue: icon.assetName))
        }
    }

    @Test func casesMatchTheManifest() throws {
        let emitted = try Self.emitted(Self.rows())
        #expect(emitted == PbblsIcon.allCases.map(\.rawValue))
        #expect(!emitted.isEmpty)
    }

    @Test func assetNamesFoldTheManifestName() throws {
        #expect(PbblsIcon(rawValue: "home")?.assetName == "pbbls-home")
        #expect(PbblsIcon(rawValue: "home.active")?.assetName == "pbbls-home-active")
        #expect(PbblsIcon(rawValue: "pebble-flat.lit-1")?.assetName == "pbbls-pebble-flat-lit-1")
    }

    @Test func twinsMatchVariants() throws {
        for row in try Self.rows() where row.status != "exploring" {
            let base = try #require(PbblsIcon(rawValue: row.name), Comment(rawValue: row.name))
            #expect((base.activeTwin != nil) == row.variants.contains("active"), Comment(rawValue: row.name))
            #expect(base.activeTwin?.rawValue == (row.variants.contains("active") ? "\(row.name).active" : nil))
            for n in 1...3 {
                #expect((base.lit(n) != nil) == row.variants.contains("lit-\(n)"),
                        Comment(rawValue: "\(row.name) lit-\(n)"))
            }
            #expect(base.lit(0) == nil && base.lit(4) == nil, Comment(rawValue: row.name))
        }
    }

    @Test func aTwinAnswersForItsBase() throws {
        let twin = try #require(PbblsIcon.allCases.first { $0.rawValue.hasSuffix(".active") })
        #expect(twin.base == String(twin.rawValue.dropLast(".active".count)))
        #expect(twin.activeTwin == twin)
    }
}
