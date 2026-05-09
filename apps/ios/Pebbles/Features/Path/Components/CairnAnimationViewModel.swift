import RiveRuntime

/// `RiveViewModel` subclass that exposes a closure fired when the
/// underlying animation reaches its stopped state. The bundled
/// `pbbls-cairn.riv` is authored as a one-shot, so
/// `player(stoppedWithModel:)` is invoked exactly once when the cairn
/// finishes — `WeekSectionHeader` uses that signal to hand control back
/// to `PathView`, which then cascades the rest of the first week's
/// content into view.
final class CairnAnimationViewModel: RiveViewModel {
    /// Fired once when the cairn animation transitions to `stopped`.
    /// Cleared after the first invocation so a re-played cairn (e.g.
    /// after a `.task` reset) cannot trigger the cascade twice.
    var onStopped: (() -> Void)?

    @objc override func player(stoppedWithModel riveModel: RiveModel?) {
        super.player(stoppedWithModel: riveModel)
        let handler = onStopped
        onStopped = nil
        handler?()
    }
}
