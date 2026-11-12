import SwiftUI

/// Report a person or a glyph (App Store guideline 1.2, #838). A native grouped
/// list built from the app's own list chrome: one reason, optional details,
/// and, for a connection, remove-and-block in the same step.
struct ReportSheet: View {
    let target: ReportTarget
    /// Called once the report is filed; `true` when the connection was also
    /// removed and blocked, so the caller can drop it from its list.
    var onSent: (_ blocked: Bool) -> Void = { _ in }

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss
    @State private var model: ReportSheetModel

    init(target: ReportTarget, onSent: @escaping (_ blocked: Bool) -> Void = { _ in }) {
        self.target = target
        self.onSent = onSent
        _model = State(initialValue: ReportSheetModel(target: target))
    }

    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle(target.title)
                .toolbar { toolbar }
                .pebblesScreen()
        }
        .interactiveDismissDisabled(model.phase == .sending)
        .onChange(of: model.phase) { _, phase in
            if phase == .sent { onSent(model.didBlock) }
        }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        if model.phase == .sent {
            ToolbarItem(placement: .confirmationAction) {
                PebbleToolbarButton("Done") { dismiss() }
            }
        } else {
            ToolbarItem(placement: .cancellationAction) {
                PebbleToolbarButton("Cancel") { dismiss() }
                    .disabled(model.phase == .sending)
            }
            ToolbarItem(placement: .confirmationAction) {
                PebbleToolbarButton(action: send) {
                    if model.phase == .sending {
                        ProgressView()
                            .accessibilityLabel(Text("Sending"))
                    } else {
                        Text("Send report")
                    }
                }
                .disabled(!model.canSend)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.phase == .sent {
            ContentUnavailableView {
                Label {
                    Text("Thanks. We'll look at it.")
                } icon: {
                    PbblsGlyphView(.checkCircleDot, .hero)
                }
            }
        } else {
            form
        }
    }

    private var form: some View {
        @Bindable var model = model
        return List {
            Section {
                ForEach(Array(ReportReason.allCases.enumerated()), id: \.element) { index, reason in
                    Button {
                        model.reason = reason
                    } label: {
                        HStack {
                            Text(reason.label)
                                .foregroundStyle(Color.pebbles.onSurface)
                            Spacer(minLength: 0)
                            if model.reason == reason {
                                PbblsGlyphView(.checkmarkDot, .row, style: AnyShapeStyle(Color.pebbles.primary))
                                    .accessibilityHidden(true)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .accessibilityAddTraits(model.reason == reason ? .isSelected : [])
                    .pebblesListRow(position: pebblesRowPosition(index: index, count: ReportReason.allCases.count))
                }
            } header: {
                Text("Why are you reporting?").pebblesSectionHeader()
            }

            Section {
                TextField("What happened?", text: $model.detail, axis: .vertical)
                    .lineLimit(3...8)
                    .onChange(of: model.detail) { _, _ in model.clampDetail() }
                    .pebblesListRow(position: .only)
            } header: {
                Text("Add details (optional)").pebblesSectionHeader()
            }

            if target.offersBlock {
                Section {
                    Toggle(isOn: $model.removeAndBlock) {
                        Text(target.blockLabel)
                    }
                    .tint(Color.pebbles.primary)
                    .pebblesListRow(position: .only)
                } footer: {
                    Text("Blocking also stops them joining again through your invite link.")
                }
            }

            if case .failed(let message) = model.phase {
                Section {
                    Text(message)
                        .foregroundStyle(Color.pebbles.error)
                        .pebblesListRow(position: .only)
                }
            }
        }
        .pebblesList()
        .disabled(model.phase == .sending)
    }

    private func send() {
        let service = ReportService(supabase: supabase)
        Task { await model.submit(using: service.send) }
    }
}

#Preview("Connection") {
    ReportSheet(target: .connection(id: UUID(), name: "Ana"))
        .environment(SupabaseService())
}
