import SwiftUI

/// The Adjust tool (spec 2026-11-23 "Adjust"): the lab's controls as a scrolling row
/// of knobs, offsets on top of the filter. Each shows the person's own offset, so it
/// starts at 0 whatever the preset.
struct AdjustToolPanel: View {
    @Bindable var model: PhotoLookModel

    /// Wide enough for the longest name ("Highlights", « Hautes lumières » shrinks to fit).
    private static let knobWidth: CGFloat = 80

    var body: some View {
        VStack(spacing: Spacing.xs) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 0) {
                    ForEach(AdjustControl.allCases) { control in
                        LookKnob(label: control.label, value: binding(for: control), range: control.range,
                                 defaultValue: 0, isEnabled: isEnabled(control))
                            .frame(width: Self.knobWidth)
                    }
                }
                .padding(.vertical, Spacing.sm)
            }
            .scrollClipDisabled()
            if let note = refocusNote {
                Text(note)
                    .pebblesFont(.captionEmphasized)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
    }

    private func binding(for control: AdjustControl) -> Binding<Double> {
        Binding(get: { model.look.adjust[keyPath: control.keyPath] },
                set: { model.look.adjust[keyPath: control.keyPath] = $0 })
    }

    /// Refocus stays movable until the search finished without a subject.
    private func isEnabled(_ control: AdjustControl) -> Bool {
        control != .refocus || model.masksState != .ready || model.subjectFound
    }

    private var refocusNote: LocalizedStringResource? {
        switch model.masksState {
        case .notLoaded: nil
        case .loading: "Finding the subject…"
        case .ready: model.subjectFound ? nil : "No subject found in this photo."
        }
    }
}
