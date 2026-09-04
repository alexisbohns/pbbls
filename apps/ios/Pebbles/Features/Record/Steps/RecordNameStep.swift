import SwiftUI

/// Step 2 — what to call it. Clamped to 40 characters as you type, so the
/// counter can never show an over-limit value and there is no error state to
/// design (D3). The limit is front-end only.
struct RecordNameStep: View {
    let name: String
    let limit: Int
    let onChange: (String) -> Void

    /// The counter stays out of the way until the end is in sight: it fades in
    /// at 15 characters left and counts down to 0.
    private static let countdownFrom = 15

    /// The field owns its text so the clamp can write back into it. Feeding
    /// `TextField` a computed `Binding` that truncates in its setter does not
    /// work: UIKit keeps its own buffer during editing, so the extra characters
    /// stay on screen and only vanish at publish — the field says one thing and
    /// the saved pebble says another.
    @State private var text: String
    @FocusState private var isFocused: Bool

    init(name: String, limit: Int, onChange: @escaping (String) -> Void) {
        self.name = name
        self.limit = limit
        self.onChange = onChange
        _text = State(initialValue: name)
    }

    private var remaining: Int { max(0, limit - text.count) }
    private var showsCountdown: Bool { remaining <= Self.countdownFrom }

    var body: some View {
        VStack(spacing: Spacing.sm) {
            TextField("Name", text: $text, axis: .vertical)
                .lineLimit(1...3)
                .pebblesFont(.nameInputHand)
                .multilineTextAlignment(.center)
                .focused($isFocused)
                .submitLabel(.done)
                .padding(Spacing.md)
                .onChange(of: text) { _, newValue in
                    let clamped = String(newValue.prefix(limit))
                    // Assigning state (not a binding setter) is what actually
                    // rewrites the visible field. Guarded so the re-entrant
                    // onChange converges.
                    if clamped != newValue { text = clamped }
                    onChange(clamped)
                }
                .onChange(of: name) { _, newName in
                    // Resync if the model's value moves on its own (a resumed
                    // or reset draft). A no-op while typing, since the model
                    // holds exactly what we just sent it.
                    if newName != text { text = newName }
                }

            // Always laid out, only faded — otherwise the field jumps when the
            // counter appears mid-typing.
            Text(verbatim: "\(remaining)")
                .pebblesFont(.subhead)
                .monospacedDigit()
                .foregroundStyle(
                    remaining == 0 ? Color.accent.primary : Color.system.secondary
                )
                .opacity(showsCountdown ? 1 : 0)
                .animation(.easeInOut(duration: 0.2), value: showsCountdown)
                .accessibilityHidden(!showsCountdown)
                .accessibilityLabel(Text("\(remaining) characters left"))
        }
        .onAppear { isFocused = true }
    }
}
