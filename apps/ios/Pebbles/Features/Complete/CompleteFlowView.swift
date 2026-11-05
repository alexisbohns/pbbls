import SwiftUI
import os

/// The Complete flow (spec §6): turns a moment into the caller's pebble.
/// Full-screen; presented from the read page and chained from Capture.
struct CompleteFlowView: View {
    let momentId: UUID
    /// For the end screen's title line.
    let momentName: String?
    /// Fired as soon as `complete_pebble` succeeds, while the end screen is
    /// up, and again after each of its edits, so the parent reloads behind
    /// the cover.
    let onCompleted: (UUID) -> Void
    /// Chained from Capture: the valence step says the moment is already
    /// saved and what the three questions are for (D1).
    var afterSave = false
    /// For the end screen's week row; hidden when the host does not know.
    var momentHappenedAt: Date?
    /// The end screen offers "Add a few words" only when the moment has none.
    var momentHasWords = false
    /// "Record another" on the end screen; nil where the host cannot start a
    /// fresh capture (the held card, Home's chained Complete).
    var onRecordAnother: (() -> Void)?

    @Environment(SupabaseService.self) private var supabase
    @Environment(ReferenceDataService.self) private var refs
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(KarmaNotificationService.self) private var karma
    @Environment(AchievementsService.self) private var achievements
    @Environment(CompleteDraftStore.self) private var drafts
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss

