// The state machine reads as one file; steps 4 and 5 are in the extension at the bottom.
// swiftlint:disable file_length
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

/// What the flow leaves for the first composer (D9): bound to its user and consumed once by the
/// host, so a session ending between the two never opens B's composer with A's prompt.
struct OnboardingHandoffNote: Equatable, Sendable {
    let userId: String?
    let reason: OnboardingReason?
}

/// The seven-step flow's state machine (#1510). Every interaction is a method here and fires its
/// haptic, as in the record flow. Answers live here until `complete_onboarding` sends them in one
/// go (D5); consent is the exception, recorded on Continue. Mirrors Android's `OnboardingViewModel`.
///
/// Built per user, and a new sign-in gets a new model (D10). `userId` never changes, but it only
/// keys the gate's cache: `record` goes out under the client's ambient session. What protects the
/// ledger is that `OnboardingFlowSlot` calls `sessionEnded()` when the user id changes or the session
/// ends, the write loop checks before every act, and Log out is disabled while saving.
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
    /// Left by a successful `complete_onboarding`; `OnboardingFlowHost` hands it up when `isDone` fires.
    private(set) var doneNote: OnboardingHandoffNote?

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
    /// A person with no glyph at all is left out here and flagged by `hasPersonWithoutGlyph`.
    var answers: OnboardingAnswers {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return OnboardingAnswers(
            displayName: trimmedName.isEmpty ? nil : trimmedName,
            glyphId: glyphId,
            reasons: reasons.isEmpty ? nil : reasons,
            pace: pace,
            souls: souls
        )
    }

    private var souls: [OnboardingSoulDraft] {
        people.enumerated().compactMap { index, person in
            guard Self.isListed(person), let glyph = person.glyphId ?? defaultGlyphId(index) else { return nil }
            let name = person.name.trimmingCharacters(in: .whitespacesAndNewlines)
            return OnboardingSoulDraft(name: name, glyphId: glyph)
        }
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
            complete()
            return
        case .people:
            commitEditingPeople()
        case .promise, .reasons, .you, .handoff:
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
            complete()
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
    /// and nobody's consent is given on their behalf. `OnboardingFlowSlot` calls this when the user
    /// id changes, or from `RootView` on a sign-out: the cover's content does not hear that one. A
    /// model simply dropped stops the same way, at the next act, but SwiftUI's timing for that is no
    /// guarantee.
    func sessionEnded() {
        sessionHasEnded = true
        consent.isSaving = false
        isCompleting = false
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

// Steps 4 to 6 and the carve live in an extension of their own so the class body stays readable.
extension OnboardingFlowModel {
    // MARK: - Step 4

    /// Settings' display-name limit; a person's name shares it.
    static let nameLimit = 40

    /// What step 4's stone offers: the system glyphs, then any carved in the flow.
    var glyphChoices: [Glyph] { systemGlyphs + carved }

    /// A name or a glyph is an answer; either alone is enough.
    var canContinueYou: Bool { !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || glyphId != nil }

    func setName(_ value: String) {
        name = String(value.prefix(Self.nameLimit))
    }

    func pickGlyph(_ id: UUID) {
        haptic(.selection)
        glyphId = id
    }

    func drawForYou() {
        haptic(.selection)
        carvingFor = .you
    }

    // MARK: - Step 5

    var canContinuePeople: Bool { people.contains { !$0.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty } }

    /// Three named people name the button; anything less is the plain line.
    var peopleCTA: LocalizedStringResource {
        let names = people.map { $0.name.trimmingCharacters(in: .whitespacesAndNewlines) }
        guard names.count == 3, names.allSatisfy({ !$0.isEmpty }) else { return "These are my people" }
        return "Keep \(names[0]), \(names[1]) and \(names[2])"
    }

    /// True when a named, closed person would be sent with no glyph, because none loaded
    /// (`souls.glyph_id` is not null). `complete()` refuses that rather than send an empty id.
    var hasPersonWithoutGlyph: Bool {
        people.enumerated().contains { index, person in
            Self.isListed(person) && (person.glyphId ?? defaultGlyphId(index)) == nil
        }
    }

    private static func isListed(_ person: OnboardingPerson) -> Bool {
        !person.isEditing && !person.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// No-op at three people, or while one is being typed.
    func addPerson() {
        guard people.count < 3, !people.contains(where: \.isEditing) else { return }
        haptic(.selection)
        people.append(OnboardingPerson())
    }

    func setPersonName(_ index: Int, _ value: String) {
        guard people.indices.contains(index) else { return }
        people[index].name = String(value.prefix(Self.nameLimit))
    }

    /// The server refuses two souls whose `lower(btrim(name))` collide, which would fail the whole send.
    /// Trimmed and lower-cased like Postgres does for ordinary letters; accents are not folded.
    private static func folded(_ name: String) -> String {
        name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private func isDuplicate(_ index: Int) -> Bool {
        let mine = Self.folded(people[index].name)
        return people.enumerated().contains { $0.offset != index && Self.folded($0.element.name) == mine }
    }

    /// The keyboard's done: a blank or duplicate name removes the place, a name closes the field.
    func commitPerson(_ index: Int) {
        guard people.indices.contains(index) else { return }
        let trimmed = people[index].name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            people.remove(at: index)
        } else if isDuplicate(index) {
            haptic(.warning)
            people.remove(at: index)
        } else {
            people[index].name = trimmed
            people[index].isEditing = false
        }
    }

    /// Tapping a name reopens its field; emptying it removes the place. No-op while another is open.
    func editPerson(_ index: Int) {
        guard people.indices.contains(index), !people.contains(where: \.isEditing) else { return }
        haptic(.selection)
        people[index].isEditing = true
    }

    /// Leaving the step never strands a half-typed name: a blank or duplicate place is dropped, a name is kept.
    private func commitEditingPeople() {
        while let index = people.firstIndex(where: \.isEditing) { commitPerson(index) }
    }

    /// The glyph place `index` wears until a pick: a different one per place, so three people never
    /// look alike. By the enumerated index, never a lookup by value, which gives two equal people one.
    func defaultGlyphId(_ index: Int) -> UUID? {
        systemGlyphs.indices.contains(index) ? systemGlyphs[index].id : systemGlyphs.first?.id
    }

    /// No-op while any field is open: presenting the sheet would take its focus, and a blank commit
    /// would then remove a place and shift the index the sheet targets.
    func openGlyphSheet(_ index: Int) {
        guard people.indices.contains(index), !people.contains(where: \.isEditing) else { return }
        haptic(.selection)
        glyphSheetFor = index
    }

    func closeGlyphSheet() { glyphSheetFor = nil }

    func pickPersonGlyph(_ index: Int, _ glyphId: UUID) {
        guard people.indices.contains(index) else { return }
        haptic(.selection)
        people[index].glyphId = glyphId
        glyphSheetFor = nil
    }

    func drawForPerson(_ index: Int) {
        guard people.indices.contains(index) else { return }
        haptic(.selection)
        glyphSheetFor = nil
        carvingFor = .person(index)
    }

    // MARK: - Step 6

    /// Choosing the chosen row again keeps it: a radio set always has one, once it has any.
    func selectPace(_ value: OnboardingPace) {
        guard !isCompleting else { return }
        haptic(.selection)
        pace = value
    }

    /// The one write (D5). Refused, or failed, it keeps every answer so a retry resends. A person
    /// is never sent without a glyph, and never fewer people than were named.
    private func complete() {
        guard !isCompleting, !isDone, !sessionHasEnded else { return }
        isCompleting = true
        completeFailed = false
        work = Task { [weak self, logger] in
            guard let self else { return }
            // A failed first read gets one more try before anyone is sent without a glyph.
            if self.systemGlyphs.isEmpty, self.hasPersonWithoutGlyph { await self.loadSystemGlyphs() }
            guard !self.sessionHasEnded else { return }
            let answers = self.answers
            let named = self.people.filter(Self.isListed).count
            guard !self.hasPersonWithoutGlyph, answers.souls.count == named else {
                logger.error("onboarding not sent: a named person has no glyph, the system glyphs did not load")
                self.finishCompleting(failed: true)
                return
            }
            do {
                try await self.service.complete(answers)
                guard !self.sessionHasEnded else { return }
                self.doneNote = OnboardingHandoffNote(userId: self.userId, reason: answers.reasons?.first)
                self.isCompleting = false
                self.isDone = true
            } catch {
                logger.error("complete_onboarding failed: \(error.localizedDescription, privacy: .private)")
                self.finishCompleting(failed: true)
            }
        }
    }

    private func finishCompleting(failed: Bool) {
        guard !sessionHasEnded else { return }
        isCompleting = false
        completeFailed = failed
        if failed { haptic(.warning) }
    }

    // MARK: - S1, the carve studio

    /// The carve saved a glyph: it joins the stone and becomes the pick of whoever it was drawn for.
    func carved(_ glyph: Glyph) {
        guard let target = carvingFor else { return }
        carved.append(glyph)
        carvingFor = nil
        switch target {
        case .you:
            glyphId = glyph.id
        case .person(let index):
            if people.indices.contains(index) { people[index].glyphId = glyph.id }
        }
    }

    func carveCancelled() { carvingFor = nil }
}
