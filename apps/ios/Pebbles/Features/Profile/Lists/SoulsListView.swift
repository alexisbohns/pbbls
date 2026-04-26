import SwiftUI
import os

struct SoulsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var items: [SoulWithGlyph] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false
    @State private var pendingDeletion: SoulWithGlyph?
    @State private var deleteError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 16)]

    var body: some View {
        content
            .navigationTitle("Souls")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isPresentingCreate = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add soul")
                }
            }
            .task { await load() }
            .sheet(isPresented: $isPresentingCreate) {
                CreateSoulSheet(onCreated: {
                    Task { await load() }
                })
            }
            .confirmationDialog(
                pendingDeletion.map { "Delete \($0.name)?" } ?? "",
                isPresented: Binding(
                    get: { pendingDeletion != nil },
                    set: { if !$0 { pendingDeletion = nil } }
                ),
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { soul in
                Button("Delete", role: .destructive) {
                    Task { await delete(soul) }
                }
                Button("Cancel", role: .cancel) {
                    pendingDeletion = nil
                }
            } message: { _ in
                Text("Linked pebbles stay; only the soul and its links are removed.")
            }
            .alert(
                "Couldn't delete",
                isPresented: Binding(
                    get: { deleteError != nil },
                    set: { if !$0 { deleteError = nil } }
                ),
                presenting: deleteError
            ) { _ in
                Button("OK", role: .cancel) { deleteError = nil }
            } message: { message in
                Text(message)
            }
            .pebblesScreen()
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
        } else if items.isEmpty {
            ContentUnavailableView(
                "No souls yet",
                systemImage: "person.2",
                description: Text("People and beings you tag on your pebbles will appear here.")
            )
        } else {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 16) {
                    ForEach(items) { item in
                        NavigationLink {
                            SoulDetailView(initial: item, onChanged: {
                                Task { await load() }
                            })
                        } label: {
                            SoulGridCell(soul: item)
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button(role: .destructive) {
                                pendingDeletion = item
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                }
                .padding()
            }
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            let result: [SoulWithGlyph] = try await supabase.client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .order("name", ascending: true)
                .execute()
                .value
            self.items = result
        } catch {
            logger.error("souls fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }

    private func delete(_ soul: SoulWithGlyph) async {
        pendingDeletion = nil
        do {
            try await supabase.client
                .from("souls")
                .delete()
                .eq("id", value: soul.id)
                .execute()
            await load()
        } catch {
            logger.error("delete soul failed: \(error.localizedDescription, privacy: .private)")
            deleteError = "Something went wrong. Please try again."
        }
    }
}

#Preview {
    NavigationStack {
        SoulsListView()
            .environment(SupabaseService())
    }
}
