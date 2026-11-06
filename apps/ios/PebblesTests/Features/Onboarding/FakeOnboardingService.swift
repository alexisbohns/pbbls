import Foundation
@testable import Pebbles

/// `OnboardingServicing` for the onboarding tests: stubbed results, failures on demand, calls recorded.
final class FakeOnboardingService: OnboardingServicing, @unchecked Sendable {
    private let lock = NSLock()
    private var _completed: Bool
    private var _completedFailure: Error?
    private var _completedCalls = 0
    private var _letter: PromiseLetter?
    private var _letterFailure: Error?
    private var _glyphs: [Glyph] = []
    private var _glyphsFailure: Error?
    private var _glyphCalls = 0
    private var _completeFailure: Error?
    private var _completeCalls: [OnboardingAnswers] = []

    private var _onCompleted: (@Sendable (Int) async throws -> Bool)?

    /// Replaces the answer of `onboardingCompleted`, called with the 1-based call number,
    /// so a test can hold a read in flight and release it later.
    var onCompleted: (@Sendable (Int) async throws -> Bool)? {
        get { lock.withLock { _onCompleted } }
        set { lock.withLock { _onCompleted = newValue } }
    }

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
    var letterFailure: Error? {
        get { lock.withLock { _letterFailure } }
        set { lock.withLock { _letterFailure = newValue } }
    }
    var glyphs: [Glyph] {
        get { lock.withLock { _glyphs } }
        set { lock.withLock { _glyphs = newValue } }
    }
    var glyphsFailure: Error? {
        get { lock.withLock { _glyphsFailure } }
        set { lock.withLock { _glyphsFailure = newValue } }
    }
    var glyphCalls: Int { lock.withLock { _glyphCalls } }
    var completeFailure: Error? {
        get { lock.withLock { _completeFailure } }
        set { lock.withLock { _completeFailure = newValue } }
    }

    func promiseLetter() async throws -> PromiseLetter? {
        try lock.withLock {
            if let failure = _letterFailure { throw failure }
            return _letter
        }
    }

    func systemGlyphs() async throws -> [Glyph] {
        try lock.withLock {
            _glyphCalls += 1
            if let failure = _glyphsFailure { throw failure }
            return _glyphs
        }
    }

    func onboardingCompleted() async throws -> Bool {
        let (call, hook) = lock.withLock { () -> (Int, (@Sendable (Int) async throws -> Bool)?) in
            _completedCalls += 1
            return (_completedCalls, _onCompleted)
        }
        if let hook { return try await hook(call) }
        return try lock.withLock {
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

/// One suspended read, released by the test.
final class Latch: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<Void, Never>?
    private var opened = false

    /// Whether `open` has run, so a test can wait for it without hanging when it never comes.
    var isOpen: Bool { lock.withLock { opened } }

    func wait() async {
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
            let ready = lock.withLock { () -> Bool in
                if !opened { continuation = c }
                return opened
            }
            if ready { c.resume() }
        }
    }

    func open() {
        let c = lock.withLock { () -> CheckedContinuation<Void, Never>? in
            opened = true
            defer { continuation = nil }
            return continuation
        }
        c?.resume()
    }
}
