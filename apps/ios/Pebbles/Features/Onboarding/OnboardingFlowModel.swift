import Foundation
import Observation
import os

/// Step 2's four switches (#1510 D3). `recorded` acts are on and locked; `on` are the ones switched on here.
struct OnboardingConsentState: Equatable {
    /// Display order, health first (the mock's and Android's). `ConsentGateLogic.required` keeps the gate's order.
    static let order: [ConsentKind] = [.healthData, .terms, .privacy, .ageAssurance]

    var recorded: Set<ConsentKind> = []
    var on: Set<ConsentKind> = []
    var isSaving = false
    var failed = false

    var canContinue: Bool { !isSaving && recorded.union(on).isSuperset(of: Self.order) }
    /// Switched on here and not yet in the ledger: what Continue records.
    var toRecord: [ConsentKind] { Self.order.filter { on.contains($0) && !recorded.contains($0) } }
    /// Drives the quiet Log out: shown while any act is not on record.
    var hasMissingActs: Bool { !recorded.isSuperset(of: Self.order) }
}

/// One piece of step 5's stone: a named person, or the empty place being typed into.
struct OnboardingPerson: Equatable {
    var name = ""
    /// The picked glyph, or nil until a pick: `defaultGlyphId(index)` is used until then.
    var glyphId: UUID?
    /// True while the name field is open under the piece (S3).
    var isEditing = true
}

/// Who the carve studio (S1) is open for.
enum OnboardingCarveTarget: Equatable { case you, person(Int) }

/// The seven-step flow's state machine (#1510). Every interaction is a method here and fires its
/// haptic, as in the record flow. Answers live here until `complete_onboarding` sends them in one
/// go (D5); consent is the exception, recorded on Continue. Mirrors Android's `OnboardingViewModel`.
///
/// Built per user, and a new sign-in gets a new model (D10). `userId` never changes, but it only
/// keys the gate's cache: `record` goes out under the client's ambient session. What protects the
/// ledger is that the host calls `sessionEnded()` when the user id changes, the write loop checks
/// before every act, and Log out is disabled while saving.
@Observable
@MainActor
final class OnboardingFlowModel {
    private(set) var step: OnboardingFlowStep = OnboardingFlowSteps.all[0]
    private(set) var letter: PromiseLetter?
    private(set) var letterUnavailable = false
    private(set) var systemGlyphs: [Glyph] = []
    private(set) var consent = OnboardingConsentState()
    /// In pick order: the first one picked chooses the hand-off's prompt (D9).
    private(set) var reasons: [OnboardingReason] = []
    var name = ""
    private(set) var glyphId: UUID?
    /// Glyphs carved in the flow (S1), shown on the stone beside the system ones.
    private(set) var carved: [Glyph] = []
    private(set) var people: [OnboardingPerson] = []
    /// Which person's glyph sheet is open (S2), or nil.
    private(set) var glyphSheetFor: Int?
    private(set) var carvingFor: OnboardingCarveTarget?
    private(set) var pace: OnboardingPace?
    private(set) var isCompleting = false
    private(set) var completeFailed = false
    private(set) var isDone = false

    /// The consent write in flight, so tests can await it. Not tied to the view: leaving the
    /// screen must not cancel a request the server may have accepted.
    @ObservationIgnored private(set) var work: Task<Void, Never>?
    /// The glyph retry in flight, so tests can await it.
    @ObservationIgnored private(set) var glyphWork: Task<Void, Never>?
    @ObservationIgnored private var sessionHasEnded = false

