import SwiftUI

/// The moment page (record-flow D5, Ledger treatment): the thumbnail with its
/// upload state and "Change photo", the NAME field (focused only when there
/// is no photo), the WHEN chip, A FEW WORDS with a counter and the "+1 karma
/// as a pebble" caption. The buttons live in the scaffold.
struct RecordMomentStep: View {
    let model: CaptureFlowModel
    let snap: FormSnap?
    let blockedMessage: String?
    let onChangePhoto: () -> Void
    let onRetry: () -> Void
    let onRemove: () -> Void
    /// Reference renders only: ImageRenderer draws neither ScrollView content
    /// nor TextFields, so a static page draws each field's text as a `Text`.
    var drawsStaticFields = false

    /// The fields own their text so the clamp can write back into them: a
    /// computed `Binding` that truncates in its setter leaves the extra
    /// characters on screen (see `RecordNameStep`).
    @State private var name: String
    @State private var words: String
    @FocusState private var focus: Field?

    private enum Field { case name, words }

    /// The counter fades in once this many characters are left.
    private static let countdownFrom = 40

    init(model: CaptureFlowModel, snap: FormSnap?, blockedMessage: String?,
         onChangePhoto: @escaping () -> Void, onRetry: @escaping () -> Void, onRemove: @escaping () -> Void,
         drawsStaticFields: Bool = false) {
        self.model = model
        self.drawsStaticFields = drawsStaticFields
        self.snap = snap
        self.blockedMessage = blockedMessage
        self.onChangePhoto = onChangePhoto
        self.onRetry = onRetry
        self.onRemove = onRemove
        _name = State(initialValue: model.draft.name)
        _words = State(initialValue: model.draft.description)
    }

    private var wordsRemaining: Int { max(0, CaptureFlowModel.descriptionLimit - words.count) }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            photoRow
            field("NAME") {
                if drawsStaticFields {
                    staticField(name, placeholder: "Name").pebblesFont(.nameInputHand)
                } else {
                    TextField("Name", text: $name, axis: .vertical)
                        .lineLimit(1...2)
                        .pebblesFont(.nameInputHand)
                        .focused($focus, equals: .name)
                        .submitLabel(.next)
                        .onSubmit { focus = .words }
                        .onChange(of: name) { _, value in
                            let clamped = String(value.prefix(CaptureFlowModel.nameLimit))
                            if clamped != value { name = clamped }
                            model.setName(clamped)
                        }
                        .onChange(of: model.draft.name) { _, value in if value != name { name = value } }
                }
            }
            field("WHEN") {
                RecordWhenChip(
                    happenedAt: Binding(get: { model.draft.happenedAt }, set: { model.setHappenedAt($0) }),
                    source: model.whenSource
                )
            }
            field("A FEW WORDS") {
                VStack(alignment: .trailing, spacing: Spacing.xs) {
                    if drawsStaticFields {
                        staticField(words, placeholder: "What happened?").pebblesFont(.body)
                    } else {
                        TextField("What happened?", text: $words, axis: .vertical)
                            .lineLimit(3...6)
                            .pebblesFont(.body)
                            .focused($focus, equals: .words)
                            .onChange(of: words) { _, value in
                                let clamped = String(value.prefix(CaptureFlowModel.descriptionLimit))
                                if clamped != value { words = clamped }
                                model.setDescription(clamped)
                            }
                            .onChange(of: model.draft.description) { _, value in if value != words { words = value } }
                    }
                    HStack {
                        Label { Text("+1 karma as a pebble") } icon: {
                            PbblsIconImage(.karmaDot, size: 14, relativeTo: .caption,
                                           style: AnyShapeStyle(Color.pebbles.primary))
                        }
                        .pebblesFont(.captionEmphasized)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        Spacer()
                        Text(verbatim: "\(wordsRemaining)")
                            .pebblesFont(.captionEmphasized)
                            .monospacedDigit()
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                            .opacity(wordsRemaining <= Self.countdownFrom ? 1 : 0)
                            .accessibilityHidden(wordsRemaining > Self.countdownFrom)
                            .accessibilityLabel(Text("\(wordsRemaining) characters left"))
                    }
                }
            }
            if let blockedMessage {
                Text(verbatim: blockedMessage)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            if let error = model.saveError {
                Text(verbatim: error)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.pebbles.error)
            }
        }
        .onAppear { if snap == nil { focus = .name } }
    }

    /// The text a field holds, or its placeholder in the placeholder colour.
    @ViewBuilder
    private func staticField(_ text: String, placeholder: LocalizedStringResource) -> some View {
        if text.isEmpty {
            Text(placeholder).foregroundStyle(Color.pebbles.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            Text(verbatim: text).frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func field<Content: View>(_ label: LocalizedStringResource,
                                      @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs + 2) {
            Text(label)
                .pebblesFont(.meta)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            content()
                .padding(Spacing.md)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.pebbles.surfaceContainerLow)
                )
        }
    }

    // MARK: - Photo

    @ViewBuilder
    private var photoRow: some View {
        switch snap {
        case .pending(let attached):
            HStack(spacing: Spacing.md) {
                thumbnail(attached)
                VStack(alignment: .leading, spacing: 2) {
                    stateLabel(attached.state)
                    Button("Change photo", action: onChangePhoto)
                        .pebblesFont(.subheadEmphasized)
                        .foregroundStyle(Color.pebbles.primary)
                }
                Spacer(minLength: 0)
                if attached.state == .failed {
                    Button("Retry", action: onRetry).pebblesFont(.subhead)
                }
                Button(action: onRemove) {
                    Image(systemName: "xmark")
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Remove photo")
            }
        case .existing:
            // Unreachable from Capture: a restored photo is re-seeded as a
            // pending upload (see `CaptureFlowView.restoreIfNeeded`).
            EmptyView()
        case .none:
            Button(action: onChangePhoto) {
                Label { Text("Add a photo") } icon: { PbblsIconImage(.camera, size: 20, relativeTo: .subheadline) }
                    .pebblesFont(.subheadEmphasized)
                    .foregroundStyle(Color.pebbles.primary)
            }
            .buttonStyle(.plain)
        }
    }

    /// The upload ring is a static 270° arc over the thumbnail. A restored
    /// snap has no thumbnail bytes: the slot stays an empty rounded square.
    private func thumbnail(_ attached: AttachedSnap) -> some View {
        ZStack {
            if let image = UIImage(data: attached.localThumb) {
                Image(uiImage: image).resizable().scaledToFill()
            }
            if attached.state == .uploading {
                Circle().trim(from: 0, to: 0.75)
                    .stroke(Color.pebbles.onPrimary, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    .frame(width: 28, height: 28)
                    .rotationEffect(.degrees(-90))
            }
        }
        .frame(width: 72, height: 72)
        .clipShape(RoundedRectangle(cornerRadius: Spacing.md, style: .continuous))
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private func stateLabel(_ state: AttachedSnap.UploadState) -> some View {
        switch state {
        case .uploading:
            Text("Uploading…").pebblesFont(.subhead).foregroundStyle(Color.pebbles.onSurfaceVariant)
        case .uploaded:
            Text("Ready").pebblesFont(.subhead).foregroundStyle(Color.pebbles.onSurfaceVariant)
        case .failed:
            Text("Upload failed").pebblesFont(.subhead).foregroundStyle(Color.pebbles.error)
        }
    }
}
