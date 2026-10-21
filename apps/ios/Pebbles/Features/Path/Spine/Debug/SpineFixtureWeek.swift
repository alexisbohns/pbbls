#if DEBUG
import CoreGraphics
import Foundation

/// The Spine's fixture week (#1130), a port of `apps/web/lib/seed/spine-fixture-week.ts`:
/// Monday 5 to Sunday 11 October 2026 in Europe/Paris, today Sunday 11 at 23:00.
/// It takes every branch the product takes: a still Friday, Bruno's uncarved
/// stone, a fourteen-person picnic, an untitled photo, a named bare moment set
/// as type and an untitled bare photo. Ids are fixed UUIDs (moment n is
/// `…-8000-0000000000nn`), and each pebble keeps its moment's id, as backfilled
/// rows do. The palettes are the web sandbox's `CATEGORY_PALETTES`.
enum SpineFixtureWeek {
    static let calendar: Calendar = {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris") ?? .gmt
        return calendar
    }()
    static let today = date("2026-10-11T23:00:00+02:00")
    static var weekStart: Date { WeekRollBuilder.weekStart(for: today, calendar: calendar) }

    // MARK: - Souls

    private static let soulTable: [(key: String, name: String, mark: String)] = [
        ("mara", "Mara", "partner"), ("elio", "Elio", "family"), ("nadia", "Nadia", "friends"),
        ("jules", "Jules", "hobbies"), ("bruno", "Bruno", "travel"), ("ana", "Ana", "friends"),
        ("bea", "Béa", "hobbies"), ("cyril", "Cyril", "identity"), ("dora", "Dora", "travel"),
        ("ezra", "Ezra", "family"), ("felix", "Félix", "partner"), ("gaby", "Gaby", "friends"),
        ("hugo", "Hugo", "hobbies"), ("ines", "Inès", "travel")
    ]
    private static let markNames = ["identity", "travel", "friends", "family", "hobbies", "partner"]

    private static func soul(_ key: String) -> PathSoul {
        let index = soulTable.firstIndex { $0.key == key } ?? 0
        let mark = markNames.firstIndex(of: soulTable[index].mark) ?? 0
        return PathSoul(id: id(0x9000, index + 1), name: soulTable[index].name, glyphId: id(0xA000, mark + 1))
    }

    /// Lowercase soul id → name, as `PathModel` builds it from the reference cache.
    static var soulNames: [String: String] {
        Dictionary(uniqueKeysWithValues: soulTable.map { row in
            let entry = Self.soul(row.key)
            return (entry.id.uuidString.lowercased(), entry.name)
        })
    }

    /// Lowercase soul id → its glyph's strokes.
    static var soulGlyphs: [String: [GlyphStroke]] {
        Dictionary(uniqueKeysWithValues: soulTable.map { row in
            (Self.soul(row.key).id.uuidString.lowercased(), SpineFixtureMarks.strokes[row.mark] ?? [])
        })
    }

    // MARK: - Emotions and palettes

    /// primary, secondary, light, surface, shaded, dark: `CATEGORY_PALETTES`.
    private static let categories: [String: [String]] = [
        "fear": ["#7B5E99FF", "#AE91CCFF", "#F2EFF5FF", "#7B5E991A", "#56426BFF", "#19131FFF"],
        "sadness": ["#59658AFF", "#8C98BDFF", "#EEF0F3FF", "#59658A1A", "#3E4761FF", "#12141CFF"],
        "pride": ["#A9478AFF", "#EA91CEFF", "#F6EDF3FF", "#A9478A1A", "#763261FF", "#220E1CFF"],
        "joy": ["#A15C08FF", "#CF8C39FF", "#FAF6EAFF", "#A15C081A", "#714006FF", "#201202FF"],
        "peace": ["#487C5AFF", "#80BF96FF", "#EDF2EEFF", "#487C5A1A", "#32573FFF", "#0E1912FF"],
        "anger": ["#8E4242FF", "#C17575FF", "#F4ECECFF", "#8E42421A", "#632E2EFF", "#1C0D0DFF"]
    ]
    private static let emotionTable: [(slug: String, name: String, category: String)] = [
        ("proud", "Proud", "pride"), ("joyful", "Joyful", "joy"), ("angry", "Angry", "anger"),
        ("scared", "Scared", "fear"), ("peaceful", "Peaceful", "peace"), ("sad", "Sad", "sadness")
    ]

    private static func emotion(_ slug: String) -> EmotionRef {
        let index = emotionTable.firstIndex { $0.slug == slug } ?? 0
        return EmotionRef(id: id(0xB000, index + 1), slug: slug, name: emotionTable[index].name)
    }

    static let palettes: [UUID: EmotionPalette] = Dictionary(uniqueKeysWithValues: emotionTable.compactMap { row in
        guard let hex = categories[row.category], let palette = EmotionPalette(
            primaryHex: hex[0], secondaryHex: hex[1], lightHex: hex[2], surfaceHex: hex[3],
            darkHex: hex[5], shadedHex: hex[4]
        ) else { return nil }
        return (Self.emotion(row.slug).id, palette)
    })

