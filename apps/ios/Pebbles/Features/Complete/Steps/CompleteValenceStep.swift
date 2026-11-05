import SwiftUI

/// How big and how bright (A1). Commits in place and arrives empty (D7);
/// `Continue` advances once a stone is chosen. The help sheet (D8) opens by
/// itself over an account's first pebble and lives behind the callout after.
struct CompleteValenceStep: View {
    let model: CompleteFlowModel
    /// True on an account with no completed pebble (read once by the flow).
    let isFirstPebble: Bool

    @Environment(SupabaseService.self) private var supabase
    @State private var showsHelp = false

    var body: some View {
        VStack(spacing: Spacing.md) {
            ValencePickerContent(selected: model.draft.valence) { picked in
                model.select(valence: picked)
            }
            Button { showsHelp = true } label: {
                Label { Text("How to read this") } icon: { Image(systemName: "info.circle") }
                    .pebblesFont(.captionEmphasized)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            .buttonStyle(.plain)
        }
        .sheet(isPresented: $showsHelp) { ValenceHelpSheet() }
        // The flow learns `isFirstPebble` from an async read that may land
        // after this step is on screen, so watch the flag rather than appear.
        .onChange(of: isFirstPebble, initial: true) { _, isFirst in
            guard isFirst, let owner = supabase.session?.user.id, !ValenceHelpSeen.seen(owner) else { return }
            ValenceHelpSeen.markSeen(owner)
            showsHelp = true
        }
    }
}
