import Observation

/// Cross-tab "a moment changed" signal (#903 D5).
///
/// Bumpers: `HeldCardModel` on every save, delete and completion made on the
/// held card, from the tab that opened it (#1146), the `.momentDeleteDialog`
/// presenters, and Capture's `onMomentChanged`. Reader: `PathModel`, which
/// reloads when another tab bumped. People and Collections detail views
/// already reload through their own callbacks.
@Observable @MainActor
final class MomentsRevision {
    private(set) var value = 0
    private(set) var lastOrigin: AppTab?

    func bump(from origin: AppTab) {
        lastOrigin = origin
        value += 1
    }

    /// True when the latest change was made somewhere other than `tab`.
    func changedOutside(_ tab: AppTab) -> Bool {
        guard let lastOrigin else { return false }
        return lastOrigin != tab
    }
}
