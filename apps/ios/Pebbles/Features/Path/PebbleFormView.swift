import SwiftUI
import os

/// The pebble `Form` body, shared by `CreatePebbleSheet` and `EditPebbleSheet`.
///
/// Pure UI: takes a binding to a `PebbleDraft` and the four reference lists.
/// Knows nothing about Supabase, save/insert semantics, or which sheet is
/// presenting it. The optional `saveError` row is rendered inline so both
/// sheets can surface save failures the same way.
struct PebbleFormView: View {
    @Binding var draft: PebbleDraft
    let emotions: [Emotion]
    let domains: [Domain]
    let souls: [Soul]
    let collections: [PebbleCollection]
    let saveError: String?
    var renderSvg: String?
    var strokeColor: String?

    @State private var showPicker = false
    @State private var showValencePicker = false
    @State private var selectedGlyph: Glyph?

    @Environment(SupabaseService.self) private var supabase

    var body: some View {
        Form {
            if let svg = renderSvg {
                PebbleRenderView(svg: svg, strokeColor: strokeColor)
                    .frame(maxWidth: .infinity)
                    .frame(height: 260)
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

                TextField("Name", text: $draft.name)

                TextField("Description (optional)", text: $draft.description, axis: .vertical)
                    .lineLimit(1...5)
            }

            Section("Mood") {
                Picker("Emotion", selection: $draft.emotionId) {
                    Text("Choose…").tag(UUID?.none)
                    ForEach(emotions) { emotion in
                        Text(emotion.name).tag(UUID?.some(emotion.id))
                    }
                }

                Picker("Domain", selection: $draft.domainId) {
                    Text("Choose…").tag(UUID?.none)
                    ForEach(domains) { domain in
                        Text(domain.name).tag(UUID?.some(domain.id))
                    }
                }

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
                        Text(draft.valence?.label ?? "Choose…")
                            .foregroundStyle(Color.pebblesMutedForeground)
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                            .accessibilityHidden(true)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Valence")
                .accessibilityValue(draft.valence?.label ?? "Choose")
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
            }

            Section("Optional") {
                Picker("Soul", selection: $draft.soulId) {
                    Text("None").tag(UUID?.none)
                    ForEach(souls) { soul in
                        Text(soul.name).tag(UUID?.some(soul.id))
                    }
                }

                Picker("Collection", selection: $draft.collectionId) {
                    Text("None").tag(UUID?.none)
                    ForEach(collections) { collection in
                        Text(collection.name).tag(UUID?.some(collection.id))
                    }
                }
            }

            Section("Privacy") {
                Picker("Privacy", selection: $draft.visibility) {
                    ForEach(Visibility.allCases) { visibility in
                        Text(visibility.label).tag(visibility)
                    }
                }
                .pickerStyle(.segmented)
            }

            if let saveError {
                Section {
                    Text(saveError)
                        .foregroundStyle(.red)
                        .font(.callout)
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
        .task(id: draft.glyphId) { await loadSelectedGlyph() }
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
