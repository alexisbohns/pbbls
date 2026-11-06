import Foundation
import Observation
import os

/// Pure gating for the flow (#1510 D10). `hasSeen` is the device flag the old slides already
/// wrote (`hasSeenOnboarding`): accounts from before the flow never wrote `onboarding_completed`,
/// and the flag is what keeps them from being re-onboarded on the same device.
enum OnboardingGate {
    static func shouldPresent(userId: String?, hasSeen: Bool, completed: Bool?) -> Bool {
        userId != nil && !hasSeen && completed == false
    }

    /// Still waiting to know. The app and the consent gate hold while this is true, so a new
    /// account never sees either before the promise (D2). A failed read is not pending: nothing
    /// is presented this launch, and the consent gate decides, failing closed.
    static func isPending(userId: String?, hasSeen: Bool, completed: Bool?, readFailed: Bool) -> Bool {
        userId != nil && !hasSeen && completed == nil && !readFailed
    }
}

/// Owned by `RootView`, like `ConsentGateModel`.
@Observable
@MainActor
final class OnboardingGateModel {
    private(set) var completed: Bool?
    private(set) var readFailed = false
    private(set) var hasSeen: Bool

    @ObservationIgnored var service: (any OnboardingServicing)?
    @ObservationIgnored private var userId: String?
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private(set) var work: Task<Void, Never>?
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "onboarding")

    static let hasSeenKey = "hasSeenOnboarding"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        hasSeen = defaults.bool(forKey: Self.hasSeenKey)
    }

    var shouldPresent: Bool { OnboardingGate.shouldPresent(userId: userId, hasSeen: hasSeen, completed: completed) }
    var isPending: Bool {
        OnboardingGate.isPending(userId: userId, hasSeen: hasSeen, completed: completed, readFailed: readFailed)
    }

    /// Idempotent for one user. A new id forgets the last user's answer.
    func start(userId: String?) {
        guard userId != self.userId else { return }
        work?.cancel()
        self.userId = userId
        completed = nil
        readFailed = false
        guard userId != nil, !hasSeen else { return }
        read(userId)
    }

    private func read(_ uid: String?) {
        guard let service else {
            logger.error("onboarding gate without a service")
            readFailed = true
            return
        }
        work = Task {
            do {
                let done = try await service.onboardingCompleted()
                guard uid == userId else { return }
                completed = done
            } catch {
                logger.error("onboarding flag read failed: \(error.localizedDescription, privacy: .private)")
                guard uid == userId else { return }
                readFailed = true
            }
        }
    }

    /// The flow ended: this device never re-presents, whatever the profile says next.
    func finish() {
        hasSeen = true
        defaults.set(true, forKey: Self.hasSeenKey)
        completed = true
    }
}
