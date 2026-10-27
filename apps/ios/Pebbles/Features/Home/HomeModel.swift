import Foundation
import os

/// The Home tab's state. Static parts (greeting, shortcuts, About header)
/// render at once; the server projections load together, each section
/// keeping its last value while it reloads (spec §1.6). Later parts add
/// the checklist and the guides here.
@Observable @MainActor final class HomeModel {
    private(set) var name: String = ""
    private(set) var hasLoadedOnce = false
    private(set) var wraps: HomeWraps?
    private(set) var wrapsError: String?
    /// The decks Home has played this launch (D-plan-1): one ledger for the tab.
    @ObservationIgnored let introLedger = IntroLedger()
    @ObservationIgnored var zone: TimeZone = SpineCalendar.current.timeZone
    @ObservationIgnored var service: (any HomeServicing)?
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "home.model")

    func load() async {
        defer { hasLoadedOnce = true }
        guard service != nil else { return }
        async let months: Void = loadWraps()
        await loadName()
        await months
    }

    private func loadName() async {
        guard let service else { return }
        do {
            name = try await service.displayName() ?? ""
        } catch {
            logger.warning("display name failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Keeps the last months when the reload fails, and says so.
    func loadWraps() async {
        guard let service else { return }
        do {
            wraps = try await service.wraps(zone: zone)
            wrapsError = nil
        } catch {
            logger.warning("home wraps failed: \(error.localizedDescription, privacy: .public)")
            wrapsError = String(localized: "home.wraps.error")
        }
    }

    /// Unfolds `month` and flips it in place, so the card and the sheet follow.
    func reveal(_ month: CalendarMonth, zone: TimeZone) async throws -> HomeReveal {
        guard let service else { throw CancellationError() }
        let reveal = try await service.reveal(month: month, zone: zone)
        if let at = reveal.revealedAt, let current = wraps {
            wraps = HomeWraps(months: current.months.map {
                $0.month == month ? HomeMonth(month: $0.month, count: $0.count, revealedAt: at, cover: $0.cover) : $0
            })
        }
        return reveal
    }
}
