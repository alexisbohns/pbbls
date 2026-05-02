import SwiftUI
import os

/// Multi-select sheet for tagging a pebble with souls. Shown from
/// `SelectedSoulsRow` inside `PebbleFormView`. Loads its own souls via
/// `SupabaseService` so the form doesn't need to refetch when an inline
/// `+ New` insert happens.
///
/// Tap a cell to toggle. Done applies the selection. Cancel (or
/// swipe-down) discards. The `+ New` tile presents `CreateSoulSheet`;
/// the inserted soul is appended to the local list and pre-selected.
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

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 16)]

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Choose souls")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") {
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
                LazyVGrid(columns: columns, spacing: 16) {
                    NewSoulTile { isPresentingCreate = true }
                    ForEach(souls) { soul in
                        SoulSelectableCell(
                            soul: soul,
                            isSelected: selection.contains(soul.id),
                            onToggle: { toggle(soul.id) }
                        )
                    }
                }
                .padding()

                if souls.isEmpty {
                    Text("Add the first soul to tag this pebble with")
                        .font(.callout)
                        .foregroundStyle(Color.pebblesMutedForeground)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                        .padding(.top, 8)
                }
            }
        }
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
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
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

/// Trailing tile in the picker grid that opens `CreateSoulSheet`.
/// Visually matches a soul cell: same 96pt square frame, dashed border,
/// `person.badge.plus` icon, and "+ New soul" label below.
private struct NewSoulTile: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(
                        Color.pebblesMutedForeground,
                        style: StrokeStyle(lineWidth: 1.5, dash: [4])
                    )
                    .frame(width: 96, height: 96)
                    .overlay {
                        Image(systemName: "person.badge.plus")
                            .font(.title2)
                            .foregroundStyle(Color.pebblesMutedForeground)
                    }
                Text("+ New soul")
                    .font(.callout)
                    .foregroundStyle(Color.pebblesMutedForeground)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Create a new soul")
    }
}
