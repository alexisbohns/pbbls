import SwiftUI

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

    /// Full glyph row for the currently-selected `draft.glyphId`. Owned by
    /// the parent sheet (loaded from the detail SELECT on edit, or set from
    /// `onGlyphPicked` after the user picks one). The form renders a
    /// thumbnail + name from it but never fetches.
    let selectedGlyph: Glyph?

    /// Fired when the user picks a glyph from `GlyphPickerSheet`. The parent
    /// is responsible for updating both `selectedGlyph` and `draft.glyphId`
    /// (the form sets `draft.glyphId` via the binding before calling back).
    let onGlyphPicked: (Glyph) -> Void

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
        selectedGlyph: Glyph? = nil,
        onGlyphPicked: @escaping (Glyph) -> Void = { _ in },
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
        self.selectedGlyph = selectedGlyph
        self.onGlyphPicked = onGlyphPicked
        self.showsPhotoSection = showsPhotoSection
        self._photoPickerPresented = photoPickerPresented
        self.isRemovingExistingSnap = isRemovingExistingSnap
        self.onRemoveExistingSnap = onRemoveExistingSnap
        self.formSnap = formSnap
        self.onRetryPending = onRetryPending
        self.onRemovePending = onRemovePending
    }

    var body: some View {
        List {
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
                .tint(Color.accent.primary)
                .pebblesListRow(position: .top)

                TextField("Name", text: $draft.name)
                    .pebblesListRow(position: .middle)

                TextField("Description (optional)", text: $draft.description, axis: .vertical)
                    .lineLimit(1...5)
                    .pebblesListRow(position: .bottom)
            }

            Section {
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
                                .foregroundStyle(Color.system.secondary)
                        }
                        Text("Emotion")
                            .foregroundStyle(Color.system.foreground)
                        Spacer()
                        emotionRowLabel
                            .foregroundStyle(Color.system.secondary)
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
                .pebblesListRow(position: .top)

                Picker("Domain", selection: $draft.domainId) {
                    Text("Choose…").tag(UUID?.none)
                    ForEach(domains) { domain in
                        Text(domain.localizedName).tag(UUID?.some(domain.id))
                    }
                }
                .pickerStyle(.menu)
                .pebblesListRow(position: .middle)

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
                                .foregroundStyle(Color.system.secondary)
                                .accessibilityHidden(true)
                        } else {
                            RoundedRectangle(cornerRadius: 6)
                                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                                .frame(width: 32, height: 32)
                                .foregroundStyle(Color.system.secondary)
                        }
                        Text("Valence")
                            .foregroundStyle(Color.system.foreground)
                        Spacer()
                        if let label = draft.valence?.label {
                            Text(label)
                                .foregroundStyle(Color.system.secondary)
                        } else {
                            Text("Choose…")
                                .foregroundStyle(Color.system.secondary)
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
                .pebblesListRow(position: .bottom)
            } header: {
                Text("Mood").pebblesSectionHeader()
            }

            Section {
                Button {
                    showPicker = true
                } label: {
                    HStack(spacing: 12) {
                        if let glyph = selectedGlyph {
                            GlyphView(case: .default, strokes: glyph.strokes, side: 32)
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
                        } label: {
                            Label("Remove glyph", systemImage: "trash")
                        }
                    }
                }
                .pebblesListRow(position: .only)
            } header: {
                Text("Glyph").pebblesSectionHeader()
            }

            Section {
                SelectedSoulsRow(
                    soulIds: $draft.soulIds,
                    allSouls: souls
                )
                .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                .pebblesListRow(position: .only)
            } header: {
                Text("Souls").pebblesSectionHeader()
            }

            Section {
                Picker("Collection", selection: $draft.collectionId) {
                    Text("None").tag(UUID?.none)
                    ForEach(collections) { collection in
                        Text(collection.name).tag(UUID?.some(collection.id))
                    }
                }
                .pickerStyle(.menu)
                .pebblesListRow(position: .only)
            } header: {
                Text("Optional").pebblesSectionHeader()
            }

            if showsPhotoSection {
                Section {
                    switch formSnap {
                    case .none:
                        Button {
                            photoPickerPresented = true
                        } label: {
                            Label("Add a photo", systemImage: "photo.badge.plus")
                        }
                        .pebblesListRow(position: .only)
                    case .existing(_, let storagePath):
                        ExistingSnapRow(
                            storagePath: storagePath,
                            isRemoving: isRemovingExistingSnap,
                            onRemove: onRemoveExistingSnap
                        )
                        .pebblesListRow(position: .only)
                    case .pending(let snap):
                        AttachedPhotoView(
                            snap: snap,
                            onRetry: onRetryPending,
                            onRemove: onRemovePending
                        )
                        .pebblesListRow(position: .only)
                    }
                } header: {
                    Text("Photo").pebblesSectionHeader()
                }
            }

            if let saveError {
                Section {
                    Text(saveError)
                        .foregroundStyle(.red)
                        .font(.callout)
                        .pebblesListRow(position: .only)
                }
            }
        }
        .pebblesList()
        .sheet(isPresented: $showPicker) {
            GlyphPickerSheet(
                currentGlyphId: draft.glyphId,
                onSelected: { picked in
                    draft.glyphId = picked.id
                    onGlyphPicked(picked)
                }
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
}
