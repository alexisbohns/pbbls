import Foundation
@testable import Pebbles

/// `OnboardingServicing` for the onboarding tests: stubbed results, failures on demand, calls recorded.
final class FakeOnboardingService: OnboardingServicing, @unchecked Sendable {
    private let lock = NSLock()
    private var _completed: Bool
    private var _completedFailure: Error?
    private var _completedCalls = 0
    private var _letter: PromiseLetter?
    private var _glyphs: [Glyph] = []
    private var _completeFailure: Error?
    private var _completeCalls: [OnboardingAnswers] = []

    init(completed: Bool = false) { _completed = completed }

    var completedCalls: Int { lock.withLock { _completedCalls } }
    var completeCalls: [OnboardingAnswers] { lock.withLock { _completeCalls } }
    var completed: Bool {
        get { lock.withLock { _completed } }
        set { lock.withLock { _completed = newValue } }
    }
    var completedFailure: Error? {
        get { lock.withLock { _completedFailure } }
        set { lock.withLock { _completedFailure = newValue } }
    }
    var letter: PromiseLetter? {
        get { lock.withLock { _letter } }
        set { lock.withLock { _letter = newValue } }
    }
    var glyphs: [Glyph] {
        get { lock.withLock { _glyphs } }
        set { lock.withLock { _glyphs = newValue } }
    }
    var completeFailure: Error? {
        get { lock.withLock { _completeFailure } }
        set { lock.withLock { _completeFailure = newValue } }
    }

    func promiseLetter() async throws -> PromiseLetter? { letter }
    func systemGlyphs() async throws -> [Glyph] { glyphs }

    func onboardingCompleted() async throws -> Bool {
        try lock.withLock {
            _completedCalls += 1
            if let failure = _completedFailure { throw failure }
            return _completed
        }
    }

    func complete(_ answers: OnboardingAnswers) async throws {
        try lock.withLock {
            if let failure = _completeFailure { throw failure }
            _completeCalls.append(answers)
        }
    }
}
