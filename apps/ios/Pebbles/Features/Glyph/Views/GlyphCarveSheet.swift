import SwiftUI
import os

/// Full-screen cover for carving a new glyph. Presented from
/// `GlyphPickerSheet` (during pebble record/edit) and from `GlyphsListView`
/// (from the profile page).
///
/// Full-screen cover — not a sheet — so the canvas can't be dismissed by an
/// accidental downward stroke. User exits via Cancel/Save only.
struct GlyphCarveSheet: View {
    let onSaved: (Glyph) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var strokes: [GlyphStroke] = []
    @State private var isSaving = false
    @State private var saveError: String?
    @State private var showDiscardAlert = false
    @State private var name: String = ""
    @FocusState private var nameFieldFocused: Bool

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "glyph-carve")

    private var service: GlyphService { GlyphService(supabase: supabase) }

    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle("New glyph")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        PebbleToolbarButton("Cancel") { cancelTapped() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        if isSaving {
                            ProgressView()
                        } else {
                            PebbleToolbarButton("Save") {
                                Task { await save() }
                            }
                            .disabled(strokes.isEmpty)
                        }
                    }
                    ToolbarItemGroup(placement: .keyboard) {
                        Spacer()
                        Button("Done") { nameFieldFocused = false }
                    }
                }
                .pebblesScreen()
                .alert("Discard your glyph?", isPresented: $showDiscardAlert) {
                    Button("Keep editing", role: .cancel) {}
                    Button("Discard", role: .destructive) { dismiss() }
                }
        }
    }

    @ViewBuilder
    private var content: some View {
        VStack(spacing: 24) {
            Spacer(minLength: 0)

            TextField(
                "",
                text: $name,
                prompt: Text("Name (optional)").foregroundStyle(Color.system.muted)
            )
            .pebblesFont(.title)
            .foregroundStyle(Color.system.foreground)
            .multilineTextAlignment(.center)
            .textInputAutocapitalization(.words)
            .submitLabel(.done)
            .focused($nameFieldFocused)
            .onSubmit { nameFieldFocused = false }
            .accessibilityLabel("Glyph name")

            GlyphCanvasView(
                committedStrokes: strokes,
                onStrokeCommit: { stroke in strokes.append(stroke) },
                strokeColor: Color.accent.primary
            )
            .overlay(
                RoundedRectangle(cornerRadius: Spacing.xxl, style: .continuous)
                    .stroke(Color.system.muted, lineWidth: 1)
            )

            if let saveError {
                Text(saveError)
                    .foregroundStyle(.red)
                    .font(.callout)
            }

            HStack(spacing: 24) {
                Button {
                    if !strokes.isEmpty { strokes.removeLast() }
                } label: {
                    Label("Undo", systemImage: "arrow.uturn.backward")
                        .foregroundStyle(Color.accent.primary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Capsule().fill(Color.accent.surface))
                }
                .buttonStyle(.plain)
                .disabled(strokes.isEmpty)

                Button(role: .destructive) {
                    strokes.removeAll()
                } label: {
                    Label("Clear", systemImage: "trash")
                        .foregroundStyle(Color.accent.primary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Capsule().fill(Color.accent.surface))
                }
                .buttonStyle(.plain)
                .disabled(strokes.isEmpty)
            }

            Spacer(minLength: 0)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        .onTapGesture { nameFieldFocused = false }
    }

    private func cancelTapped() {
        if strokes.isEmpty {
            dismiss()
        } else {
            showDiscardAlert = true
        }
    }

    private func save() async {
        guard !strokes.isEmpty else { return }
        isSaving = true
        saveError = nil
        do {
            let glyph = try await service.create(strokes: strokes, name: name)
            onSaved(glyph)
            dismiss()
        } catch {
            logger.error("glyph create failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = "Couldn't save your glyph. Please try again."
            self.isSaving = false
        }
    }
}

#Preview {
    GlyphCarveSheet(onSaved: { _ in })
        .environment(SupabaseService())
}
