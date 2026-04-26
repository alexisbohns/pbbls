import SwiftUI
import os

/// Sheet for editing a soul. Name + glyph row, save/cancel toolbar.
/// UPDATE goes directly to `public.souls` — RLS scopes to the owner.
struct EditSoulSheet: View {
    let original: SoulWithGlyph
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var draft: SoulDraft
    @State private var isSaving = false
    @State private var saveError: String?
    @State private var isPresentingPicker = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    init(original: SoulWithGlyph, onSaved: @escaping () -> Void) {
        self.original = original
        self.onSaved = onSaved
        self._draft = State(initialValue: SoulDraft(from: original))
    }

    private var canSave: Bool {
        guard draft.isValid else { return false }
        let trimmed = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let nameChanged = trimmed != original.name
        let glyphChanged = draft.glyphId != original.glyphId
        return nameChanged || glyphChanged
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $draft.name)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled(false)
                }
                Section("Glyph") {
                    GlyphRow(
                        glyph: draft.currentGlyph,
                        onTap: { isPresentingPicker = true }
                    )
                }
                if let saveError {
                    Section {
                        Text(saveError)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Edit soul")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        Button("Save") {
                            Task { await save() }
                        }
                        .disabled(!canSave)
                    }
                }
            }
            .pebblesScreen()
            .sheet(isPresented: $isPresentingPicker) {
                GlyphPickerSheet(
                    currentGlyphId: draft.glyphId,
                    onSelected: { selected in
                        if let selected {
                            draft.glyphId = selected
                            Task { await loadGlyph(id: selected) }
                        }
                    }
                )
            }
        }
    }

    private func loadGlyph(id: UUID) async {
        do {
            let fetched: Glyph = try await supabase.client
                .from("glyphs")
                .select("id, name, strokes, view_box")
                .eq("id", value: id)
                .single()
                .execute()
                .value
            draft.currentGlyph = fetched
        } catch {
            logger.error("edit soul: load glyph failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    private func save() async {
        guard canSave else { return }
        isSaving = true
        saveError = nil
        do {
            let payload = SoulUpdatePayload(
                name: draft.name.trimmingCharacters(in: .whitespacesAndNewlines),
                glyphId: draft.glyphId
            )
            try await supabase.client
                .from("souls")
                .update(payload)
                .eq("id", value: original.id)
                .execute()
            onSaved()
            dismiss()
        } catch {
            logger.error("update soul failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save your changes. Please try again."
            isSaving = false
        }
    }
}

/// Row shared with `CreateSoulSheet`. Kept duplicated as `private` here because
/// only two callers exist; lift to `Profile/Components/` if a third appears.
private struct GlyphRow: View {
    let glyph: Glyph?
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                if let glyph {
                    GlyphThumbnail(strokes: glyph.strokes, side: 32)
                        .accessibilityHidden(true)
                } else {
                    RoundedRectangle(cornerRadius: 6)
                        .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                        .frame(width: 32, height: 32)
                        .foregroundStyle(.secondary)
                }
                Text("Tap to choose")
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    EditSoulSheet(
        original: SoulWithGlyph(
            id: UUID(),
            name: "Preview",
            glyphId: SystemGlyph.default,
            glyph: Glyph(
                id: SystemGlyph.default,
                name: nil,
                strokes: [],
                viewBox: "0 0 200 200",
                userId: nil
            )
        ),
        onSaved: {}
    )
    .environment(SupabaseService())
}
