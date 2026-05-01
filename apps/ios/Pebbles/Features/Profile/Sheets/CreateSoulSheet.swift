import SwiftUI
import os

/// Sheet for creating a new soul. Name + glyph row, save/cancel toolbar.
/// INSERT goes directly to `public.souls` — RLS scopes to the current user.
/// `glyph_id` is initialised to the system default; the user can swap it
/// via `GlyphPickerSheet` (which itself can carve a fresh glyph).
struct CreateSoulSheet: View {
    let onCreated: (SoulWithGlyph) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var draft = SoulDraft()
    @State private var isSaving = false
    @State private var saveError: String?
    @State private var isPresentingPicker = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

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
            .navigationTitle("New soul")
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
                        .disabled(!draft.isValid)
                    }
                }
            }
            .pebblesScreen()
            .task { await loadDefaultGlyph() }
            .sheet(isPresented: $isPresentingPicker) {
                GlyphPickerSheet(
                    currentGlyphId: draft.glyphId,
                    onSelected: { selected in
                        // Picker returns the chosen glyph's id. Update draft
                        // and refetch the glyph so the row's thumbnail
                        // re-renders without waiting for a list reload.
                        if let selected {
                            draft.glyphId = selected
                            Task { await loadGlyph(id: selected) }
                        }
                    }
                )
            }
        }
    }

    private func loadDefaultGlyph() async {
        // Idempotent: only fetch if we still hold the system default and
        // haven't already loaded its strokes.
        guard draft.glyphId == SystemGlyph.default,
              draft.currentGlyph?.id != SystemGlyph.default else { return }
        await loadGlyph(id: SystemGlyph.default)
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
            logger.error("create soul: load glyph failed: \(error.localizedDescription, privacy: .private)")
            // Leave currentGlyph as-is; the empty thumbnail still works as a tap target.
        }
    }

    private func save() async {
        guard draft.isValid else { return }
        guard let userId = supabase.session?.user.id else {
            logger.error("create soul: no session")
            saveError = "You're signed out. Please sign in again."
            return
        }
        isSaving = true
        saveError = nil
        do {
            let payload = SoulInsertPayload(
                userId: userId,
                name: draft.name.trimmingCharacters(in: .whitespacesAndNewlines),
                glyphId: draft.glyphId
            )
            let inserted: SoulWithGlyph = try await supabase.client
                .from("souls")
                .insert(payload)
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .single()
                .execute()
                .value
            onCreated(inserted)
            dismiss()
        } catch {
            logger.error("create soul failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save the soul. Please try again."
            isSaving = false
        }
    }
}

/// Row used by both create and edit soul sheets. Shows the current glyph
/// thumbnail (or a dashed placeholder when not yet loaded) + label + chevron.
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
    CreateSoulSheet(onCreated: { _ in })
        .environment(SupabaseService())
}
