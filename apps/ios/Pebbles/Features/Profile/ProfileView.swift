import SwiftUI
import os

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

    @State private var karma: KarmaSummary?
    @State private var bounce: BounceSummary?
    @State private var presentedSheet: ProfileSheet?
    @State private var presentedLegalDoc: LegalDoc?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile")

    var body: some View {
        NavigationStack {
            List {
                Section("Stats") {
                    ProfileStatRow(
                        title: "Karma",
                        systemImage: "sparkles",
                        value: karma?.totalKarma
                    ) {
                        presentedSheet = .karma
                    }
                    ProfileStatRow(
                        title: "Bounce",
                        systemImage: "arrow.up.right",
                        value: bounce?.bounceLevel
                    ) {
                        presentedSheet = .bounce
                    }
                }

                Section("Lists") {
                    NavigationLink {
                        CollectionsListView()
                    } label: {
                        Label("Collections", systemImage: "square.stack.3d.up")
                    }
                    NavigationLink {
                        SoulsListView()
                    } label: {
                        Label("Souls", systemImage: "person.2")
                    }
                    NavigationLink {
                        GlyphsListView()
                    } label: {
                        Label("Glyphs", systemImage: "scribble")
                    }
                }

                Section("Legal") {
                    ProfileNavRow(title: "Terms", systemImage: "doc.text") {
                        presentedLegalDoc = .terms
                    }
                    ProfileNavRow(title: "Privacy", systemImage: "lock.shield") {
                        presentedLegalDoc = .privacy
                    }
                }

                Section {
                    Button(role: .destructive) {
                        Task { await supabase.signOut() }
                    } label: {
                        Text("Log out")
                            .frame(maxWidth: .infinity)
                    }
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
        async let karmaResult: KarmaSummary = supabase.client
            .from("v_karma_summary")
            .select("total_karma, pebbles_count")
            .single()
            .execute()
            .value

        async let bounceResult: BounceSummary = supabase.client
            .from("v_bounce")
            .select("bounce_level, active_days")
            .single()
            .execute()
            .value

        do {
            self.karma = try await karmaResult
        } catch {
            logger.error("karma fetch failed: \(error.localizedDescription, privacy: .private)")
        }

        do {
            self.bounce = try await bounceResult
        } catch {
            logger.error("bounce fetch failed: \(error.localizedDescription, privacy: .private)")
        }
    }
}

#Preview {
    ProfileView()
        .environment(SupabaseService())
}
