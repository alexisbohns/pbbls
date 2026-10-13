import SwiftUI
import os

/// The Complete flow (spec §6): turns a moment into the caller's pebble.
/// Full-screen; presented from the read page and chained from Capture.
struct CompleteFlowView: View {
    let momentId: UUID
    /// For the success screen's title line.
    let momentName: String?
    /// Fired as soon as `complete_pebble` succeeds, while the success step is
    /// up, so the parent reloads behind the cover.
    let onCompleted: (UUID) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(ReferenceDataService.self) private var refs
    @Environment(KarmaNotificationService.self) private var karma
    @Environment(AchievementsService.self) private var achievements
    @Environment(CompleteDraftStore.self) private var drafts
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss

    @State private var model = CompleteFlowModel()
    @State private var autosave: CompleteAutosave?
    @State private var hasRestored = false
    /// Drives the step transition direction so back slides back.
    @State private var isMovingBack = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "complete-flow")

    var body: some View {
        VStack(spacing: 0) {
            if model.step != .success {
                RecordFlowChrome(
                    dotCount: CompleteStep.counted.count,
                    dotIndex: model.step.dotIndex,
                    canGoBack: model.step.previous != nil,
                    onBack: { model.back() },
                    onClose: close
                )
            }
            stepBody
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.system.background)
        .pebblesScreen()
        .animation(.snappy(duration: 0.28), value: model.step)
        .onChange(of: model.step) { old, new in
            isMovingBack = new.rawValue < old.rawValue
        }
        .task {
            TapHaptics.prepare()
            // The valence fan wobbles nine artworks on first sight.
            Task.detached(priority: .utility) {
                ValenceArt.prewarm()
                StoneCarvingArt.prewarm()
            }
            if autosave == nil {
                autosave = CompleteAutosave(
                    store: drafts,
                    momentId: momentId,
                    ownerId: { [supabase] in supabase.session?.user.id }
                )
            }
            restoreIfReady()
        }
        // `.task` may run before the reference fetch settles (#647).
        .onChange(of: refs.hasLoaded) { _, _ in restoreIfReady() }
        .onChange(of: model.draft) { _, draft in
            guard hasRestored, model.step != .success else { return }
            autosave?.schedule(draft)
        }
        .onChange(of: scenePhase) { _, phase in
            // Last reliable moment before a process kill.
            if phase != .active { autosave?.flush() }
        }
    }

    // MARK: - Steps

    @ViewBuilder
    private var stepBody: some View {
        Group {
            if model.step == .success, let result = model.completed {
                CompleteSuccessStep(
                    name: momentName,
                    result: result,
                    valence: model.draft.valence ?? .neutralMedium,
                    emotionId: model.draft.emotionId,
                    onExit: { dismiss() }
                )
            } else {
                RecordStepScaffold(
                    title: model.step.title,
                    subtitle: model.step.subtitle,
                    action: action(for: model.step)
                ) {
                    CompleteStepContent(step: model.step, model: model)
                }
            }
        }
        .id(model.step)
        .transition(.asymmetric(
            insertion: .move(edge: isMovingBack ? .leading : .trailing).combined(with: .opacity),
            removal: .move(edge: isMovingBack ? .trailing : .leading).combined(with: .opacity)
        ))
    }

    /// The one action a step offers, if any. Tile steps offer none: the pick
    /// is the advance (A1).
    private func action(for step: CompleteStep) -> RecordStepAction? {
        switch step {
        case .emotion, .domain, .success:
            return nil
        case .valence:
            return .primary("Continue", enabled: model.isAnswered, loading: false) { model.advance() }
        case .souls, .collection:
            return .primary(model.optionalButtonIsSkip ? "Skip" : "Done", enabled: true, loading: false) {
                model.advance()
            }
        case .privacy:
            return .primary("Make it a pebble", enabled: model.draft.isComplete, loading: model.isCompleting) {
                Task { await complete() }
            }
        }
    }

    // MARK: - Autosave (A3)

    /// Silent restore, once, after reference data has loaded, landing on the
    /// first unanswered mandatory step.
    private func restoreIfReady() {
        guard !hasRestored, refs.hasLoaded else { return }
        hasRestored = true
        guard let owner = supabase.session?.user.id,
              let saved = drafts.load(momentId: momentId, ownerId: owner) else { return }
        model.restore(saved, known: .init(
            soulIds: Set(refs.souls.map(\.id)),
            collectionIds: Set(refs.collections.map(\.id))
        ))
    }

    /// ✕ never asks (A1): the answers are already on disk.
    private func close() {
        autosave?.flush()
        dismiss()
    }

    // MARK: - Complete

    private func complete() async {
        guard let payload = CompletePebblePayload(momentId: momentId, draft: model.draft) else {
            model.fail(MomentWriteError.pebbleNeedsDomain.userMessage)
            return
        }
        model.beginCompleting()
        let service = MomentsService(client: supabase.client)
        do {
            let pebbleId = try await service.completePebble(payload)
            // A first completion: no events existed for this pebble id, so
            // the total after the write is its delta (R13).
            async let glyph = try? service.resolveGlyph(pebbleId: pebbleId)
            async let earned = try? service.karmaTotal(refId: pebbleId)
            let (resolved, karmaDelta) = await (glyph, earned)
            // The success screen shows `+N`, so no capsule (A4).
            karma.notifyEarned(amount: karmaDelta ?? 0, reason: .pebbleCreated, presentsCapsule: false)
            achievements.fireCheck()
            autosave?.clear()
            model.succeed(with: CompletionResult(pebbleId: pebbleId, karmaDelta: karmaDelta, glyph: resolved))
            onCompleted(momentId)
        } catch {
            logger.error("complete_pebble failed: \(error.localizedDescription, privacy: .private)")
            model.fail(userMessageForMomentWriteError(error))
        }
    }
}
