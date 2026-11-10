import SwiftUI
import Testing
@testable import Pebbles

/// One page of a form group in `GlyphFormSheetReferences`.
struct GlyphFormSheetPage: Hashable, Sendable, CustomTestStringConvertible {
    let group: String
    let index: Int

    var testDescription: String { "\(group)-\(index + 1)" }
}

/// One glyph drawn in one form on a sheet.
struct GlyphFormSheetCell: Hashable {
    let glyph: PbblsGlyph
    let form: GlyphForm
}

extension References {
    /// The glyph set in its forms (icon forms spec §4): regular, solid, marked,
    /// and one family for the rest (each stepped glyph done, each parted
    /// glyph's first parts form, each dotted drawing), at 24 pt in manifest
    /// order. Pages of at most 240 glyphs, as `IconSheetReferences` draws them:
    /// a taller sheet comes back empty from `ImageRenderer`.
    @Suite("Glyph form sheet references") @MainActor
    struct GlyphFormSheetReferences {
        nonisolated static let perRow = 12
        nonisolated static let rowsPerPage = 20
        nonisolated static let perPage = perRow * rowsPerPage

        nonisolated static let groups = ["regular", "solid", "marked", "other"]

        /// The form a group draws a glyph in, or nil when the glyph is not in the group.
        nonisolated static func form(of glyph: PbblsGlyph, in group: String) -> GlyphForm? {
            let forms = glyph.forms
            switch group {
            case "regular":
                return .regular
            case "solid":
                return forms.contains(.solid) ? .solid : nil
            case "marked":
                return forms.contains(.marked) ? .marked : nil
            default:
                if let done = forms.last(where: { form in
                    if case .progress = form.fill { return !form.marked }
                    return false
                }) {
                    return done
                }
                if let part = forms.first(where: { form in
                    if case .parts = form.fill { return true }
                    return false
                }) {
                    return part
                }
                return forms.contains(.dotted) ? .dotted : nil
            }
        }

        nonisolated static func drawn(_ group: String) -> [GlyphFormSheetCell] {
            PbblsGlyph.allCases.compactMap { glyph in
                form(of: glyph, in: group).map { GlyphFormSheetCell(glyph: glyph, form: $0) }
            }
        }

        nonisolated static let pages: [GlyphFormSheetPage] = groups.flatMap { group in
            (0..<((drawn(group).count + perPage - 1) / perPage)).map { GlyphFormSheetPage(group: group, index: $0) }
        }

        static func sheet(_ page: GlyphFormSheetPage) -> some View {
            let glyphs = Array(drawn(page.group).dropFirst(page.index * perPage).prefix(perPage))
            let rows = stride(from: 0, to: glyphs.count, by: perRow).map { start in
                Array(glyphs[start..<min(start + perRow, glyphs.count)])
            }
            // A LazyVGrid lays out nothing under ImageRenderer: draw the rows by hand.
            return VStack(alignment: .leading, spacing: Spacing.xs) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    HStack(spacing: Spacing.xs) {
                        ForEach(row, id: \.self) { PbblsGlyphView($0.glyph, form: $0.form) }
                    }
                }
            }
                .foregroundStyle(Color.pebbles.onSurface)
                .padding(Spacing.lg)
                .frame(width: ReferenceConfig.phoneWidth, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        @Test func everyGroupHasGlyphs() {
            for group in Self.groups {
                #expect(!Self.drawn(group).isEmpty, Comment(rawValue: group))
            }
            #expect(Self.drawn("regular").count == PbblsGlyph.allCases.count)
        }

        @Test(arguments: ReferenceScheme.allCases, pages)
        func sheet(scheme: ReferenceScheme, page: GlyphFormSheetPage) throws {
            ReferenceImage.assertMatches(
                Self.sheet(page),
                named: "forms-\(page.group)-\(page.index + 1)",
                config: ReferenceConfig(scheme: scheme)
            )
        }
    }
}