    static func palette(for moment: MomentSummary) -> EmotionPalette? {
        moment.pebble?.emotion.flatMap { palettes[$0.id] }
    }

    // MARK: - Photos

    /// Snap path → the sandbox file and its ratio (1:1, 3:2, 3:2, 16:9, 2:3).
    static let photos: [String: (file: String, ratio: CGFloat)] = [
        "fixture/snap-flag-march.jpg": ("flag-march", 1),
        "fixture/snap-forest-crew.jpg": ("forest-crew", 3 / 2),
        "fixture/snap-fountain.jpg": ("fountain", 3 / 2),
        "fixture/snap-rooftop-group.jpg": ("rooftop-group", 16 / 9),
        "fixture/snap-seawall-portrait.jpg": ("seawall-portrait", 2 / 3)
    ]

    // MARK: - Moments

    private struct Spec {
        let n: Int
        let name: String
        let at: String
        var photo: String?
        var pebble: (intensity: Int, positiveness: Int, emotion: String, souls: [String], glyph: String?)?
    }

    private static let neighbours = ["ana", "bea", "cyril", "dora", "ezra", "felix", "gaby", "hugo", "ines"]

    private static let specs: [Spec] = [
        Spec(n: 1, name: "Flag march with Nadia", at: "05T10:15", photo: "flag-march.jpg",
             pebble: (3, 1, "proud", ["nadia"], "friends")),
        Spec(n: 2, name: "Elio lost his first tooth", at: "05T19:40",
             pebble: (2, 1, "joyful", ["elio", "mara"], "family")),
        Spec(n: 3, name: "Rent went up again", at: "06T08:02", pebble: (2, -1, "angry", [], "identity")),
        Spec(n: 4, name: "Forest walk, all of us", at: "06T16:30", photo: "forest-crew.jpg",
             pebble: (3, 1, "peaceful", ["mara", "elio", "bruno"], "travel")),
        Spec(n: 5, name: "Couldn’t sleep", at: "07T03:10", pebble: (1, -1, "scared", [], "identity")),
        Spec(n: 6, name: "Coffee by the fountain", at: "07T12:05", photo: "fountain.jpg",
             pebble: (1, 0, "peaceful", ["jules"], "friends")),
        Spec(n: 7, name: "Rooftop birthday", at: "08T21:00", photo: "rooftop-group.jpg",
             pebble: (3, 1, "joyful", ["nadia", "jules", "mara"], "hobbies")),
        Spec(n: 12, name: "Picnic on the square, the whole street", at: "08T13:10",
             pebble: (2, 1, "joyful", ["mara", "elio", "nadia", "jules", "bruno"] + neighbours, "friends")),
        Spec(n: 8, name: "Said no to the extra project", at: "10T09:20", pebble: (2, 0, "proud", [], "identity")),
        Spec(n: 9, name: "Mara on the seawall", at: "10T17:45", photo: "seawall-portrait.jpg",
             pebble: (2, 1, "joyful", ["mara"], "partner")),
        Spec(n: 13, name: "", at: "10T19:05", photo: "fountain.jpg", pebble: (1, 0, "peaceful", ["mara"], "partner")),
        Spec(n: 10, name: "Grandma’s call went quiet", at: "11T14:00", pebble: (2, -1, "sad", [], "family")),
        Spec(n: 11, name: "Bruno asleep on my feet", at: "11T22:30", pebble: (1, 0, "peaceful", ["bruno"], nil)),
        // Not pebbles yet (#1050): blank stones, one set as type, one on a card.
        Spec(n: 14, name: "Bread from the corner bakery", at: "07T18:20"),
        Spec(n: 15, name: "", at: "11T09:10", photo: "forest-crew.jpg")
    ]

    static let moments: [MomentSummary] = specs.map { Self.moment($0) }

    private static func moment(_ spec: Spec) -> MomentSummary {
        let momentId = id(0x8000, spec.n)
        let happened = date("2026-10-\(spec.at):00+02:00")
        let pebble = spec.pebble.map { p in
            MomentSummary.PebbleSummary(
                id: momentId, intensity: p.intensity, positiveness: p.positiveness, visibility: .private,
                emotion: Self.emotion(p.emotion), glyph: p.glyph.map { Self.glyph($0) },
                souls: p.souls.map { Self.soul($0) }
            )
        }
        return MomentSummary(id: momentId, name: spec.name.isEmpty ? nil : spec.name, happenedAt: happened,
                             createdAt: happened, firstSnapPath: spec.photo.map { "fixture/snap-\($0)" },
                             pebble: pebble)
    }

    private static func glyph(_ mark: String) -> PebbleGlyph {
        PebbleGlyph(id: id(0xA000, (markNames.firstIndex(of: mark) ?? 0) + 1), name: mark,
                    strokes: SpineFixtureMarks.strokes[mark] ?? [], viewBox: "0 0 200 200", source: .domain)
    }

    private static func id(_ group: Int, _ n: Int) -> UUID {
        UUID(uuidString: String(format: "00000000-0000-4000-%04lX-%012ld", group, n)) ?? UUID()
    }

    private static func date(_ iso: String) -> Date {
        ISO8601Flexible.parse(iso) ?? .distantPast
    }
}
#endif
