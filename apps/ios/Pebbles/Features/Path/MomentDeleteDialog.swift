import Supabase
import SwiftUI

/// What a delete acts on (R8): the moment, and its pebble when completed.
struct MomentDeleteTarget: Identifiable, Equatable {
    let momentId: UUID
    let pebbleId: UUID?

    var id: UUID { momentId }

    init(momentId: UUID, pebbleId: UUID?) {
        self.momentId = momentId
        self.pebbleId = pebbleId
    }

    init(summary: MomentSummary) {
        self.init(momentId: summary.id, pebbleId: summary.pebble?.id)
    }

    init(detail: MomentDetail) {
        self.init(momentId: detail.id, pebbleId: detail.pebble?.id)
    }

    var options: [MomentDeleteOption] {
        pebbleId == nil ? [.moment] : [.pebbleKeepMoment, .moment]
    }

    /// The read page closes when the moment is gone and stays (as a bare
    /// moment) when only the pebble went.
    func closesReadPage(after option: MomentDeleteOption) -> Bool {
        option == .moment
    }

    var title: LocalizedStringResource {
        pebbleId == nil ? "Delete this moment?" : "Delete this pebble?"
    }

    var message: LocalizedStringResource {
        pebbleId == nil
            ? "This can't be undone."
            : "Keeping the moment leaves it on your path as a blank stone. Deleting the moment removes its photo too. This can't be undone."
    }

    func label(for option: MomentDeleteOption) -> LocalizedStringResource {
        switch option {
        case .pebbleKeepMoment: return "Delete the pebble, keep the moment"
        case .moment: return pebbleId == nil ? "Delete moment" : "Delete the moment too"
        }
    }
}

enum MomentDeleteOption: Hashable, Identifiable {
    case pebbleKeepMoment
    case moment

    var id: Self { self }
}

extension MomentDeletion {
    /// Runs the chosen delete. Throws so the host can show its error alert.
    static func perform(
        _ option: MomentDeleteOption,
        on target: MomentDeleteTarget,
        client: SupabaseClient
    ) async throws {
        switch option {
        case .pebbleKeepMoment:
            guard let pebbleId = target.pebbleId else { return }
            try await deletePebble(pebbleId, client: client)
        case .moment:
            try await deleteMoment(target.momentId, client: client)
        }
    }
}

extension View {
    /// The one delete confirmation (R8). `perform` runs on a destructive tap,
    /// after the warning haptic.
    func momentDeleteDialog(
        target: Binding<MomentDeleteTarget?>,
        perform: @escaping (MomentDeleteTarget, MomentDeleteOption) -> Void
    ) -> some View {
        confirmationDialog(
            target.wrappedValue.map { Text($0.title) } ?? Text(verbatim: ""),
            isPresented: Binding(
                get: { target.wrappedValue != nil },
                set: { if !$0 { target.wrappedValue = nil } }
            ),
            titleVisibility: .visible,
            presenting: target.wrappedValue
        ) { presented in
            ForEach(presented.options) { option in
                Button(role: .destructive) {
                    TapHaptics.play(.warning)
                    target.wrappedValue = nil
                    perform(presented, option)
                } label: {
                    Text(presented.label(for: option))
                }
            }
            Button("Cancel", role: .cancel) { target.wrappedValue = nil }
        } message: { presented in
            Text(presented.message)
        }
    }
}
