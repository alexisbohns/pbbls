import Foundation
@testable import Pebbles

/// What the flow-model suites share: the fakes, the cache, the haptics heard.
/// Nothing is on record by default, so every act is asked (Android's fixture).
@MainActor
final class OnboardingFlowHarness {
    static let glyphs: [Glyph] = (0..<8).map { index in
        Glyph(
            id: UUID(uuidString: String(format: "00000000-0000-0000-0000-%012d", index))!,
            name: "domain:family-\(index)",
            strokes: [],
            viewBox: "0 0 200 200",
            userId: nil
        )
    }
    static let letter = PromiseLetter(
        bodyEn: "There will never be a feed here.",
        bodyFr: nil,
        signedByEn: "Alexis",
        signedByFr: nil
    )

    /// A ledger row, at this build's version unless told otherwise.
    static func row(_ kind: ConsentKind, version: String? = nil) -> ActiveConsent {
        ActiveConsent(kind: kind.rawValue, documentVersion: version ?? ConsentGateLogic.version(for: kind))
    }

    let service = FakeOnboardingService()
    let consents: FakeConsentService
    let cache: ConsentPreferences
    private(set) var taps: [TapHaptic] = []

    init(rows: [ActiveConsent] = []) {
        consents = FakeConsentService(rows: rows)
        cache = ConsentPreferences(defaults: UserDefaults(suiteName: "onboarding-flow-\(UUID().uuidString)")!)
        service.letter = Self.letter
        service.glyphs = Self.glyphs
    }

    func model(userId: String? = "user-1") -> OnboardingFlowModel {
        OnboardingFlowModel(
            service: service,
            consents: consents,
            cache: cache,
            userId: userId,
            haptic: { [weak self] in self?.taps.append($0) }
        )
    }

    func forgetTaps() { taps = [] }

    var gateIsSatisfied: Bool {
        cache.isSatisfied(userId: "user-1", fingerprint: ConsentGateLogic.fingerprint())
    }
}

extension Latch {
    /// Waits for `open`, bounded: a regression then fails the test instead of hanging the whole run.
    func reached() async -> Bool {
        let deadline = ContinuousClock.now + .seconds(10)
        while !isOpen, ContinuousClock.now < deadline {
            try? await Task.sleep(for: .milliseconds(5))
        }
        return isOpen
    }
}

extension OnboardingFlowModel {
    func switchEveryActOn() {
        OnboardingConsentState.order.forEach { toggleConsent($0, true) }
    }

    /// From a fresh model to `consent`, the ledger read.
    func loadToConsent() async {
        await load()
        next()
    }

    /// From a fresh model to `reasons`, every act recorded.
    func loadPastConsent() async {
        await loadToConsent()
        switchEveryActOn()
        next()
        await work?.value
    }

    func seedStep(_ id: OnboardingFlowStep.StepID) {
        seed(step: id, letter: nil, systemGlyphs: OnboardingFlowHarness.glyphs, consent: OnboardingConsentState())
    }
}
