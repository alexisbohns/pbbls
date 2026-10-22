import Foundation
import Observation
import os

/// The held card's page state (#1146 §State model), the page's `@State`. It
/// loads the moment from `v_moments_full` in the page's `.task` and reloads it
/// in place after an edit, a pebble delete or a completion, so the page never
/// swaps to a spinner and back. The fetch and the delete are passed in as
/// closures, as `PathModel`'s are, so the tests need no Supabase.
///
/// Every change made here calls the opener's `onChanged` and bumps
/// `MomentsRevision` from the opener's tab (stack C's contract, which this model
/// took over from the old detail sheet).
@Observable @MainActor
final class HeldCardModel {
    enum Phase: Equatable { case loading, failed, loaded }
    enum DeleteOutcome: Equatable { case closes, reloads, failed, ignored }

    /// D6: a pebble appeared on a moment that was bare.
    struct Completion: Equatable {
        let pebbleId: UUID
    }

    /// The moment and its completion as one value, so a refresh publishes the
    /// swap and the cue in one assignment (Android's lesson: an event raced the swap).
    struct Loaded: Equatable {
        let detail: MomentDetail
        let completion: Completion?
    }

    private(set) var phase: Phase = .loading
    private(set) var loaded: Loaded?
    /// Keeps the card verso up after a print, until the next request.
    private(set) var printedOver = false
    /// The verso heading is announced once after a print, then this goes false.
    private(set) var announcing = false

    var detail: MomentDetail? { loaded?.detail }
    var completion: Completion? { loaded?.completion }
    private(set) var isDeleting = false
    /// Set before the close a delete triggers, so that close never flies (E4).
    private(set) var leaving = false

    @ObservationIgnored let momentId: UUID
    /// Only the latest load applies: a save's reload and a pebble delete's can
    /// run at once, and the slower fetch must not land last.
    @ObservationIgnored private var loadGeneration = 0
    @ObservationIgnored private let origin: AppTab
    @ObservationIgnored private let onChanged: @MainActor () -> Void
    @ObservationIgnored private let logger = Logger(subsystem: "app.pbbls.ios", category: "held-card")

    init(momentId: UUID, origin: AppTab, onChanged: @escaping @MainActor () -> Void) {
        self.momentId = momentId
        self.origin = origin
        self.onChanged = onChanged
    }

    /// The first load and every reload in place. A failed reload keeps the page
    /// it has; a cancelled load (the page left) changes nothing.
    func load(_ fetch: () async throws -> MomentDetail) async {
        loadGeneration += 1
        let generation = loadGeneration
        do {
            let fresh = try await fetch()
            guard !Task.isCancelled, generation == loadGeneration else { return }
            apply(fresh)
            phase = .loaded
        } catch {
            guard !Task.isCancelled, generation == loadGeneration else { return }
            logger.error("held card load failed: \(error.localizedDescription, privacy: .private)")
            if detail == nil { phase = .failed }
        }
    }

    /// A pebble on a moment that was bare is a completion; it survives later
    /// refreshes until shown, and clears if the pebble disappears.
    private func apply(_ fresh: MomentDetail) {
        let wasBare = loaded.map { $0.detail.pebble == nil } ?? false
        var completion = loaded?.completion
        if let pebble = fresh.pebble {
            if wasBare { completion = Completion(pebbleId: pebble.id) }
        } else {
            completion = nil
        }
        loaded = Loaded(detail: fresh, completion: completion)
    }

    /// The frame where the print reached its end (D6): the card is at rest, verso up.
    func printEnded() {
        completionShown()
        announcing = true
    }

    /// A restore found a print under way: land at rest, verso up, without it.
    func cutShort() {
        completionShown()
    }

    func announced() {
        announcing = false
    }

    private func completionShown() {
        if let loaded, loaded.completion != nil {
            self.loaded = Loaded(detail: loaded.detail, completion: nil)
        }
        printedOver = true
    }

    /// A save, a delete or a completion: the opener reloads its list, and the
    /// other tabs catch up through `MomentsRevision`.
    func changed(_ revision: MomentsRevision) {
        onChanged()
        revision.bump(from: origin)
    }

    /// Runs `perform` (`MomentDeletion.perform`). Deleting the moment closes the
    /// page, `leaving` set first; deleting the pebble reloads it as a bare
    /// moment. A second confirm while one runs is ignored.
    func delete(_ option: MomentDeleteOption, target: MomentDeleteTarget, revision: MomentsRevision,
                perform: (MomentDeleteOption, MomentDeleteTarget) async throws -> Void) async -> DeleteOutcome {
        guard !isDeleting else { return .ignored }
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await perform(option, target)
        } catch {
            logger.error("held card delete failed: \(error.localizedDescription, privacy: .private)")
            return .failed
        }
        if target.closesReadPage(after: option) {
            leaving = true
            changed(revision)
            return .closes
        }
        changed(revision)
        return .reloads
    }
}
