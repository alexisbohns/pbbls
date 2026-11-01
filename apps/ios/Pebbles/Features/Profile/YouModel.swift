import Foundation
import os
import Supabase

/// The caller's own `profiles` row, as You and Settings read it.
struct YouProfile: Decodable, Equatable {
    var displayName: String?
    let createdAt: Date
    var glyphId: UUID?
    var handle: String?
    var publicProfile: Bool

    enum CodingKeys: String, CodingKey {
        case displayName   = "display_name"
        case createdAt     = "created_at"
        case glyphId       = "glyph_id"
        case handle        = "handle"
        case publicProfile = "public_profile"
    }
}

/// You's reads, behind a protocol so the fixture and the tests use fakes
/// (the `HomeServicing` pattern, #1382 D16).
protocol YouServicing: Sendable {
    /// The caller's own row (RLS returns only it).
    func profile() async throws -> YouProfile
    /// One glyph's strokes, for the header's pentagon.
    func glyphStrokes(id: UUID) async throws -> [GlyphStroke]
}

struct YouService: YouServicing {
    let client: SupabaseClient

    func profile() async throws -> YouProfile {
        try await client
            .from("profiles")
            .select("display_name, created_at, glyph_id, handle, public_profile")
            .single().execute().value
    }

    func glyphStrokes(id: UUID) async throws -> [GlyphStroke] {
        // The `strokes` column is a JSONB array decoding into [GlyphStroke] (GlyphService.list()).
        struct GlyphRow: Decodable { let strokes: [GlyphStroke] }
        let row: GlyphRow = try await client
            .from("glyphs")
            .select("strokes")
            .eq("id", value: id.uuidString)
            .single().execute().value
        return row.strokes
    }
}

/// The figures You shows, as one snapshot: `PathStatsService` feeds it in the
/// app, the fixture in the references. Nil is "not loaded yet".
struct YouStats: Equatable {
    var rippleLevel: Int?
    var activeToday = false
    var daysPracticed: Int?
    var pebbles: Int?
    var karma: Int?
    var glyphsCarved: Int?
    var glyphsOwned: Int?
}

extension YouStats {
    @MainActor init(_ stats: PathStatsService) {
        self.init(rippleLevel: stats.ripple?.rippleLevel, activeToday: stats.ripple?.activeToday ?? false,
                  daysPracticed: stats.daysPracticed, pebbles: stats.pebbles, karma: stats.karma,
                  glyphsCarved: stats.glyphsCarved, glyphsOwned: stats.glyphsOwned)
    }
}

/// You's state (#1382 D16): the profile row and its glyph, moved out of
/// `ProfileView` so a fixture can draw the page.
@Observable @MainActor final class YouModel {
    private(set) var profile: YouProfile?
    private(set) var glyphStrokes: [GlyphStroke]?
    private(set) var hasLoadedProfile = false
    @ObservationIgnored var service: (any YouServicing)?
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "you.model")

    /// Once per model: a failed read logs and leaves the name and glyph empty.
    func loadProfile() async {
        guard !hasLoadedProfile, let service else { return }
        defer { hasLoadedProfile = true }
        do {
            let row = try await service.profile()
            profile = row
            if let glyphId = row.glyphId { await loadGlyph(glyphId) }
        } catch {
            logger.error("profile fetch failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    private func loadGlyph(_ id: UUID) async {
        guard let service else { return }
        do {
            glyphStrokes = try await service.glyphStrokes(id: id)
        } catch {
            logger.error("glyph fetch failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    /// What Settings saved, applied without a read.
    func applySettings(name: String?, glyph: (id: UUID, strokes: [GlyphStroke])?, handle: String?,
                       isPublic: Bool) {
        if var current = profile {
            current.displayName = name
            current.glyphId = glyph?.id ?? current.glyphId
            current.handle = handle
            current.publicProfile = isPublic
            profile = current
        }
        if let strokes = glyph?.strokes { glyphStrokes = strokes }
    }
}
