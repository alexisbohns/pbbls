import SwiftUI
import os

private struct ProfileCollectionRow: Decodable, Identifiable {
    let id: UUID
    let name: String
}

struct ProfileCollectionsCard: View {
    @Environment(SupabaseService.self) private var supabase

    @State private var collections: [ProfileCollectionRow] = []
    @State private var hasLoaded = false
    @State private var isPresentingCreateSheet = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile-collections")

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            header

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    if collections.isEmpty && hasLoaded {
                        ProfileCollectionCard(variant: .empty) {
                            isPresentingCreateSheet = true
                        }
                    } else {
                        ForEach(collections) { c in
                            ProfileCollectionCard(variant: .filled(name: c.name)) {
                                // The horizontal cards aren't NavigationLinks; the chevron in
                                // the card header navigates to the full list instead. Tapping
                                // a card from the Profile is a future enhancement.
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.horizontal, -16)
        }
        .padding(16)
        .background(Color.system.background)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay {
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.system.muted, lineWidth: 1)
        }
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
        HStack {
            Text("COLLECTIONS")
                .font(.caption.weight(.semibold))
                .tracking(0.8)
                .foregroundStyle(Color.system.secondary)
            Spacer()
            NavigationLink {
                CollectionsListView()
            } label: {
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.system.secondary)
            }
        }
    }

    private func load() async {
        guard !hasLoaded else { return }
        do {
            let rows: [ProfileCollectionRow] = try await supabase.client
                .from("collections")
                .select("id, name")
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
