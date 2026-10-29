import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The whole icon set at 24pt on the Spine paper: every base and twin in
    /// manifest order, the CoreSVG check of the generated catalog (spec R3).
    @Suite("Icon sheet references") @MainActor
    struct IconSheetReferences {
        static let perRow = 12

        static func sheet() -> some View {
            let rows = stride(from: 0, to: PbblsIcon.allCases.count, by: perRow).map { start in
                Array(PbblsIcon.allCases[start..<min(start + perRow, PbblsIcon.allCases.count)])
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

        @Test(arguments: ReferenceScheme.allCases)
        func sheet(scheme: ReferenceScheme) throws {
            ReferenceImage.assertMatches(Self.sheet(), named: "sheet", config: ReferenceConfig(scheme: scheme))
        }
    }
}
