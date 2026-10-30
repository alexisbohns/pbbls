import Foundation
import Observation
import os

/// The achievements page's state (#1306, spec §4). The device's last page shows
/// at once and the read replaces it when it lands (stale-while-revalidate, no
/// blink). A failed read keeps what is shown and logs; only a first visit with
/// nothing kept and no answer shows the error. Opening a sheet marks its card
/// seen, fire-and-forget; a ready mark pulls its ceremony once. Reads can
/// overlap (the screen's load and a pull's re-read), so each takes a sequence
/// number and only a read newer than the last one shown replaces the page. A
/// cancelled read is neither an error nor logged. The view sets `service`; the
/// tests fake it. Android: `AchievementsViewModel`.
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
        /// Already pulled (the fresh read shows `pulled_at`), or no row and the
        /// read again does not show it pulled. Never success.
        case alreadyPulled
        /// Not offered yet: the fresh read shows it not ready, or `not_ready`.
        case notReady
        /// Another pull is running; this one did nothing.
        case busy
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

    /// The last read asked for, and the newest one whose page is shown.
    @ObservationIgnored private var readsAsked = 0
    @ObservationIgnored private var readShown = 0

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
        if page == nil, let kept = await service.cachedPage(zone: zone), page == nil, readShown == 0 { page = kept }
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
    /// The pull is decided on a page read in this session, never on the kept
    /// copy: before any read has landed it reads first. Only a completion that
    /// read shows ready and not yet pulled is pulled; one already pulled is
    /// `.alreadyPulled` and one not offered `.notReady`, with no call. An empty
    /// or lost answer is settled by the read after it: the completion, unpulled
    /// before, now carrying `pulled_at` says this pull landed (spec §3.4).
    func pull(_ stack: AchievementsPage.CeremonyStack) async -> PullOutcome {
        let name = stack.rawValue
        guard let service else {
            logger.error("pull_ceremony(\(name, privacy: .public)) asked without a service")
            return .failed
        }
        if let running = pulling {
            logger.notice("pull_ceremony(\(name, privacy: .public)) asked while \(running.rawValue, privacy: .public) is pulling")
            return .busy
        }
        pulling = stack
        defer { pulling = nil }
        if readShown == 0 { await refresh(service) }
        guard readShown > 0 else {
            logger.error("pull_ceremony(\(name, privacy: .public)) not asked: no page read to decide on")
            return .failed
        }
        guard let before = page?.completion(stack), before.pulledAt == nil else {
            if page?.completion(stack)?.pulledAt != nil {
                logger.notice("pull_ceremony(\(name, privacy: .public)) not asked: the read shows it pulled")
                return .alreadyPulled
            }
            logger.notice("pull_ceremony(\(name, privacy: .public)) not asked: no completion")
            return .notReady
        }
        guard before.isReady else {
            logger.notice("pull_ceremony(\(name, privacy: .public)) not asked: not offered yet")
            return .notReady
        }
        var outcome: PullOutcome
        do {
            switch try await service.pull(stack, zone: zone) {
            case .pulled(let karma):
                outcome = .pulled(karma: karma)
            case .alreadyPulled:
                outcome = .alreadyPulled
            }
        } catch CeremonyPullError.notReady {
            logger.warning("pull_ceremony(\(name, privacy: .public)) is not ready")
            outcome = .notReady
        } catch {
            logger.error("pull_ceremony(\(name, privacy: .public)) failed: \(error.localizedDescription, privacy: .public)")
            outcome = .failed
        }
        let reread = await refresh(service)
        if outcome == .alreadyPulled || outcome == .failed {
            if reread, page?.completion(stack)?.pulledAt != nil {
                logger.notice("pull_ceremony(\(name, privacy: .public)) answered nothing; the read shows it pulled")
                outcome = .pulledWithoutKarma
            } else if outcome == .alreadyPulled {
                logger.error("pull_ceremony(\(name, privacy: .public)) returned no row")
            }
        }
        return outcome
    }

    /// Reads the page; true when the read landed. A read that lands after a
    /// newer one already shown is dropped: the page on screen is the newer.
    @discardableResult
    private func refresh(_ service: any AchievementsPageServicing) async -> Bool {
        readsAsked += 1
        let read = readsAsked
        do {
            let fresh = try await service.page(zone: zone)
            if read > readShown {
                readShown = read
                page = fresh
                failed = false
            }
            return true
        } catch where Self.isCancellation(error) {
            return false
        } catch {
            logger.error("get_achievements failed: \(error.localizedDescription, privacy: .public)")
            failed = page == nil
            return false
        }
    }

    /// A read the task or the session gave up on: not a failure to show or log.
    nonisolated static func isCancellation(_ error: Error) -> Bool {
        if error is CancellationError { return true }
        return (error as? URLError)?.code == .cancelled
    }
}
