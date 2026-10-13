import SwiftUI
import os

/// Edit pebble (R5): the caller's pebble on a moment — emotion, domain,
/// valence, souls, collection, privacy — saved through `complete_pebble`
/// only. The moment's fields edit in `EditMomentSheet`.
///
/// On success it shows what the edit earned (A4: karma after − before, capsule
/// with `pebbleEnriched`), then runs `check_achievements` once.
struct EditPebbleSheet: View {
    let momentId: UUID
    let pebble: MomentDetail.Pebble
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(ReferenceDataService.self) private var refs
    @Environment(KarmaNotificationService.self) private var karma
    @Environment(AchievementsService.self) private var achievements
    @Environment(\.dismiss) private var dismiss

    @State private var draft: CompleteDraft
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "edit-pebble")

    init(momentId: UUID, pebble: MomentDetail.Pebble, onSaved: @escaping () -> Void) {
        self.momentId = momentId
        self.pebble = pebble
        self.onSaved = onSaved
        self._draft = State(initialValue: CompleteDraft(from: pebble))
    }

    var body: some View {
        NavigationStack {
            PebbleFormView(
                draft: $draft,
                domains: refs.domains,
                souls: refs.souls,
                collections: refs.collections,
                saveError: saveError
            )
            .disabled(isSaving)
            .pebblesToolbarTitle("Edit pebble")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") { dismiss() }
                        .disabled(isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        PebbleToolbarButton("Save") { Task { await save() } }
                            .disabled(!draft.isComplete)
                    }
                }
                ToolbarItemGroup(placement: .bottomBar) {
                    VisibilityChip(visibility: $draft.visibility)
                        .disabled(isSaving)
                    Spacer()
                }
            }
            .pebblesScreen()
        }
        .interactiveDismissDisabled(isSaving)
    }

    private func save() async {
        guard !isSaving else { return }
        guard let payload = CompletePebblePayload(momentId: momentId, draft: draft) else {
            logger.fault("edit pebble saved with an incomplete draft")
            return
        }
        isSaving = true
        saveError = nil
        let service = MomentsService(client: supabase.client)
        // A4 / R13: what this edit earned = total after − total before. An
        // unreadable total shows nothing rather than a wrong number.
        let before = await karmaTotal(service)
        do {
            _ = try await service.completePebble(payload)
            let after = await karmaTotal(service)
            if let before, let after {
                karma.notifyEarned(amount: after - before, reason: .pebbleEnriched)
            }
            // After the karma read (A4): an edit can change the emotion,
            // newly qualifying an emotion badge.
            achievements.fireCheck()
            onSaved()
            dismiss()
        } catch {
            logger.error("complete_pebble (edit) failed: \(error.localizedDescription, privacy: .private)")
            saveError = userMessageForMomentWriteError(error)
            isSaving = false
        }
    }

    private func karmaTotal(_ service: MomentsService) async -> Int? {
        do {
            return try await service.karmaTotal(refId: pebble.id)
        } catch {
            logger.warning("karma read failed: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }
}
