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
/// takes over from `PebbleDetailSheet`).
@Observable @MainActor
final class HeldCardModel {
    enum Phase: Equatable { case loading, failed, loaded }
    enum DeleteOutcome: Equatable { case closes, reloads, failed, ignored }

    private(set) var phase: Phase = .loading
    private(set) var detail: MomentDetail?
    private(set) var isDeleting = false
    /// Set before the close a delete triggers, so that close never flies (E4).
    private(set) var leaving = false

    @ObservationIgnored let momentId: UUID
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
        do {
            let fresh = try await fetch()
            guard !Task.isCancelled else { return }
            apply(fresh)
            phase = .loaded
        } catch {
            guard !Task.isCancelled else { return }
            logger.error("held card load failed: \(error.localizedDescription, privacy: .private)")
            if detail == nil { phase = .failed }
        }
    }

    private func apply(_ fresh: MomentDetail) {
        detail = fresh
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
