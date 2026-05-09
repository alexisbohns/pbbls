import RiveRuntime

/// `RiveViewModel` subclass that exposes a closure fired when the
/// underlying animation finishes its one-shot timeline.
///
/// `pbbls-cairn.riv` is authored as a one-shot. When `RiveView` finishes
/// advancing the timeline it transitions `isPlaying` to false and fires
/// `player(pausedWithModel:)` (see `RiveView.swift` line 419), *not*
/// `player(stoppedWithModel:)` — the latter only fires on an explicit
/// `stop()` call. We override `pausedWithModel` and treat that as the
/// completion signal for the cascade.
final class CairnAnimationViewModel: RiveViewModel {
    /// Fired once when the cairn one-shot finishes. Cleared after the
    /// first invocation so a re-played cairn (e.g. after a `.task`
    /// reset) cannot trigger the cascade twice.
    var onFinished: (() -> Void)?

    @objc override func player(pausedWithModel riveModel: RiveModel?) {
        super.player(pausedWithModel: riveModel)
        let handler = onFinished
        onFinished = nil
        handler?()
    }
}
