import SwiftUI
import os

/// The pebble `Form` body, shared by `CreatePebbleSheet` and `EditPebbleSheet`.
///
/// Pure UI: takes a binding to a `PebbleDraft` and the four reference lists.
/// Knows nothing about Supabase, save/insert semantics, or which sheet is
/// presenting it. The optional `saveError` row is rendered inline so both
/// sheets can surface save failures the same way.
// swiftlint:disable:next type_body_length
struct PebbleFormView: View {
    @Binding var draft: PebbleDraft
    let domains: [Domain]
    let souls: [SoulWithGlyph]
    let collections: [PebbleCollection]
    let saveError: String?
    var renderSvg: String?
    var strokeColor: String?
    var renderHeight: CGFloat = 260

    /// When true, render the Photo section. Off by default so callers that
    /// don't yet support photo flows opt out explicitly.
    let showsPhotoSection: Bool

    @Binding var photoPickerPresented: Bool

    /// Provided by `EditPebbleSheet` to gate the remove button while the RPC
    /// is in flight. `CreatePebbleSheet` always passes `false`.
    let isRemovingExistingSnap: Bool

    /// Triggered when the user taps remove on an `.existing` snap row.
    /// `EditPebbleSheet` runs the eager `delete_pebble_media` RPC + Storage
    /// cleanup. `CreatePebbleSheet` never sees `.existing`, so it passes a
    /// no-op closure.
    let onRemoveExistingSnap: () -> Void

    /// Current snap state. The parent owns this via a `SnapUploadCoordinator`
    /// and re-passes it on every render.
    let formSnap: FormSnap?

    /// Tapped when the user hits retry on a `.pending(.failed)` chip.
    let onRetryPending: () -> Void

    /// Tapped when the user hits remove on a `.pending` chip (any state).
    let onRemovePending: () -> Void

    @State private var showPicker = false
    @State private var showValencePicker = false
    @State private var showEmotionPicker = false
    @State private var selectedGlyph: Glyph?

    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes

    init(
        draft: Binding<PebbleDraft>,
        domains: [Domain],
        souls: [SoulWithGlyph],
        collections: [PebbleCollection],
        saveError: String?,
        renderSvg: String? = nil,
        strokeColor: String? = nil,
        renderHeight: CGFloat = 260,
        showsPhotoSection: Bool = false,
        photoPickerPresented: Binding<Bool> = .constant(false),
        isRemovingExistingSnap: Bool = false,
        onRemoveExistingSnap: @escaping () -> Void = {},
        formSnap: FormSnap? = nil,
        onRetryPending: @escaping () -> Void = {},
        onRemovePending: @escaping () -> Void = {}
    ) {
        self._draft = draft
        self.domains = domains
        self.souls = souls
        self.collections = collections
        self.saveError = saveError
        self.renderSvg = renderSvg
        self.strokeColor = strokeColor
        self.renderHeight = renderHeight
        self.showsPhotoSection = showsPhotoSection
        self._photoPickerPresented = photoPickerPresented
        self.isRemovingExistingSnap = isRemovingExistingSnap
        self.onRemoveExistingSnap = onRemoveExistingSnap
        self.formSnap = formSnap
        self.onRetryPending = onRetryPending
        self.onRemovePending = onRemovePending
    }

