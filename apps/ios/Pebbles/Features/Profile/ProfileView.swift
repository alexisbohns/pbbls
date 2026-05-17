import SwiftUI
import os

private struct ProfileRow: Decodable {
    var displayName: String?
    let createdAt: Date
    var glyphId: UUID?

    enum CodingKeys: String, CodingKey {
        case displayName = "display_name"
        case createdAt   = "created_at"
        case glyphId     = "glyph_id"
    }
}

struct ProfileView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(PathStatsService.self) private var stats
    @Environment(\.dismiss) private var dismiss

    @State private var profile: ProfileRow?
    @State private var glyphStrokes: [GlyphStroke]?
    @State private var isPresentingSettings = false
    @State private var hasLoadedProfile = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile-view")

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.xl) {
                ProfileBanner(
                    displayName: profile?.displayName,
                    memberSince: profile?.createdAt,
                    glyphStrokes: glyphStrokes
                )

                ProfileShortcutsRow()

                ProfileStatsCard(
                    ripple: stats.ripple,
                    assiduity: stats.assiduity,
                    daysPracticed: stats.daysPracticed,
                    pebbles: stats.pebbles,
                    karma: stats.karma
                )

                ProfileCollectionsCard()

                ProfileLabCard()

                ProfileLogoutButton {
                    Task { await supabase.signOut() }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
        }
        .navigationTitle("Profile")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    isPresentingSettings = true
                } label: {
                    Image(systemName: "gear")
                }
                .accessibilityLabel(Text("Settings"))
            }
        }
        .pebblesScreen()
        .task {
            await stats.load()
            await loadProfile()
        }
        .sheet(isPresented: $isPresentingSettings) {
            SettingsSheet(
                initialDisplayName: profile?.displayName ?? "",
                initialGlyphId: profile?.glyphId,
                initialGlyphStrokes: glyphStrokes,
                email: supabase.session?.user.email,
                onSaved: { newName, newGlyph in
                    if var current = profile {
                        current.displayName = newName
                        current.glyphId = newGlyph?.id ?? current.glyphId
                        profile = current
                    }
                    if let strokes = newGlyph?.strokes {
                        glyphStrokes = strokes
                    }
                }
            )
        }
    }

    private func loadProfile() async {
        guard !hasLoadedProfile else { return }
        do {
            let row: ProfileRow = try await supabase.client
                .from("profiles")
                .select("display_name, created_at, glyph_id")
                .single().execute().value
            self.profile = row
            self.hasLoadedProfile = true

            if let glyphId = row.glyphId {
                await loadGlyphStrokes(glyphId)
            }
        } catch {
            logger.error("profile fetch failed: \(error.localizedDescription, privacy: .private)")
            self.hasLoadedProfile = true
        }
    }

    private func loadGlyphStrokes(_ glyphId: UUID) async {
        // GlyphService.list() confirms the column name is "strokes" (a JSONB array
        // decoding into [GlyphStroke]). We follow the same shape here for a
        // single-row fetch by id.
        struct GlyphRow: Decodable { let strokes: [GlyphStroke] }
        do {
            let row: GlyphRow = try await supabase.client
                .from("glyphs")
                .select("strokes")
                .eq("id", value: glyphId.uuidString)
                .single().execute().value
            self.glyphStrokes = row.strokes
        } catch {
            logger.error("glyph fetch failed: \(error.localizedDescription, privacy: .private)")
        }
    }
}

#Preview {
    let supabase = SupabaseService()
    return NavigationStack {
        ProfileView()
            .environment(supabase)
            .environment(PathStatsService(supabase: supabase))
    }
}
