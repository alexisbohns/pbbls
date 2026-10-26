import SwiftUI
import os

/// What the user asked for after the quick moment saved (spec D13).
enum QuickMomentExit { case open, close }

/// The quick moment: a name and a time, nothing else. Saves through
/// `save_moment`, then hands the id back so Home can open the Complete flow
/// ("Save & open") or just bump the Path ("Save & close").
struct QuickMomentSheet: View {
    let onSaved: (UUID, QuickMomentExit) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(AchievementsService.self) private var achievements
    @Environment(\.dismiss) private var dismiss

    @State private var draft = QuickMomentDraft(now: .now)
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "home.quick")

    var body: some View {
        NavigationStack {
            QuickMomentForm(draft: $draft, isSaving: isSaving, saveError: saveError) { exit in
                Task { await save(exit) }
            }
            .navigationBarTitleDisplayMode(.inline)
            .navigationTitle(Text("home.quick.title"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
        .pebblesScreen()
    }

    private func save(_ exit: QuickMomentExit) async {
        guard draft.canSave, !isSaving else { return }
        isSaving = true
        saveError = nil
        do {
            let id = try await MomentsService(client: supabase.client).saveMoment(draft.payload)
            achievements.fireCheck()
            TapHaptics.play(.success)
            dismiss()
            onSaved(id, exit)
        } catch {
            logger.error("quick save_moment failed: \(error.localizedDescription, privacy: .private)")
            saveError = String(localized: "home.quick.error")
            isSaving = false
        }
    }
}

/// The sheet's content without its services, so a reference can pin it.
struct QuickMomentForm: View {
    @Binding var draft: QuickMomentDraft
    let isSaving: Bool
    let saveError: String?
    let onSave: (QuickMomentExit) -> Void

    /// The field owns its text (as `RecordNameStep` does) so the clamp rewrites
    /// what is on screen while the draft keeps the trimmed value.
    @State private var text: String
    @FocusState private var nameFocused: Bool

    init(draft: Binding<QuickMomentDraft>, isSaving: Bool, saveError: String?,
         onSave: @escaping (QuickMomentExit) -> Void) {
        _draft = draft
        self.isSaving = isSaving
        self.saveError = saveError
        self.onSave = onSave
        _text = State(initialValue: draft.wrappedValue.name)
    }

    private var blocked: Bool { !draft.canSave || isSaving }

    var body: some View {
        VStack(spacing: Spacing.lg) {
            TextField("home.quick.name", text: $text)
                .pebblesFont(.nameInputHand)
                .multilineTextAlignment(.center)
                .focused($nameFocused)
                .submitLabel(.done)
                .padding(Spacing.md)
                .onChange(of: text) { _, newValue in
                    let clamped = String(newValue.prefix(CaptureFlowModel.nameLimit))
                    if clamped != newValue { text = clamped }
                    draft.setName(clamped)
                }

            DatePicker("home.quick.when", selection: $draft.happenedAt,
                       displayedComponents: [.date, .hourAndMinute])
                .datePickerStyle(.compact)
                .tint(Color.pebbles.primary)

            if let saveError {
                Text(verbatim: saveError)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.pebbles.error)
                    .multilineTextAlignment(.center)
            }

            VStack(spacing: Spacing.sm) {
                Button { onSave(.open) } label: { Text("home.quick.saveOpen") }
                    .buttonStyle(PebblesPrimaryButtonStyle(isLoading: false))
                    .disabled(blocked)
                Button { onSave(.close) } label: { Text("home.quick.saveClose").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered)
                    .buttonBorderShape(.capsule)
                    .controlSize(.large)
                    .tint(Color.pebbles.onSurfaceVariant)
                    .disabled(blocked)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.md)
        .frame(maxHeight: .infinity, alignment: .top)
        .onAppear { nameFocused = true }
    }
}
