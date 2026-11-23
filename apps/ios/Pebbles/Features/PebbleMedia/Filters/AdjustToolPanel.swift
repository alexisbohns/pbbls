import SwiftUI

/// The Adjust tool (spec 2026-11-23 "Adjust"): the lab's controls as a scrolling row
/// of knobs, offsets on top of the filter. Each shows the person's own offset, so it
/// starts at 0 whatever the preset.
struct AdjustToolPanel: View {
    @Bindable var model: PhotoLookModel

    var body: some View {
        VStack(spacing: Spacing.xs) {
            // Above the row, so it never moves the knobs.
            if let note = refocusNote {
                Text(note)
                    .pebblesFont(.captionEmphasized)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 0) {
                    ForEach(AdjustControl.allCases) { control in
                        LookKnob(label: control.label, value: binding(for: control), range: control.range,
                                 defaultValue: 0, isEnabled: isEnabled(control))
                            // A quarter of the row, as on Filter: the two tools' knobs
                            // sit in the same four places, and a page is four knobs.
                            .containerRelativeFrame(.horizontal, count: 4, spacing: 0)
                    }
                }
                .padding(.vertical, Spacing.sm)
                .scrollTargetLayout()
            }
            // Snaps so a knob, not part of one, sits on the inset.
            .scrollTargetBehavior(.viewAligned)
            // Edge to edge, so the whole strip scrolls; the first knob lines up with the inset.
            .contentMargins(.horizontal, Spacing.lg, for: .scrollContent)
            .padding(.horizontal, -Spacing.lg)
        }
        // Chrome, not content: it stops growing before the knobs outgrow the panel.
        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
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
