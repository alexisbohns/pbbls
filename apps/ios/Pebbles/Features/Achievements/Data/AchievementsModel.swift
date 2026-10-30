import Foundation
import Observation
import os

/// The achievements page's state (#1306, spec §4). The device's last page shows
/// at once and the read replaces it when it lands (stale-while-revalidate, no
/// blink). A failed read keeps what is shown and logs; only a first visit with
/// nothing kept and no answer shows the error. Opening a sheet marks its card
/// seen, fire-and-forget; a ready mark pulls its ceremony once. The view sets
/// `service`; the tests fake it. Android: `AchievementsViewModel`.
@Observable
@MainActor
final class AchievementsModel {
    enum PullOutcome: Equatable {
        /// A row came back: the ceremony, with the karma the pull paid.
        case pulled(karma: Int)
        /// No row, or no answer, but the page read again carries the completion's
        /// `pulled_at`: this client's pull landed and its answer was lost. The
        /// ceremony plays without a karma line (spec §3.4).
        case pulledWithoutKarma
        /// No row, and the read again does not show the completion pulled. Never success.
        case alreadyPulled
        case notReady
        case failed
    }

    private(set) var page: AchievementsPage?
    private(set) var isLoading = false
    /// Nothing to show: no kept copy, and the read failed.
    private(set) var failed = false
    /// The stack whose ceremony is being pulled, so its mark cannot fire twice.
    private(set) var pulling: AchievementsPage.CeremonyStack?

    @ObservationIgnored var service: (any AchievementsPageServicing)?
    let zone: TimeZone

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "achievements-page")

    init(zone: TimeZone = SpineCalendar.current.timeZone) {
        self.zone = zone
    }

    /// The kept copy when nothing is shown yet, then `grant` (the screen-open
    /// `check_achievements()`, the retroactive grant), then the read.
    func load(grant: @MainActor () async -> Void) async {
        guard let service, !isLoading else { return }
        isLoading = true
        defer { isLoading = false }
        if page == nil, let kept = await service.cachedPage(zone: zone) { page = kept }
        await grant()
        await refresh(service)
    }

    /// Marks `card` seen (spec §4). The marks on screen stay until the next
    /// read. Returned so the tests can wait for it.
    @discardableResult
    func opened(_ card: AchievementCard) -> Task<Void, Never>? {
        guard let service else { return nil }
        let logger = logger
        return Task {
            do {
                try await service.markSeen(card)
            } catch {
                logger.warning(
                    "mark_achievements_seen(\(card.rawValue, privacy: .public)) failed: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    /// Pulls a ready ceremony (D9), then reads the page again so its mark goes.
    /// An empty or lost answer is settled by that read: the completion's
    /// `pulled_at` says the pull landed (spec §3.4).
    func pull(_ stack: AchievementsPage.CeremonyStack) async -> PullOutcome {
        guard let service, pulling == nil else { return .failed }
        pulling = stack
        defer { pulling = nil }
        var outcome: PullOutcome
        do {
            switch try await service.pull(stack, zone: zone) {
            case .pulled(let karma):
                outcome = .pulled(karma: karma)
            case .alreadyPulled:
                outcome = .alreadyPulled
            }
        } catch CeremonyPullError.notReady {
            logger.warning("pull_ceremony(\(stack.rawValue, privacy: .public)) is not ready")
            outcome = .notReady
        } catch {
            logger.error(
                "pull_ceremony(\(stack.rawValue, privacy: .public)) failed: \(error.localizedDescription, privacy: .public)")
            outcome = .failed
        }
        let reread = await refresh(service)
        if outcome == .alreadyPulled || outcome == .failed {
            if reread, page?.completion(stack)?.pulledAt != nil {
                logger.notice("pull_ceremony(\(stack.rawValue, privacy: .public)) answered nothing; the read shows it pulled")
                outcome = .pulledWithoutKarma
            } else if outcome == .alreadyPulled {
                logger.error("pull_ceremony(\(stack.rawValue, privacy: .public)) returned no row")
            }
        }
        return outcome
    }

    /// Reads the page; true when the read landed.
    @discardableResult
    private func refresh(_ service: any AchievementsPageServicing) async -> Bool {
        do {
            page = try await service.page(zone: zone)
            failed = false
            return true
        } catch {
            logger.error("get_achievements failed: \(error.localizedDescription, privacy: .public)")
            failed = page == nil
            return false
        }
    }
}
