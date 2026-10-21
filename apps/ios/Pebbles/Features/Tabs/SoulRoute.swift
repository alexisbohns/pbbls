import SwiftUI
import os

/// Shows `PeopleRoute.soul(id)` (#903 §3). The souls list's own rows are the
/// freshest source (a soul created a moment ago is not in reference data until
/// `refreshSouls()` lands); reference data covers a restored scene whose list
/// has not loaded yet. An id found in neither, once reference data has
/// loaded, was deleted elsewhere: log and pop.
struct SoulRoute: View {
    let id: UUID
    let known: [SoulWithGlyph]
    let onChanged: () -> Void

    @Environment(ReferenceDataService.self) private var refs
    @Environment(\.dismiss) private var dismiss

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "tabs.soul-route")

    static func resolve(
        _ id: UUID,
        known: [SoulWithGlyph],
        refs: ReferenceDataService
    ) -> RouteResolution<SoulWithGlyph> {
        if let soul = known.first(where: { $0.id == id }) ?? refs.souls.first(where: { $0.id == id }) {
            return .found(soul)
        }
        return refs.hasLoaded ? .missing : .loading
    }

    var body: some View {
        switch Self.resolve(id, known: known, refs: refs) {
        case .found(let soul):
            SoulDetailView(initial: soul, onChanged: onChanged)
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .pebblesScreen()
        case .missing:
            Color.clear.task {
                logger.notice("soul \(id.uuidString, privacy: .private) no longer exists; popping its route")
                dismiss()
            }
        }
    }
}
