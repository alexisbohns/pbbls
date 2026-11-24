import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The crop editor (#1654): each tool, both schemes, English and French on Paper.
    @Suite("Photo crop references") @MainActor
    struct PhotoCropReferences {
        private static let height: CGFloat = 760

        private static func config(_ scheme: ReferenceScheme) -> ReferenceConfig {
            ReferenceConfig(scheme: scheme, locales: scheme == .paper ? [.en, .fr] : [.en])
        }

        private static func editor(tool: CropTool) -> some View {
            PhotoCropEditor(source: .fixture, initialTool: tool, onCancel: {}, onDone: { _, _ in })
                .frame(width: ReferenceConfig.phoneWidth, height: height)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func ratio(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.editor(tool: .ratio), named: "cropRatio", config: Self.config(scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func rotate(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.editor(tool: .rotate), named: "cropRotate", config: Self.config(scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func filter(scheme: ReferenceScheme) async throws {
            let crop = PhotoCropModel(source: .fixture)
            let look = PhotoLookModel()
            look.look.select(.vivid)
            await look.prepare(try await crop.render(), for: crop.geometry)
            let editor = PhotoCropEditor(source: .fixture, initialTool: .filter, look: look,
                                         onCancel: {}, onDone: { _, _ in })
                .frame(width: ReferenceConfig.phoneWidth, height: Self.height)
            ReferenceImage.assertMatches(editor, named: "cropFilter", config: Self.config(scheme))
        }
    }
}