    private let service: any OnboardingServicing
    private let consents: any ConsentServicing
    private let cache: ConsentPreferences
    private let userId: String?
    private let haptic: @MainActor (TapHaptic) -> Void
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "onboarding")

    init(
        service: any OnboardingServicing,
        consents: any ConsentServicing,
        cache: ConsentPreferences = ConsentPreferences(),
        userId: String?,
        haptic: @escaping @MainActor (TapHaptic) -> Void = { TapHaptics.play($0) }
    ) {
        self.service = service
        self.consents = consents
        self.cache = cache
        self.userId = userId
        self.haptic = haptic
    }

    /// Index into `visible`; the progress row shows `all.count` stones.
    var stepIndex: Int { OnboardingFlowSteps.visible.firstIndex(of: step) ?? 0 }

    /// True while a write is in flight: Back and Skip/Later hold (correction 11).
    var chromeDisabled: Bool { consent.isSaving || isCompleting }

    /// The answers as they would be sent now; nil where a step was skipped or left blank.
    /// `souls` arrives with the people members.
    var answers: OnboardingAnswers {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return OnboardingAnswers(
            displayName: trimmedName.isEmpty ? nil : trimmedName,
            glyphId: glyphId,
            reasons: reasons.isEmpty ? nil : reasons,
            pace: pace
        )
    }

    // MARK: - Loading

    /// The letter, the glyphs and the ledger, each on its own: a slow or failed read holds up
    /// neither of the others. The flow is presented only once the session is live, because the
    /// last two read through RLS, and an anon read answers `200 []` with no error.
    func load() async {
        async let letterRead: Void = loadLetter()
        async let glyphsRead: Void = loadSystemGlyphs()
        async let ledgerRead: Void = loadLedger()
        await letterRead
        await glyphsRead
        await ledgerRead
    }

    private func loadLetter() async {
        do {
            let read = try await service.promiseLetter()
            letter = read
            letterUnavailable = read == nil
        } catch {
            // The view cancelling its `.task` is not a failed read.
            guard !Task.isCancelled else { return }
            logger.error("promise letter failed: \(error.localizedDescription, privacy: .private)")
            letterUnavailable = letter == nil
        }
    }

    private func loadSystemGlyphs() async {
        do {
            let glyphs = try await service.systemGlyphs()
            guard !glyphs.isEmpty else {
                logger.error("system glyphs came back empty")
                return
            }
            systemGlyphs = glyphs
        } catch {
            guard !Task.isCancelled else { return }
            logger.error("system glyphs failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    private func loadLedger() async {
        do {
            let active = try await consents.active()
            let onRecord = Set(OnboardingConsentState.order).subtracting(ConsentGateLogic.missing(active))
            // A union, not an assignment: this read can land after an act recorded here, and
            // must not take it back.
            consent.recorded.formUnion(onRecord)
        } catch {
            guard !Task.isCancelled else { return }
            // Every act is asked, and `record_consent` is idempotent.
            logger.error("consent read failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    // MARK: - Stepping

    func next() {
        switch step.id {
        case .consent:
            guard !consent.isSaving else { return }
            guard consent.canContinue else {
                haptic(.warning)
                return
            }
            guard consent.toRecord.isEmpty else {
                recordConsents()
                return
            }
            // All four are on record, perhaps by a read that covered what a write failed on, so
            // the error line goes too. The cache is left to the gate's own check, which marks it.
            consent.failed = false
        case .pace:
            // `complete()` sends from here.
            return
        case .promise, .reasons, .you, .people, .handoff:
            break
        }
        move(+1)
    }

    func skip() {
        guard step.skip != .none, !chromeDisabled else { return }
        switch step.id {
        case .reasons:
            reasons = []
        case .you:
            name = ""
            glyphId = nil
        case .people:
            people = []
        case .pace:
            pace = nil
            // `complete()` sends from here.
            return
        case .promise, .consent, .handoff:
            break
        }
        move(+1)
    }

    func back() {
        guard stepIndex > 0, !chromeDisabled else { return }
        move(-1)
    }

    private func move(_ delta: Int) {
        let steps = OnboardingFlowSteps.visible
        haptic(.advance)
        step = steps[min(max(stepIndex + delta, 0), steps.count - 1)]
        completeFailed = false
        glyphSheetFor = nil
        carvingFor = nil
        // A failed or too-early first read gets another try on the steps that draw the glyphs.
        if systemGlyphs.isEmpty, step.id == .you || step.id == .people {
            glyphWork = Task { await loadSystemGlyphs() }
        }
    }

    // MARK: - Step 2

    /// A switch is the user's until Continue: nothing reaches the ledger, so nothing has to be
    /// undone. An act already on record stays on and locked: this step never withdraws one.
    func toggleConsent(_ kind: ConsentKind, _ isOn: Bool) {
        guard !consent.recorded.contains(kind), !consent.isSaving else { return }
        haptic(.selection)
        if isOn {
            consent.on.insert(kind)
        } else {
            consent.on.remove(kind)
        }
        consent.failed = false
    }

    /// The session this model was built for is over. The act already sent finishes under its own
    /// token and nothing after it goes out: the next one would be sent as whoever signs in next,
    /// and nobody's consent is given on their behalf. The host calls this when the user id
    /// changes. A model simply dropped stops the same way, at the next act, but SwiftUI's timing
    /// for that is no guarantee.
    func sessionEnded() {
        sessionHasEnded = true
        consent.isSaving = false
    }

    /// Continue: every act switched on here goes in the ledger, then the flow moves on. The acts
    /// are independent, so one failing does not stop the others, and a retry sends the remainder.
    private func recordConsents() {
        guard !sessionHasEnded else { return }
        let toRecord = consent.toRecord
        consent.isSaving = true
        consent.failed = false
        // `logger` is captured on its own: the early exit has to log when `self` is already gone.
        work = Task { [weak self, logger] in
            var failed = false
            for (index, kind) in toRecord.enumerated() {
                guard let self, !self.sessionHasEnded else {
                    let unsent = toRecord.count - index
                    logger.notice("session over, consent write stopped: \(unsent, privacy: .public) acts left unsent")
                    return
                }
                do {
                    try await self.consents.record(
                        kind: kind,
                        documentVersion: ConsentGateLogic.version(for: kind),
                        source: ConsentGateLogic.source
                    )
                    self.consent.recorded.insert(kind)
                } catch {
                    logger.error("record consent failed: \(error.localizedDescription, privacy: .private)")
                    failed = true
                }
            }
            self?.finishRecording(failed: failed)
        }
    }

    private func finishRecording(failed: Bool) {
        guard !sessionHasEnded else { return }
        consent.isSaving = false
        consent.failed = failed
        guard !failed else {
            haptic(.warning)
            return
        }
        // Every act on record: the gate's cache says so, so the gate does not re-ask after the flow.
        if let userId, consent.recorded.isSuperset(of: OnboardingConsentState.order) {
            cache.markSatisfied(userId: userId, fingerprint: ConsentGateLogic.fingerprint())
        }
        move(+1)
    }

    // MARK: - Step 3

    func toggleReason(_ reason: OnboardingReason) {
        haptic(.selection)
        if let index = reasons.firstIndex(of: reason) {
            reasons.remove(at: index)
        } else {
            reasons.append(reason)
        }
    }

    // MARK: - Seeding

    /// Test and reference seeding only. The defaulted values are the state that has no way in
    /// yet, or none a reference can drive without a network.
    func seed(
        step id: OnboardingFlowStep.StepID,
        letter: PromiseLetter?,
        systemGlyphs: [Glyph],
        consent: OnboardingConsentState,
        glyphId: UUID? = nil,
        people: [OnboardingPerson] = [],
        pace: OnboardingPace? = nil,
        glyphSheetFor: Int? = nil,
        carvingFor: OnboardingCarveTarget? = nil,
        isCompleting: Bool = false,
        completeFailed: Bool = false
    ) {
        if let seeded = OnboardingFlowSteps.all.first(where: { $0.id == id }) { step = seeded }
        self.letter = letter
        self.systemGlyphs = systemGlyphs
        self.consent = consent
        self.glyphId = glyphId
        self.people = people
        self.pace = pace
        self.glyphSheetFor = glyphSheetFor
        self.carvingFor = carvingFor
        self.isCompleting = isCompleting
        self.completeFailed = completeFailed
    }
}
