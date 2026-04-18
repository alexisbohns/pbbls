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

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "glyph-carve")

    private var service: GlyphService { GlyphService(supabase: supabase) }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("New glyph")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { cancelTapped() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        if isSaving {
                            ProgressView()
                        } else {
                            Button("Save") {
                                Task { await save() }
                            }
                            .disabled(strokes.isEmpty)
                        }
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

            GlyphCanvasView(
                committedStrokes: strokes,
                onStrokeCommit: { stroke in strokes.append(stroke) }
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
                }
                .disabled(strokes.isEmpty)

                Button(role: .destructive) {
                    strokes.removeAll()
                } label: {
                    Label("Clear", systemImage: "trash")
                }
                .disabled(strokes.isEmpty)
            }
            .buttonStyle(.bordered)

            Spacer(minLength: 0)
        }
        .padding()
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
            let glyph = try await service.create(strokes: strokes)
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
