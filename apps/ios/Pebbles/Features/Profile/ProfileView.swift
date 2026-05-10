import SwiftUI

/// Discriminator for which explainer sheet is currently presented.
/// Driving sheets by an optional enum is the idiomatic SwiftUI pattern —
/// it guarantees a single sheet presentation per state transition.
private enum ProfileSheet: String, Identifiable {
    case karma
    case bounce
    var id: String { rawValue }
}

struct ProfileView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(PathStatsService.self) private var stats

    @State private var presentedSheet: ProfileSheet?
    @State private var presentedLegalDoc: LegalDoc?

    var body: some View {
        NavigationStack {
            List {
                Section("Stats") {
                    ProfileStatRow(
                        title: "Karma",
                        systemImage: "sparkles",
                        value: stats.karma
                    ) {
                        presentedSheet = .karma
                    }
                    .listRowBackground(Color.pebblesListRow)
                    ProfileStatRow(
                        title: "Bounce",
                        systemImage: "arrow.up.right",
                        value: stats.bounce
                    ) {
                        presentedSheet = .bounce
                    }
                    .listRowBackground(Color.pebblesListRow)
                }

                Section("Lists") {
                    NavigationLink {
                        CollectionsListView()
                    } label: {
                        Label("Collections", systemImage: "square.stack.3d.up")
                    }
                    .listRowBackground(Color.pebblesListRow)
                    NavigationLink {
                        SoulsListView()
                    } label: {
                        Label("Souls", systemImage: "person.2")
                    }
                    .listRowBackground(Color.pebblesListRow)
                    NavigationLink {
                        GlyphsListView()
                    } label: {
                        Label("Glyphs", systemImage: "scribble")
                    }
                    .listRowBackground(Color.pebblesListRow)
                }

                Section("Legal") {
                    ProfileNavRow(title: "Terms", systemImage: "doc.text") {
                        presentedLegalDoc = .terms
                    }
                    .listRowBackground(Color.pebblesListRow)
                    ProfileNavRow(title: "Privacy", systemImage: "lock.shield") {
                        presentedLegalDoc = .privacy
                    }
                    .listRowBackground(Color.pebblesListRow)
                }

                Section {
                    Button(role: .destructive) {
                        Task { await supabase.signOut() }
                    } label: {
                        Text("Log out")
                            .frame(maxWidth: .infinity)
                    }
                    .listRowBackground(Color.pebblesListRow)
                }
            }
            .navigationTitle("Profile")
            .pebblesScreen()
            .task { await loadStats() }
            .sheet(item: $presentedSheet) { sheet in
                switch sheet {
                case .karma:  KarmaExplainerSheet()
                case .bounce: BounceExplainerSheet()
                }
            }
            .sheet(item: $presentedLegalDoc) { doc in
                LegalDocumentSheet(url: doc.url)
                    .ignoresSafeArea()
            }
        }
    }

    private func loadStats() async {
        await stats.load()
    }
}

#Preview {
    ProfileView()
        .environment(SupabaseService())
}
