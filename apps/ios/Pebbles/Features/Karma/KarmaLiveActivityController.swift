import ActivityKit
import Foundation
import os

/// Drives the transient karma Live Activity: request → (system auto-expands)
/// → end(.immediate) after ~2.5s. A second earn within the window UPDATES the
/// running activity in place (replace-not-stack) and resets the dismiss timer.
@MainActor
final class KarmaLiveActivityController: KarmaLiveActivityPresenting {
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "karma-activity")
    private var current: Activity<KarmaActivityAttributes>?
    private var dismissTask: Task<Void, Never>?

    /// Tune-on-device. TEMPORARY 30s for diagnosis (was 2500ms) — restore before merge.
    private let visibleDuration: Duration = .seconds(30)

    func present(_ content: KarmaEarnedContent) async -> Bool {
        let enabled = ActivityAuthorizationInfo().areActivitiesEnabled
        let hasCurrent = current != nil
        logger.info("present laEnabled=\(enabled, privacy: .public) hasCurrent=\(hasCurrent, privacy: .public)")
        guard enabled else { return false }

        let state = KarmaActivityAttributes.ContentState(
            amount: content.amount,
            reasonRawValue: content.reason.rawValue
        )

        if let activity = current {
            await activity.update(ActivityContent(state: state, staleDate: nil))
            scheduleDismiss()
            return true
        }

        do {
            let activity = try Activity.request(
                attributes: KarmaActivityAttributes(),
                content: ActivityContent(state: state, staleDate: nil),
                pushType: nil
            )
            current = activity
            // If this logs an id but nothing shows in the notch, the Live
            // Activity was created but the system suppressed it because the app
            // is frontmost (the DI shows a foreground app's own activity only
            // once it's backgrounded).
            let diag = "Activity.request OK id=\(activity.id) state=\(String(describing: activity.activityState))"
            logger.info("\(diag, privacy: .public)")
            scheduleDismiss()
            return true
        } catch {
            logger.error("Activity.request failed: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }

    private func scheduleDismiss() {
        dismissTask?.cancel()
        dismissTask = Task { [weak self, visibleDuration] in
            try? await Task.sleep(for: visibleDuration)
            guard !Task.isCancelled else { return }
            await self?.end()
        }
    }

    private func end() async {
        guard let activity = current else { return }
        // Clear `current` BEFORE awaiting the teardown: a re-earn that
        // interleaves at this suspension point must see no running activity
        // and request a fresh one, rather than update an activity mid-end.
        current = nil
        await activity.end(nil, dismissalPolicy: .immediate)
    }
}
