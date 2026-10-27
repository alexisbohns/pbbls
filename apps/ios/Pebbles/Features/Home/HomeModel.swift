import Foundation
import os

/// The Home tab's state. Static parts (greeting, shortcuts, About header)
/// render at once; the server projections load together, each section
/// keeping its last value while it reloads (spec §1.6). Later parts add
/// the checklist and the guides here.
enum HomeModelError: Error { case noService }

@Observable @MainActor final class HomeModel {
    private(set) var name: String = ""
    private(set) var hasLoadedOnce = false
    private(set) var wraps: HomeWraps?
    private(set) var wrapsError: String?
    private(set) var checklist: HomeChecklist?
    private(set) var claimError: String?
    private(set) var isClaiming = false
    /// The About Pebbles readings; nil until the first read lands (spec §1.6).
    private(set) var guides: [Guide]?
    /// The decks Home has played this launch (D-plan-1): one ledger for the tab.
    @ObservationIgnored let introLedger = IntroLedger()
    @ObservationIgnored var zone: TimeZone = SpineCalendar.current.timeZone
    @ObservationIgnored var service: (any HomeServicing)?
    @ObservationIgnored var achievements: (any AchievementsProviding)?
    /// Bumped by every checklist read and by a claim, so a slower earlier read
    /// cannot overwrite a newer state.
    @ObservationIgnored private var loadGeneration = 0
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "home.model")

    func load() async {
        defer { hasLoadedOnce = true }
        guard service != nil else { return }
        async let months: Void = loadWraps()
        async let firsts: Void = loadChecklist()
        async let readings: Void = loadGuides()
        await loadName()
        await months
        await firsts
        await readings
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

    /// Keeps the last readings when the reload fails; nothing is drawn under
    /// the header for a user who has never had them (spec §1.6).
    func loadGuides() async {
        guard let service else { return }
        do {
            guides = try await service.guides()
        } catch {
            logger.warning("home guides failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// The first five (spec D6): the retroactive check first, so a veteran's
    /// history is already ticked, then the catalog and the unlocks. A failed
    /// read keeps the last checklist.
    func loadChecklist() async {
        guard let achievements else { return }
        loadGeneration += 1
        let generation = loadGeneration
        await achievements.checkIgnoringFailure()
        do {
            async let catalogRows = achievements.loadCatalog()
            async let unlockRows = achievements.loadUnlocks()
            let catalog = try await catalogRows
            let unlocks = try await unlockRows
            let slugById = Dictionary(catalog.map { ($0.id, $0.slug) }, uniquingKeysWith: { first, _ in first })
            var unlockedAt: [String: Date] = [:]
            for unlock in unlocks {
                if let slug = slugById[unlock.achievementId] { unlockedAt[slug] = unlock.unlockedAt }
            }
            guard generation == loadGeneration else { return }
            checklist = HomeChecklist.make(catalog: catalog, unlocked: Set(unlockedAt.keys), unlockedAt: unlockedAt)
        } catch {
            guard !Task.isCancelled else { return }
            logger.warning("home checklist failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Claims the blue pebble. An empty grant is not success (the RPC's
    /// contract): the row stays and says so.
    func claim() async {
        guard let achievements, !isClaiming else { return }
        isClaiming = true
        claimError = nil
        defer { isClaiming = false }
        do {
            let granted = try await achievements.claimBluePebble()
            if granted.isEmpty {
                logger.error("claim_blue_pebble granted nothing")
                claimError = String(localized: "home.firstFive.claimFailed")
            } else {
                loadGeneration += 1 // a read begun before the grant must not land after it
                await loadChecklist()
            }
        } catch {
            logger.error("claim_blue_pebble failed: \(error.localizedDescription, privacy: .public)")
            claimError = String(localized: "home.firstFive.claimFailed")
        }
    }

    /// Unfolds `month` and flips it in place, so the card and the sheet follow.
    func reveal(_ month: CalendarMonth, zone: TimeZone) async throws -> HomeReveal {
        guard let service else { throw HomeModelError.noService }
        let reveal = try await service.reveal(month: month, zone: zone)
        if reveal.revealedAt == nil {
            logger.error("reveal_wrap returned no timestamp for \(month.firstDay.iso, privacy: .public)")
        }
        if let at = reveal.revealedAt, let current = wraps {
            wraps = HomeWraps(months: current.months.map {
                $0.month == month ? HomeMonth(month: $0.month, count: $0.count, revealedAt: at, cover: $0.cover) : $0
            })
        }
        return reveal
    }
}
