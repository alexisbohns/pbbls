import Foundation
import Observation
import os
#if DEBUG
import Supabase
#endif

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

    /// What stands above the tabs. Fails closed: anything but a pass leaves something opaque there.
    /// The flow collects the consent itself, promise first (D2), so while it is in play nobody meets
    /// the gate; everyone else's gate is unchanged.
    static func consentLayer(
        isPassed: Bool,
        isPending: Bool,
        shouldPresent: Bool,
        isPresenting: Bool
    ) -> OnboardingConsentLayer {
        if isPassed { return .open }
        return isPending || shouldPresent || isPresenting ? .surface : .gate
    }

    /// An invite waits for the flow's end, and past it until the cover is gone (`isClosing`): a sheet
    /// presented while a cover is going is dropped.
    static func invitesWait(isPending: Bool, shouldPresent: Bool, isPresenting: Bool, isClosing: Bool) -> Bool {
        isPending || shouldPresent || isPresenting || isClosing
    }

    /// One presentation once everything is out of the way. A parked invite wins: missing the composer
    /// is fine (D9), and an invite comes from someone who is waiting on it.
    static func handOff(parkedInvite: String?, note: OnboardingHandoffNote?) -> OnboardingHandOff {
        if let parkedInvite { return .invite(parkedInvite) }
        if let note { return .composer(note) }
        return .nothing
    }

    /// The one form a user id takes in the gates: the consent cache and the hand-off note are compared
    /// by string, so every side has to build it the same way.
    static func userKey(_ id: UUID?) -> String? { id?.uuidString.lowercased() }
}

/// What `OnboardingGate.consentLayer` chose.
enum OnboardingConsentLayer: Equatable {
    /// Consent is on record: nothing above the tabs.
    case open
    /// The plain surface, while the flow is pending, about to present or presenting.
    case surface
    /// `ConsentGateView`.
    case gate
}

/// What `OnboardingGate.handOff` chose.
enum OnboardingHandOff: Equatable {
    case invite(String)
    case composer(OnboardingHandoffNote)
    case nothing
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
    /// Bumped whenever the answer a read would deliver is no longer wanted (user change, `finish`).
    /// A read lands only if its token is still current, on success and on failure alike.
    @ObservationIgnored private var generation = 0
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
        generation += 1
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
        let token = generation
        work = Task {
            do {
                let done = try await service.onboardingCompleted()
                guard token == generation else { return }
                completed = done
            } catch {
                // A superseded or cancelled read logs nothing and changes nothing.
                guard token == generation, !Task.isCancelled else { return }
                logger.error("onboarding flag read failed: \(error.localizedDescription, privacy: .private)")
                readFailed = true
            }
        }
    }

    /// The flow ended: this device never re-presents, whatever the profile says next.
    func finish() {
        work?.cancel()
        generation += 1
        hasSeen = true
        defaults.set(true, forKey: Self.hasSeenKey)
        completed = true
    }
}

#if DEBUG
extension OnboardingGateModel {
    /// The Settings row: shows the flow again without a restart. `OnboardingServicing` stays as it is:
    /// this is the one write the app makes to the flag outside `complete_onboarding`, and it only
    /// exists in debug builds. `profiles.onboarding_completed` is still owner-writable.
    func replay(client: SupabaseClient) async {
        guard let userId else {
            logger.error("onboarding replay without a user")
            return
        }
        do {
            try await client
                .from("profiles")
                .update(["onboarding_completed": false])
                .eq("user_id", value: userId)
                .execute()
        } catch {
            logger.error("onboarding replay failed: \(error.localizedDescription, privacy: .private)")
            return
        }
        forgetForReplay()
        await work?.value
        if completed == true { logger.error("onboarding replay: the profile still says completed") }
    }

    /// The device's half: the flag and the last answer go, then the profile is read again.
    func forgetForReplay() {
        work?.cancel()
        generation += 1
        defaults.removeObject(forKey: Self.hasSeenKey)
        hasSeen = false
        completed = nil
        readFailed = false
        guard userId != nil else { return }
        read(userId)
    }
}
#endif
