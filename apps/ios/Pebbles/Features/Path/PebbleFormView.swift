import SwiftUI

/// The pebble `Form` body for Edit pebble: emotion, domain, valence, souls,
/// collection. The moment's fields edit in `EditMomentSheet`.
///
/// Pure UI: takes a binding to a `CompleteDraft` and the reference lists.
/// Knows nothing about Supabase or save semantics. The optional `saveError`
/// row is rendered inline.
struct PebbleFormView: View {
    @Binding var draft: CompleteDraft
    let domains: [Domain]
    let souls: [SoulWithGlyph]
    let collections: [PebbleCollection]
    let saveError: String?

    @State private var showValencePicker = false
    @State private var showEmotionPicker = false

    @Environment(EmotionPaletteService.self) private var palettes

    var body: some View {
        List {
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
                                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        }
                        Text("Emotion")
                            .foregroundStyle(Color.pebbles.onSurface)
                        Spacer()
                        emotionRowLabel
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
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
                                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                                .accessibilityHidden(true)
                        } else {
                            RoundedRectangle(cornerRadius: 6)
                                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                                .frame(width: 32, height: 32)
                                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        }
                        Text("Valence")
                            .foregroundStyle(Color.pebbles.onSurface)
                        Spacer()
                        if let label = draft.valence?.label {
                            Text(label)
                                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        } else {
                            Text("Choose…")
                                .foregroundStyle(Color.pebbles.onSurfaceVariant)
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

            if let saveError {
                Section {
                    Text(saveError)
                        .foregroundStyle(Color.pebbles.error)
                        .font(.callout)
                        .pebblesListRow(position: .only)
                }
            }
        }
        .pebblesList()
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
}