    @State private var model = CompleteFlowModel()
    @State private var autosave: CompleteAutosave?
    @State private var hasRestored = false
    /// No completed pebble on the account yet: the valence step opens its
    /// help sheet (D8). Read once in `.task`; a failed read keeps it closed.
    @State private var isFirstPebble = false
    /// Drives the step transition direction so back slides back.
    @State private var isMovingBack = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "complete-flow")

    var body: some View {
        VStack(spacing: 0) {
            if model.step != .success {
                RecordFlowChrome(
                    dotCount: CompleteStep.counted.count,
                    dotIndex: model.step.dotIndex,
                    canGoBack: model.step.previous != nil && !model.isCompleting,
                    onBack: { model.back() },
                    onClose: close
                )
            }
            stepBody
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.pebbles.surface)
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
            // After the restore: a slow read must not delay it, and the step
            // consumes the flag through `onChange(initial:)`, so order is free.
            if let moments = try? await MomentsService(client: supabase.client).pathMoments() {
                isFirstPebble = !moments.contains(where: \.isCompleted)
            }
        }
        // `.task` may run before the reference fetch settles (#647).
        .onChange(of: refs.hasLoaded) { _, _ in restoreIfReady() }
        .onChange(of: model.draftToAutosave) { _, draft in
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
                CompleteRicherStep(
                    momentId: momentId,
                    name: momentName,
                    happenedAt: momentHappenedAt,
                    result: result,
                    draft: model.draft,
                    hasWords: momentHasWords,
                    onExit: { dismiss() },
                    onRecordAnother: onRecordAnother,
                    onEdited: { onCompleted(momentId) }
                )
            } else {
                RecordStepScaffold(
                    title: model.step.title,
                    subtitle: valenceSubtitle ?? model.step.subtitle,
                    pill: model.step == .valence ? nil : AnyView(summaryPill),
                    action: action(for: model.step),
                    // The life step scrolls itself and pins its bar; a scaffold
                    // ScrollView would unpin it.
                    contentFillsHeight: model.step == .life
                ) {
                    CompleteStepContent(step: model.step, model: model, isFirstPebble: isFirstPebble)
                }
            }
        }
        .id(model.step)
        .transition(.asymmetric(
            insertion: .move(edge: isMovingBack ? .leading : .trailing).combined(with: .opacity),
            removal: .move(edge: isMovingBack ? .trailing : .leading).combined(with: .opacity)
        ))
    }

    private var valenceSubtitle: LocalizedStringResource? {
        guard afterSave, model.step == .valence else { return nil }
        return "Moment saved · 3 questions to make it a pebble"
    }

    /// The answers so far (D11). On the emotion step Edit returns to the
    /// valence; on Life, to the emotion, the last answer, unless it was
    /// skipped by a restore and the valence is all there is to edit.
    private var summaryPill: some View {
        RecordSummaryPill(
            valence: model.draft.valence,
            emotionName: model.draft.emotionId.flatMap { palettes.byEmotionId[$0]?.localizedName },
            onEdit: { model.go(to: model.step == .life && model.draft.emotionId != nil ? .emotion : .valence) }
        )
    }

    /// The one action a step offers, if any. The emotion step offers none:
    /// the pick is the advance (A1). The life step's button counts the domains
    /// past the first (D10).
    private func action(for step: CompleteStep) -> RecordStepAction? {
        switch step {
        case .emotion, .success:
            return nil
        case .valence:
            return .primary("Continue", enabled: model.isAnswered, loading: false) { model.advance() }
        case .life:
            let count = model.draft.domainIds.count
            return .primary(
                count > 1 ? "Make it a pebble (\(count))" : "Make it a pebble",
                enabled: model.draft.isComplete,
                loading: model.isCompleting
            ) {
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
        // Never restore over a flow the user has already started.
        guard model.draft.isEmpty, model.step == .valence else { return }
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
        guard !model.isCompleting else { return }
        guard let payload = CompletePebblePayload(momentId: momentId, draft: model.draft) else {
            logger.fault("complete pressed with an incomplete draft")
            model.fail(MomentWriteError.pebbleNeedsDomain.userMessage)
            return
        }
        model.beginCompleting()
        let service = MomentsService(client: supabase.client)
        // Karma already earned by a pebble that exists on the moment (a stale
        // read, a retried chain): `complete_pebble` updates it in place, so
        // the delta is sum-after minus sum-before (R13). Nil = unknown.
        let karmaBefore = await karmaBeforeWrite(service)
        do {
            let pebbleId = try await service.completePebble(payload)
            let resolved = await resolveGlyph(service, pebbleId: pebbleId)
            let karmaAfter = await karmaTotal(service, refId: pebbleId)
            var karmaDelta: Int?
            if let karmaBefore, let karmaAfter { karmaDelta = karmaAfter - karmaBefore }
            // The end screen shows `+N`, so no capsule (A4).
            karma.notifyEarned(amount: karmaDelta ?? 0, reason: .pebbleCreated, presentsCapsule: false)
            achievements.fireCheck()
            autosave?.clear()
            if let owner = supabase.session?.user.id {
                RecentDomains().record(model.draft.domainIds, owner: owner)
            }
            model.succeed(with: CompletionResult(pebbleId: pebbleId, karmaDelta: karmaDelta, glyph: resolved))
            onCompleted(momentId)
        } catch {
            logger.error("complete_pebble failed: \(error.localizedDescription, privacy: .private)")
            // A slug the server names keeps its own copy; anything else says
            // what still stands: the moment is saved, the pebble is not (D6).
            model.fail(knownMomentWriteError(error)?.userMessage
                ?? String(localized: "Moment saved, pebble not made yet · Try again"))
        }
    }

    /// Karma total of the moment's existing pebble, 0 when there is none, nil
    /// when it can't be read (R13: show nothing rather than a wrong number).
    private func karmaBeforeWrite(_ service: MomentsService) async -> Int? {
        do {
            guard let existing = try await service.moment(id: momentId).pebble?.id else { return 0 }
            return await karmaTotal(service, refId: existing)
        } catch {
            logger.warning("pre-completion moment read failed: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }

    private func resolveGlyph(_ service: MomentsService, pebbleId: UUID) async -> PebbleGlyph? {
        do {
            return try await service.resolveGlyph(pebbleId: pebbleId)
        } catch {
            logger.warning("glyph read failed: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }

    private func karmaTotal(_ service: MomentsService, refId: UUID) async -> Int? {
        do {
            return try await service.karmaTotal(refId: refId)
        } catch {
            logger.warning("karma read failed: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }
}
