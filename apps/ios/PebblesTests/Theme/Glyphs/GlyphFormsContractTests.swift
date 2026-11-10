import Foundation
import Testing
@testable import Pebbles

/// The iOS side of the forms contract (spec §4): `packages/icons/glyphs/forms-contract.json`,
/// generated from `formsOf`, `renderForm` and `resolveState`, against the bundled data.
@Suite("Glyph forms contract")
struct GlyphFormsContractTests {
    struct Entry: Decodable {
        let forms: [String]
        let rendered: [String: [RenderedPathData]]
        let states: [String: String]
    }

    struct Contract: Decodable {
        let glyphs: [String: Entry]
    }

    static func contract() throws -> Contract {
        let url = RepoFile.url("packages/icons/glyphs/forms-contract.json")
        return try JSONDecoder().decode(Contract.self, from: Data(contentsOf: url))
    }

    @Test func everySampledGlyphHasTheGeneratorsForms() throws {
        let contract = try Self.contract()
        #expect(contract.glyphs.count >= 10)
        for (name, entry) in contract.glyphs {
            let glyph = try #require(PbblsGlyph(rawValue: name), Comment(rawValue: name))
            #expect(GlyphCatalog.shared.glyph(glyph).formKeys == entry.forms, Comment(rawValue: name))
        }
    }

    @Test func everyFormRendersAsTheGeneratorRendersIt() throws {
        for (name, entry) in try Self.contract().glyphs {
            let data = GlyphCatalog.shared.glyph(try #require(PbblsGlyph(rawValue: name)))
            for (key, expected) in entry.rendered {
                let form = try #require(GlyphForm(key: key), Comment(rawValue: "\(name) \(key)"))
                #expect(GlyphRenderer.render(data, form) == expected, Comment(rawValue: "\(name) \(key)"))
            }
        }
    }

    @Test func everyStateResolvesAsTheGeneratorResolvesIt() throws {
        for (name, entry) in try Self.contract().glyphs {
            let glyph = try #require(PbblsGlyph(rawValue: name))
            for (spec, expected) in entry.states {
                let parts = spec.split(separator: "@")
                let state = try #require(PbblsGlyphState(rawValue: String(parts[0])), Comment(rawValue: spec))
                let k = parts.count > 1 ? Int(parts[1]) ?? 0 : 0
                #expect(glyph.form(for: state, k: k).key == expected, Comment(rawValue: "\(name) \(spec)"))
            }
        }
    }

    @Test func progressZeroIsRegularAndAMissingFormFallsBackToRegular() throws {
        let stack = GlyphCatalog.shared.glyph(.stack)
        #expect(GlyphRenderer.render(stack, GlyphForm(.progress(0))) == GlyphRenderer.render(stack, .regular))
        #expect(GlyphRenderer.render(stack, .solid) == GlyphRenderer.render(stack, .regular))
    }

    @Test func everyCaseHasData() {
        for glyph in PbblsGlyph.allCases {
            #expect(!GlyphCatalog.shared.glyph(glyph).paths.isEmpty, Comment(rawValue: glyph.rawValue))
        }
    }
}
