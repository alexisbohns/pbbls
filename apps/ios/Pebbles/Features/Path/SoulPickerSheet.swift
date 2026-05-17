import SwiftUI
import os

/// Multi-select sheet for tagging a pebble with souls. Shown from
/// `SelectedSoulsRow` inside `PebbleFormView`. Loads its own souls via
/// `SupabaseService` so the form doesn't need to refetch when an inline
/// `+ New` insert happens.
///
/// Selection rule (see issue #459):
/// - If no soul is currently selected, all rows render as `.default`.
/// - As soon as one or more souls are selected, selected rows render as
///   `.selected` and every other soul renders as `.unselected`.
/// - The `.create` tile is always rendered the same; selection does not
///   affect it.
struct SoulPickerSheet: View {
    let currentSelection: [UUID]
    let onConfirm: ([UUID]) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var souls: [SoulWithGlyph] = []
    @State private var selection: Set<UUID> = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-form.souls")

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: Spacing.lg)]

    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle("Choose souls")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        PebbleToolbarButton("Cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        PebbleToolbarButton("Done") {
                            onConfirm(Array(selection))
                            dismiss()
                        }
                    }
                }
                .pebblesScreen()
                .task { await load() }
                .sheet(isPresented: $isPresentingCreate) {
                    CreateSoulSheet { inserted in
                        souls.append(inserted)
                        selection.insert(inserted.id)
                    }
                }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load souls",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    Text("All my souls")
                        .pebblesFont(.cardHeading)
                        .foregroundStyle(Color.system.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    LazyVGrid(columns: columns, spacing: Spacing.lg) {
                        SoulItem(case: .create, soul: nil, count: nil) {
                            isPresentingCreate = true
                        }
                        ForEach(souls) { soul in
                            SoulItem(
                                case: itemCase(for: soul.id),
                                soul: soul,
                                count: soul.pebblesCount
                            ) {
                                toggle(soul.id)
                            }
                        }
                    }

                    if souls.isEmpty {
                        Text("Add the first soul to tag this pebble with")
                            .pebblesFont(.callout)
                            .foregroundStyle(Color.system.secondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(Spacing.lg)
            }
        }
    }

    private func itemCase(for id: UUID) -> SoulItem.Case {
        if selection.isEmpty { return .default }
        return selection.contains(id) ? .selected : .unselected
    }

    private func toggle(_ id: UUID) {
        if selection.contains(id) {
            selection.remove(id)
        } else {
            selection.insert(id)
        }
    }

    private func load() async {
        selection = Set(currentSelection)
        isLoading = true
        loadError = nil
        do {
            let result: [SoulWithGlyph] = try await supabase.client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)")
                .order("name", ascending: true)
                .execute()
                .value
            self.souls = result
        } catch {
            logger.error("souls fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}
