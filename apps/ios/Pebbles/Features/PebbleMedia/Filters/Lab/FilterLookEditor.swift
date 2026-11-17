#if DEBUG
import SwiftUI

/// Tune one photo: live preview (hold for the original), sliders, extras, saved looks.
struct FilterLookEditor: View {
    @State private var model: FilterLookModel
    let store: FilterLabStore

    init(photo: LabPhoto, source: CGImage, measures: PhotoMeasures?, store: FilterLabStore) {
        _model = State(initialValue: FilterLookModel(photo: photo, source: source,
                                                     faceCount: measures?.faceCount ?? 0,
                                                     aestheticsBefore: measures?.aesthetics))
        self.store = store
    }

    var body: some View {
        @Bindable var model = model
        VStack(spacing: 0) {
            preview
            List {
                Section { scores } header: { Text(verbatim: "Aesthetics") }
                Section {
                    ForEach(FilterControl.allCases) { slider(for: $0) }
                    Toggle(isOn: $model.params.autoEnhance) { Text(verbatim: "Auto-enhance + red-eye") }
                } header: {
                    Text(verbatim: "Controls (double-tap a name to reset it)")
                }
                Section { looksList } header: { Text(verbatim: "Looks") }
                Section { lookActions(name: $model.lookName) }
            }
        }
        .navigationTitle(Text(verbatim: "Tune"))
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.loadMasks() }
    }

    private var preview: some View {
        Image(decorative: model.showsOriginal ? model.source : (model.preview ?? model.source), scale: 1)
            .resizable()
            .scaledToFit()
            .frame(maxHeight: 340)
            .padding(.vertical, 8)
            .onLongPressGesture(minimumDuration: 0, maximumDistance: 60, perform: {}, onPressingChanged: { pressing in
                model.showsOriginal = pressing
            })
            .accessibilityLabel(Text(verbatim: "Preview. Touch and hold to see the original."))
    }

    @ViewBuilder
    private var scores: some View {
        if model.aestheticsBefore == nil && model.lastAesthetics == nil {
            Text(verbatim: "Needs iOS 18.")
        } else {
            LabeledContent { Text(verbatim: format(model.aestheticsBefore)) } label: { Text(verbatim: "Original") }
            LabeledContent { Text(verbatim: format(model.lastAesthetics)) } label: {
                Text(verbatim: "This look (on save)")
            }
        }
    }

    private func slider(for control: FilterControl) -> some View {
        let value = Binding(get: { model.params[keyPath: control.keyPath] },
                            set: { model.params[keyPath: control.keyPath] = $0 })
        let blocked = control == .refocus && !model.subjectFound
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(verbatim: control.label)
                Spacer()
                Text(verbatim: String(format: "%+.2f", value.wrappedValue))
                    .monospacedDigit()
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            .contentShape(Rectangle())
            .onTapGesture(count: 2) { value.wrappedValue = 0 }
            Slider(value: value, in: control.range)
                .disabled(blocked)
                .accessibilityLabel(Text(verbatim: control.label))
            if blocked {
                Text(verbatim: model.masksReady ? "No subject found in this photo." : "Finding the subject…")
                    .font(.caption)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
    }

    @ViewBuilder
    private var looksList: some View {
        let looks = store.looks(of: model.photo.id)
        if looks.isEmpty {
            Text(verbatim: "No looks saved yet.")
        }
        ForEach(looks) { look in
            Button {
                model.load(look)
            } label: {
                LabeledContent {
                    if look.id == model.editingLookID { Text(verbatim: "editing") }
                } label: {
                    Text(verbatim: "\(look.name) · \(format(look.aesthetics))")
                }
            }
            .swipeActions {
                Button { model.delete(look, from: store) } label: { Text(verbatim: "Delete") }
                Button { model.duplicate(look) } label: { Text(verbatim: "Duplicate") }
            }
        }
    }

    @ViewBuilder
    private func lookActions(name: Binding<String>) -> some View {
        TextField(text: name, prompt: Text(verbatim: "Look name")) { Text(verbatim: "Look name") }
        Button { Task { await model.save(into: store, asNew: true) } } label: { Text(verbatim: "Save as new look") }
            .disabled(model.isSaving)
        if model.editingLookID != nil {
            Button { Task { await model.save(into: store, asNew: false) } } label: {
                Text(verbatim: "Update this look")
            }
                .disabled(model.isSaving)
        }
        Button { model.startNewLook() } label: { Text(verbatim: "Start from neutral") }
        if let error = model.errorMessage {
            Text(verbatim: error).foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
    }

    private func format(_ score: Double?) -> String {
        score.map { String(format: "%+.2f", $0) } ?? "–"
    }
}
#endif
