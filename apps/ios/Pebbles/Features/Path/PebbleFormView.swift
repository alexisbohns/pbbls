import SwiftUI

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
    var renderSvg: String? = nil

    var body: some View {
        Form {
            if let svg = renderSvg {
                PebbleRenderView(svg: svg)
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

                Picker("Valence", selection: $draft.valence) {
                    Text("Choose…").tag(Valence?.none)
                    ForEach(Valence.allCases) { valence in
                        Text(valence.label).tag(Valence?.some(valence))
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
    }
}
