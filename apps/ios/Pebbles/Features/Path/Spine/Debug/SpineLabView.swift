#if DEBUG
import SwiftUI

/// The Spine's debug lab (#1130), Settings → Developer, beside the stone lab.
/// Until D5a the Spine shows only here and in the references; each part adds a
/// section. Verbatim text throughout, so nothing reaches the string catalogue.
struct SpineLabView: View {
    enum Section: String, CaseIterable, Identifiable {
        case stones
        var id: String { rawValue }
    }

    @Environment(\.dismiss) private var dismiss
    @State private var section: Section = .stones

    var body: some View {
        NavigationStack {
            ScrollView {
                switch section {
                case .stones:
                    SpineStoneGallery(pressable: true)
                }
            }
            .background(Color.pebbles.surface)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Picker(selection: $section) {
                        ForEach(Section.allCases) { Text(verbatim: $0.rawValue).tag($0) }
                    } label: {
                        Text(verbatim: "Section")
                    }
                    .pickerStyle(.segmented)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { dismiss() } label: { Text(verbatim: "Done") }
                }
            }
        }
    }
}
#endif
