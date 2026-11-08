import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The whole icon set at 24pt on the Spine paper: every base and twin in
    /// manifest order, the CoreSVG check of the generated catalog (spec R3).
    /// Drawn as pages: the whole set on one sheet (1,878 icons, 8,540 px tall)
    /// came back empty from `ImageRenderer`, while the 122-icon sheet rendered
    /// at 1,088 px. A page of 20 rows stays near that height.
    @Suite("Icon sheet references") @MainActor
    struct IconSheetReferences {
        nonisolated static let perRow = 12
        nonisolated static let rowsPerPage = 20
        nonisolated static let perPage = perRow * rowsPerPage
        nonisolated static let pages = Array(0..<((PbblsIcon.allCases.count + perPage - 1) / perPage))

        static func sheet(page: Int) -> some View {
            let icons = Array(PbblsIcon.allCases.dropFirst(page * perPage).prefix(perPage))
            let rows = stride(from: 0, to: icons.count, by: perRow).map { start in
                Array(icons[start..<min(start + perRow, icons.count)])
            }
            // A LazyVGrid lays out nothing under ImageRenderer: draw the rows by hand.
            return VStack(alignment: .leading, spacing: Spacing.xs) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    HStack(spacing: Spacing.xs) {
                        ForEach(row, id: \.self) { PbblsIconImage($0) }
                    }
                }
            }
                .foregroundStyle(Color.pebbles.onSurface)
                .padding(Spacing.lg)
                .frame(width: ReferenceConfig.phoneWidth, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases, pages)
        func sheet(scheme: ReferenceScheme, page: Int) throws {
            ReferenceImage.assertMatches(
                Self.sheet(page: page),
                named: "sheet-\(page + 1)",
                config: ReferenceConfig(scheme: scheme)
            )
        }
    }
}
