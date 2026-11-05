import Foundation
@testable import Pebbles

/// `ConsentServicing` for `ConsentGateModel`'s tests: an in-memory ledger that
/// `record` writes to, failures on demand, the calls recorded. Mirrors
/// Android's `FakeConsentService`.
final class FakeConsentService: ConsentServicing, @unchecked Sendable {
    struct Recorded: Equatable {
        let kind: ConsentKind
        let version: String
        let source: String
    }

    private let lock = NSLock()
    private var rows: [ActiveConsent]
    private var _activeCalls = 0
    private var _recordCalls: [Recorded] = []
    private var _activeFailure: Error?
    private var _recordFailure: Error?
    private var _beforeRecord: (@Sendable () async -> Void)?

    static func satisfied() -> [ActiveConsent] {
        ConsentGateLogic.required.map {
            ActiveConsent(kind: $0.rawValue, documentVersion: ConsentGateLogic.version(for: $0))
        }
    }

    init(rows: [ActiveConsent] = FakeConsentService.satisfied()) {
        self.rows = rows
    }

    var activeCalls: Int { lock.withLock { _activeCalls } }
    var recordCalls: [Recorded] { lock.withLock { _recordCalls } }
    var activeFailure: Error? {
        get { lock.withLock { _activeFailure } }
        set { lock.withLock { _activeFailure = newValue } }
    }
    var recordFailure: Error? {
        get { lock.withLock { _recordFailure } }
        set { lock.withLock { _recordFailure = newValue } }
    }

    /// Awaited at the start of every `record`, before it lands: lets a test
    /// hold a record in flight.
    var beforeRecord: (@Sendable () async -> Void)? {
        get { lock.withLock { _beforeRecord } }
        set { lock.withLock { _beforeRecord = newValue } }
    }

    func active() async throws -> [ActiveConsent] {
        try lock.withLock {
            _activeCalls += 1
            if let failure = _activeFailure { throw failure }
            return rows
        }
    }

    func record(kind: ConsentKind, documentVersion: String, source: String) async throws {
        if let hold = beforeRecord { await hold() }
        try lock.withLock {
            if let failure = _recordFailure { throw failure }
            _recordCalls.append(Recorded(kind: kind, version: documentVersion, source: source))
            rows.removeAll { $0.kind == kind.rawValue }
            rows.append(ActiveConsent(kind: kind.rawValue, documentVersion: documentVersion))
        }
    }
}
