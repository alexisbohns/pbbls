import SwiftUI

/// The Adjust tool (spec 2026-11-23 "Adjust"): the lab's sliders as offsets on top of
/// the filter. Each shows the person's own offset, so it starts at 0 whatever the preset.
struct AdjustToolPanel: View {
    @Bindable var model: PhotoLookModel

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                ForEach(AdjustControl.allCases) { control in
                    LookSlider(label: control.label, value: binding(for: control), range: control.range,
                               defaultValue: 0, readout: .signed, isEnabled: isEnabled(control))
                    if control == .refocus, let note = refocusNote {
                        Text(note)
                            .pebblesFont(.meta)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .padding(.vertical, Spacing.sm)
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
