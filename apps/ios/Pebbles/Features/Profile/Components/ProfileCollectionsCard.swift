import SwiftUI
import os

struct ProfileCollectionsCard: View {
    @Environment(SupabaseService.self) private var supabase

    @State private var collections: [Collection] = []
    @State private var hasLoaded = false
    @State private var isPresentingCreateSheet = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile-collections")

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            header

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    if collections.isEmpty && hasLoaded {
                        Button {
                            isPresentingCreateSheet = true
                        } label: {
                            ProfileCollectionCard(variant: .empty)
                        }
                        .buttonStyle(.plain)
                    } else {
                        ForEach(collections) { collection in
                            NavigationLink {
                                CollectionDetailView(collection: collection, onChanged: {
                                    Task {
                                        hasLoaded = false
                                        await load()
                                    }
                                })
                            } label: {
                                ProfileCollectionCard(variant: .filled(collection: collection))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, Spacing.lg)
            }
            .padding(.horizontal, -Spacing.lg)
        }
        .profileCard()
        .task { await load() }
        .sheet(isPresented: $isPresentingCreateSheet) {
            CreateCollectionSheet(onCreated: {
                Task {
                    hasLoaded = false
                    await load()
                }
            })
        }
    }

    private var header: some View {
        NavigationLink {
            CollectionsListView()
        } label: {
            HStack {
                Text("Collections")
                    .pebblesFont(.cardHeading)
                    .foregroundStyle(Color.system.secondary)
                Spacer()
                Image(systemName: "chevron.right")
                    .pebblesIcon(.md)
                    .foregroundStyle(Color.system.muted)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func load() async {
        guard !hasLoaded else { return }
        do {
            let rows: [Collection] = try await supabase.client
                .from("collections")
                .select("id, name, mode, pebble_count:collection_pebbles(count)")
                .order("created_at", ascending: false)
                .execute().value
            self.collections = rows
            self.hasLoaded = true
        } catch {
            logger.error("collections fetch failed: \(error.localizedDescription, privacy: .private)")
            self.hasLoaded = true
        }
    }
}
