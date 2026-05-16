import RiveRuntime
import SwiftUI

/// One cell in the horizontal weeks roll.
///
/// Renders a state-machine-driven cairn animation above the ISO week number.
/// The animation is powered by `pbbls-cairn-states.riv` which exposes:
///   - `isSelected` (state-machine bool input): switches between idle and
///     active states.
///   - `strokeColor` (Data Binding color property): tints the cairn outline.
///
/// Each cell owns its own `RiveViewModel` so the state machines run
/// independently across the visible roll.
struct WeekRollCairnCell: View {
    let entry: WeekRollEntry
    let isFocused: Bool
    let calendar: Calendar
    let onTap: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    // RiveViewModel is an ObservableObject (SDK class); @StateObject keeps it
    // alive for the cell's lifetime and lets it drive re-renders when Rive
    // publishes ObjectWillChange notifications.
    @StateObject private var rvm = RiveViewModel(
        fileName: "pbbls-cairn-states",
        stateMachineName: "State Machine 1"
    )

    // Cached reference to the color property; nil until the auto-bind
    // callback fires (i.e. until the artboard is loaded and bound).
    @State private var colorProperty: RiveDataBindingViewModel.Instance.ColorProperty?

    var body: some View {
        let weekNum = calendar.component(.weekOfYear, from: entry.weekStart)
        Button(action: onTap) {
            VStack(spacing: 4) {
                rvm.view()
                    .frame(width: 56, height: 56)
                    .accessibilityHidden(true)
                Text(verbatim: "\(weekNum)")
                    .font(.ysabeauSemibold(13))
                    .foregroundStyle(isFocused ? Color.accent.primary : Color.system.secondary)
            }
        }
        .buttonStyle(.plain)
        .frame(width: 72)
        .onAppear { configureAutoBind() }
        .onChange(of: isFocused) { _, _ in applyViewModel() }
        .onChange(of: colorScheme) { _, _ in applyViewModel() }
        .accessibilityLabel("Week \(weekNum), \(entry.pebbles.count) pebbles")
    }

    // MARK: - Computed state

    /// The stroke color to feed into Rive:
    ///   - focused → accent (both schemes)
    ///   - unfocused → mutedForeground (both schemes; same shade in light/dark
    ///     so the cairn stays visible on both the white path background and
    ///     the dark theme background)
    private var strokeColor: SwiftUI.Color {
        isFocused ? SwiftUI.Color.accent.primary : SwiftUI.Color.system.secondary
    }

    // MARK: - Data Binding setup

    /// Registers the auto-bind callback on `riveModel` so the SDK creates
    /// and binds the default Data Binding view-model instance when the
    /// artboard loads. The callback:
    ///   1. Caches the `strokeColor` `ColorProperty` (SDK caches it too,
    ///      so subsequent calls with the same path are a no-op lookup).
    ///   2. Applies the initial state immediately.
    ///
    /// `enableAutoBind` is idempotent; calling it again from a re-mount
    /// (unlikely with @StateObject, but defensive) just re-registers.
    private func configureAutoBind() {
        rvm.riveModel?.enableAutoBind { [self] instance in
            colorProperty = instance.colorProperty(fromPath: "strokeColor")
            applyViewModel()
        }
    }

    // MARK: - State application

    /// Pushes the current `isFocused` / `colorScheme` state into Rive.
    ///
    /// (a) State-machine bool input — `setInput(_:value:)` on `RiveViewModel`
    ///     works in 6.19.2 (defined in RiveViewModel.swift:376–377).
    ///
    /// (b) Data Binding color property — `set(red:green:blue:alpha:)` on
    ///     `RiveDataBindingViewModelInstance.ColorProperty` (defined in
    ///     RiveDataBindingViewModelInstanceProperty.h:168–173).
    ///     Accessed via `colorPropertyFromPath("strokeColor")` on the
    ///     auto-bound instance (defined in
    ///     RiveDataBindingViewModelInstance.h:108–109).
    private func applyViewModel() {
        rvm.setInput("isSelected", value: isFocused)
        guard let prop = colorProperty else { return }
        strokeColor.applyToRiveColorProperty(prop, in: colorScheme)
    }
}