    var body: some View {
        Form {
            if let svg = renderSvg {
                PebbleRenderView(svg: svg, strokeColor: strokeColor)
                    .frame(maxWidth: .infinity)
                    .frame(height: renderHeight)
                    .padding(.vertical)
                    // Form rows add insets and a card background; strip both so the artwork spans edge-to-edge.
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            }

            Section {
                DatePicker(
                    "When",
                    selection: $draft.happenedAt,
                    displayedComponents: [.date, .hourAndMinute]
                )
                .tint(Color.pebblesAccent)
                .listRowBackground(Color.pebblesListRow)

                TextField("Name", text: $draft.name)
                    .listRowBackground(Color.pebblesListRow)

                TextField("Description (optional)", text: $draft.description, axis: .vertical)
                    .lineLimit(1...5)
                    .listRowBackground(Color.pebblesListRow)
            }

            Section("Mood") {
                Button {
                    showEmotionPicker = true
                } label: {
                    HStack(spacing: 12) {
                        if let id = draft.emotionId, let row = palettes.byEmotionId[id] {
                            Text(row.emoji)
                                .font(.system(size: 28))
                                .frame(width: 32, height: 32)
                                .accessibilityHidden(true)
                        } else {
                            RoundedRectangle(cornerRadius: 6)
                                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                                .frame(width: 32, height: 32)
                                .foregroundStyle(Color.pebblesMutedForeground)
                        }
                        Text("Emotion")
                            .foregroundStyle(Color.pebblesForeground)
                        Spacer()
                        emotionRowLabel
                            .foregroundStyle(Color.pebblesMutedForeground)
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                            .accessibilityHidden(true)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Emotion")
                .accessibilityValue(
                    draft.emotionId
                        .flatMap { palettes.byEmotionId[$0] }
                        .map { Text(verbatim: $0.localizedName) }
                        ?? Text("Choose")
                )
                .listRowBackground(Color.pebblesListRow)

                Picker("Domain", selection: $draft.domainId) {
                    Text("Choose…").tag(UUID?.none)
                    ForEach(domains) { domain in
                        Text(domain.localizedName).tag(UUID?.some(domain.id))
                    }
                }
                .listRowBackground(Color.pebblesListRow)

                Button {
                    showValencePicker = true
                } label: {
                    HStack(spacing: 12) {
                        if let valence = draft.valence {
                            Image(valence.assetName)
                                .renderingMode(.template)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 32, height: 32)
                                .foregroundStyle(Color.pebblesMutedForeground)
                                .accessibilityHidden(true)
                        } else {
                            RoundedRectangle(cornerRadius: 6)
                                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                                .frame(width: 32, height: 32)
                                .foregroundStyle(Color.pebblesMutedForeground)
                        }
                        Text("Valence")
                            .foregroundStyle(Color.pebblesForeground)
                        Spacer()
                        if let label = draft.valence?.label {
                            Text(label)
                                .foregroundStyle(Color.pebblesMutedForeground)
                        } else {
                            Text("Choose…")
                                .foregroundStyle(Color.pebblesMutedForeground)
                        }
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                            .accessibilityHidden(true)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Valence")
                .accessibilityValue(
                    draft.valence.map { Text($0.label) } ?? Text("Choose")
                )
                .listRowBackground(Color.pebblesListRow)
            }

            Section("Glyph") {
                Button {
                    showPicker = true
                } label: {
                    HStack(spacing: 12) {
                        if let glyph = selectedGlyph {
                            GlyphThumbnail(strokes: glyph.strokes, side: 32)
                                .accessibilityHidden(true)
                        } else {
                            RoundedRectangle(cornerRadius: 6)
                                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                                .frame(width: 32, height: 32)
                                .foregroundStyle(.secondary)
                        }
                        Text(glyphRowLabel)
                            .foregroundStyle(selectedGlyph == nil ? .secondary : .primary)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                    }
                }
                .buttonStyle(.plain)
                .contextMenu {
                    if draft.glyphId != nil {
                        Button(role: .destructive) {
                            draft.glyphId = nil
                            selectedGlyph = nil
                        } label: {
                            Label("Remove glyph", systemImage: "trash")
                        }
                    }
                }
                .listRowBackground(Color.pebblesListRow)
            }

            Section("Souls") {
                SelectedSoulsRow(
                    soulIds: $draft.soulIds,
                    allSouls: souls
                )
                .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                .listRowBackground(Color.clear)
            }

            Section("Optional") {
                Picker("Collection", selection: $draft.collectionId) {
                    Text("None").tag(UUID?.none)
                    ForEach(collections) { collection in
                        Text(collection.name).tag(UUID?.some(collection.id))
                    }
                }
                .listRowBackground(Color.pebblesListRow)
            }

            if showsPhotoSection {
                Section("Photo") {
                    switch formSnap {
                    case .none:
                        Button {
                            photoPickerPresented = true
                        } label: {
                            Label("Add a photo", systemImage: "photo.badge.plus")
                        }
                        .listRowBackground(Color.pebblesListRow)
                    case .existing(_, let storagePath):
                        ExistingSnapRow(
                            storagePath: storagePath,
                            isRemoving: isRemovingExistingSnap,
                            onRemove: onRemoveExistingSnap
                        )
                        .listRowBackground(Color.pebblesListRow)
                    case .pending(let snap):
                        AttachedPhotoView(
                            snap: snap,
                            onRetry: onRetryPending,
                            onRemove: onRemovePending
                        )
                        .listRowBackground(Color.pebblesListRow)
                    }
                }
            }

            if let saveError {
                Section {
                    Text(saveError)
                        .foregroundStyle(.red)
                        .font(.callout)
                        .listRowBackground(Color.pebblesListRow)
                }
            }
        }
        .sheet(isPresented: $showPicker) {
            GlyphPickerSheet(
                currentGlyphId: draft.glyphId,
                onSelected: { glyphId in draft.glyphId = glyphId }
            )
        }
        .sheet(isPresented: $showValencePicker) {
            ValencePickerSheet(
                currentValence: draft.valence,
                onSelected: { picked in draft.valence = picked }
            )
        }
        .sheet(isPresented: $showEmotionPicker) {
            EmotionPickerSheet(
                currentEmotionId: draft.emotionId,
                valence: draft.valence,
                onSelected: { picked in draft.emotionId = picked }
            )
        }
        .task(id: draft.glyphId) { await loadSelectedGlyph() }
    }

    /// Right-hand label on the Emotion row. Returns a `Text` (not a string)
    /// to avoid double-localization: `localizedName` is already resolved
    /// against the catalog at runtime, so we wrap it with `Text(verbatim:)`.
    private var emotionRowLabel: Text {
        if let id = draft.emotionId, let row = palettes.byEmotionId[id] {
            return Text(verbatim: row.localizedName)
        }
        return Text("Choose…")
    }

    private var glyphRowLabel: String {
        if draft.glyphId == nil { return "Carve or pick a glyph" }
        if let name = selectedGlyph?.name { return name }
        return "Untitled glyph"
    }

    private func loadSelectedGlyph() async {
        guard let id = draft.glyphId else {
            selectedGlyph = nil
            return
        }
        if selectedGlyph?.id == id { return }
        do {
            let fetched: Glyph = try await supabase.client
                .from("glyphs")
                .select("id, name, strokes, view_box")
                .eq("id", value: id)
                .single()
                .execute()
                .value
            self.selectedGlyph = fetched
        } catch {
            // Non-fatal: the row renders without a thumbnail until the refetch works.
            Logger(subsystem: "app.pbbls.ios", category: "pebble-form")
                .warning("glyph fetch for preview failed: \(error.localizedDescription, privacy: .private)")
        }
    }
}
