import Foundation
import Observation
import os

/// Holds a signed-in user behind the consent gate until the ledger carries
/// every act `ConsentGateLogic.required` names at a current version. Mirrors
/// Android's `ConsentGateViewModel` (design §5.3).
///
/// Owned by `RootView`, not by a tab: the gate is an overlay above the app's
/// navigation, so nothing that navigates can route around it. `RootView`
/// calls `start(userId:)` whenever the session's user id changes.
@Observable
@MainActor
final class ConsentGateModel {
    enum GateError: Equatable {
        case offline
        case other

        var message: LocalizedStringResource {
            switch self {
            case .offline: return "You appear to be offline. Please check your connection."
            case .other: return "We couldn't save that. Please try again."
            }
        }
    }

    struct Required: Equatable {
        var missing: [ConsentKind]
        var ticked: Set<ConsentKind> = []
        var isSubmitting = false
        var error: GateError?

        var canContinue: Bool { !isSubmitting && ticked.isSuperset(of: missing) }
    }

    enum State: Equatable {
        /// No signed-in user: nothing to gate.
        case idle
        case checking
        /// `userId` passed. Carried so a stale pass can never let a different user through.
        case satisfied(userId: String)
        case required(Required)
        /// The ledger could not be read. Fails closed.
        case failed(GateError)
    }

    private(set) var state: State = .idle

    /// Set by `RootView` before `start`. Without it the gate fails closed.
    @ObservationIgnored var service: (any ConsentServicing)?
    @ObservationIgnored var cache = ConsentPreferences()
    /// The check or record in flight, so tests can await it.
    @ObservationIgnored private(set) var work: Task<Void, Never>?
    @ObservationIgnored private var userId: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "consent")

    /// Whether the app beneath may be used. Only a pass for the current user opens it.
    var isPassed: Bool {
        guard let userId, case .satisfied(let passed) = state else { return false }
        return passed == userId
    }

    /// Idempotent for the same user: a check already in flight, or a verdict
    /// already reached, must not restart.
    func start(userId: String?) {
        if userId == self.userId, state != .idle { return }
        // A check or record in flight belongs to the previous user: it must
        // never land under the next session.
        if userId != self.userId { work?.cancel() }
        self.userId = userId
        guard let userId else {
            state = .idle
            return
        }
        if cache.isSatisfied(userId: userId, fingerprint: ConsentGateLogic.fingerprint()) {
            state = .satisfied(userId: userId)
        } else {
            check(userId)
        }
    }

    func retry() {
        guard let userId else { return }
        check(userId)
    }

    func toggle(_ kind: ConsentKind, _ checked: Bool) {
        guard case .required(var form) = state, !form.isSubmitting else { return }
        if checked { form.ticked.insert(kind) } else { form.ticked.remove(kind) }
        form.error = nil
        state = .required(form)
    }

    func submit() {
        guard case .required(var form) = state, let uid = userId, form.canContinue else { return }
        form.isSubmitting = true
        form.error = nil
        state = .required(form)
        let missing = form.missing
        guard let service else {
            fail(record: uid, error: nil)
            return
        }
        work = Task {
            do {
                // One idempotent record_consent per act, as web's OAuth callback
                // does. The acts are independent, so there is no cross-row
                // invariant for a batch RPC to protect; if one fails, the
                // re-read below shows only what is still missing.
                for kind in missing {
                    try Task.checkCancellation()
                    try await service.record(
                        kind: kind,
                        documentVersion: ConsentGateLogic.version(for: kind),
                        source: ConsentGateLogic.source
                    )
                }
                let active = try await service.active()
                guard uid == userId else { return }
                resolve(uid, ConsentGateLogic.missing(active))
            } catch {
                logger.error("record consent failed: \(error.localizedDescription, privacy: .private)")
                fail(record: uid, error: error)
            }
        }
    }

    private func fail(record uid: String, error: Error?) {
        guard uid == userId, case .required(var form) = state else { return }
        form.isSubmitting = false
        form.error = Self.gateError(error)
        state = .required(form)
    }

    private func check(_ uid: String) {
        state = .checking
        guard let service else {
            logger.error("consent check without a service; failing closed")
            state = .failed(.other)
            return
        }
        work = Task {
            do {
                let active = try await service.active()
                guard uid == userId else { return }
                resolve(uid, ConsentGateLogic.missing(active))
            } catch {
                logger.error("consent check failed: \(error.localizedDescription, privacy: .private)")
                guard uid == userId else { return }
                state = .failed(Self.gateError(error))
            }
        }
    }

    private func resolve(_ uid: String, _ missing: [ConsentKind]) {
        if missing.isEmpty {
            cache.markSatisfied(userId: uid, fingerprint: ConsentGateLogic.fingerprint())
            state = .satisfied(userId: uid)
        } else {
            state = .required(Required(missing: missing))
        }
    }

    static func gateError(_ error: Error?) -> GateError {
        guard let error else { return .other }
        if error is URLError { return .offline }
        let nsError = error as NSError
        return nsError.domain == NSURLErrorDomain ? .offline : .other
    }
}
