#if DEBUG
import SwiftUI
import os

/// Tune one photo. Decodes the photo off the main thread, then hands over to the editor
/// with one model that lives as long as this screen.
struct FilterLookEditor: View {
    let photoID: UUID
    let measures: PhotoMeasures?
    let store: FilterLabStore
    @State private var model: FilterLookModel?
    @State private var failed = false
    @Environment(\.dismiss) private var dismiss
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "filter-lab-editor")

    var body: some View {
        Group {
            if let model {
                FilterLookEditorContent(model: model, store: store)
            } else if failed {
                Text(verbatim: "This photo is missing.")
            } else {
                ProgressView()
            }
        }
        .navigationTitle(Text(verbatim: "Tune"))
        .navigationBarTitleDisplayMode(.inline)
        // Hiding the system back button turns off the swipe back, which iOS 26 starts from
        // anywhere on the screen: every slider drag was popping the editor.
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button { dismiss() } label: { Text(verbatim: "Photos") }
            }
        }
        .task { await load() }
    }

    private func load() async {
        guard model == nil else { return }
        guard let photo = store.document.photos.first(where: { $0.id == photoID }) else {
            logger.error("lab photo not in the document")
            failed = true
            return
        }
        let url = store.root.appending(path: photo.file)
        let image = await Task.detached(priority: .userInitiated) { FilterLabStore.loadImage(at: url) }.value
        guard let image else {
            failed = true
            return
        }
        model = FilterLookModel(photo: photo, source: image, faceCount: measures?.faceCount ?? 0,
                                aestheticsBefore: measures?.aesthetics)
    }
}

/// Live preview (hold for the original), sliders, extras, saved looks.
private struct FilterLookEditorContent: View {
    @Bindable var model: FilterLookModel
    let store: FilterLabStore
    @FocusState private var nameFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            preview
            List {
                Section { scores } header: { Text(verbatim: "Aesthetics") }
                Section {
                    ForEach(FilterControl.allCases) { slider(for: $0) }
                    Toggle(isOn: $model.params.autoEnhance) { Text(verbatim: "Auto-enhance + red-eye") }
                } header: {
                    Text(verbatim: "Controls (double-tap a slider to reset it)")
                }
                Section { looksList } header: { Text(verbatim: "Looks") }
                Section { lookActions }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .task { await model.loadMasks() }
    }

    private var preview: some View {
        let image = model.showsOriginal ? model.source : (model.preview ?? model.source)
        return Image(image, scale: 1, label: Text(verbatim: "Preview. Touch and hold to see the original."))
            .resizable()
            .scaledToFit()
            .frame(maxHeight: nameFocused ? 120 : 340)
            .padding(.vertical, 8)
            .animation(.easeOut(duration: 0.2), value: nameFocused)
            .onLongPressGesture(minimumDuration: 0, maximumDistance: 60, perform: {}, onPressingChanged: { pressing in
                model.showsOriginal = pressing
            })
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
                // Simultaneous, so the double-tap resets without stealing the drag.
                .simultaneousGesture(TapGesture(count: 2).onEnded { value.wrappedValue = 0 })
                .accessibilityLabel(Text(verbatim: control.label))
                .accessibilityAction(named: Text(verbatim: "Reset")) { value.wrappedValue = 0 }
            if blocked {
                Text(verbatim: model.masksReady ? "No subject found in this photo." : "Finding the subject…")
                    .font(.caption)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
        // Wider than the list's default inset, so a drag never starts at the screen edge.
        .listRowInsets(EdgeInsets(top: 8, leading: 36, bottom: 8, trailing: 36))
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
            .swipeActions(allowsFullSwipe: false) {
                Button { model.duplicate(look) } label: { Text(verbatim: "Duplicate") }
                Button { model.delete(look, from: store) } label: { Text(verbatim: "Delete") }
            }
        }
    }

    @ViewBuilder
    private var lookActions: some View {
        TextField(text: $model.lookName, prompt: Text(verbatim: "Look name")) { Text(verbatim: "Look name") }
            .focused($nameFocused)
        Button { Task { await model.save(into: store, asNew: true) } } label: { Text(verbatim: "Save as new look") }
            .disabled(!canSave)
        if model.editingLookID != nil {
            Button { Task { await model.save(into: store, asNew: false) } } label: {
                Text(verbatim: "Update this look")
            }
            .disabled(!canSave)
        }
        Button { model.startNewLook() } label: { Text(verbatim: "Start from neutral") }
        if let error = model.errorMessage {
            Text(verbatim: error).foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
    }

    /// Saving waits for the masks so the score matches what refocus and auto-enhance show.
    private var canSave: Bool { model.masksReady && !model.isSaving }

    private func format(_ score: Double?) -> String {
        score.map { String(format: "%+.2f", $0) } ?? "–"
    }
}
#endif
