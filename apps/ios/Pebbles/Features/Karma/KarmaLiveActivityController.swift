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

    /// Tune-on-device.
    private let visibleDuration: Duration = .milliseconds(2500)

    func present(_ content: KarmaEarnedContent) async -> Bool {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return false }

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
            current = try Activity.request(
                attributes: KarmaActivityAttributes(),
                content: ActivityContent(state: state, staleDate: nil),
                pushType: nil
            )
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
