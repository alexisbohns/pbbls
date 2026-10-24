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

    // swiftlint:disable:next large_tuple
    private static let soulTable: [(key: String, name: String, mark: String)] = [
        ("mara", "Mara", "partner"), ("elio", "Elio", "family"), ("nadia", "Nadia", "friends"),
        ("jules", "Jules", "hobbies"), ("bruno", "Bruno", "travel"), ("ana", "Ana", "friends"),
        ("bea", "Béa", "hobbies"), ("cyril", "Cyril", "identity"), ("dora", "Dora", "travel"),
        ("ezra", "Ezra", "family"), ("felix", "Félix", "partner"), ("gaby", "Gaby", "friends"),
        ("hugo", "Hugo", "hobbies"), ("ines", "Inès", "travel")
    ]
    private static let markNames = ["identity", "travel", "friends", "family", "hobbies", "partner"]

    private static func soul(_ key: String) -> PathSoul {
        guard let index = soulTable.firstIndex(where: { $0.key == key }) else {
            preconditionFailure("unknown soul \(key)")
        }
        guard let mark = markNames.firstIndex(of: soulTable[index].mark) else {
            preconditionFailure("unknown mark \(soulTable[index].mark)")
        }
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
    // swiftlint:disable:next large_tuple
    private static let emotionTable: [(slug: String, name: String, category: String)] = [
        ("proud", "Proud", "pride"), ("joyful", "Joyful", "joy"), ("angry", "Angry", "anger"),
        ("scared", "Scared", "fear"), ("peaceful", "Peaceful", "peace"), ("sad", "Sad", "sadness")
    ]

    private static func emotion(_ slug: String) -> EmotionRef {
        guard let index = emotionTable.firstIndex(where: { $0.slug == slug }) else {
            preconditionFailure("unknown emotion \(slug)")
        }
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

    private struct PebbleSpec {
        let intensity: Int
        let positiveness: Int
        let emotion: String
        let souls: [String]
        let glyph: String?
    }

    private struct Spec {
        let n: Int
        let name: String
        let at: String
        var photo: String?
        var pebble: PebbleSpec?
    }

    private static let neighbours = ["ana", "bea", "cyril", "dora", "ezra", "felix", "gaby", "hugo", "ines"]

    private static let specs: [Spec] = [
        Spec(n: 1, name: "Flag march with Nadia", at: "05T10:15", photo: "flag-march.jpg",
             pebble: PebbleSpec(intensity: 3, positiveness: 1, emotion: "proud",
                                souls: ["nadia"], glyph: "friends")),
        Spec(n: 2, name: "Elio lost his first tooth", at: "05T19:40",
             pebble: PebbleSpec(intensity: 2, positiveness: 1, emotion: "joyful",
                                souls: ["elio", "mara"], glyph: "family")),
        Spec(n: 3, name: "Rent went up again", at: "06T08:02",
             pebble: PebbleSpec(intensity: 2, positiveness: -1, emotion: "angry",
                                souls: [], glyph: "identity")),
        Spec(n: 4, name: "Forest walk, all of us", at: "06T16:30", photo: "forest-crew.jpg",
             pebble: PebbleSpec(intensity: 3, positiveness: 1, emotion: "peaceful",
                                souls: ["mara", "elio", "bruno"], glyph: "travel")),
        Spec(n: 5, name: "Couldn’t sleep", at: "07T03:10",
             pebble: PebbleSpec(intensity: 1, positiveness: -1, emotion: "scared",
                                souls: [], glyph: "identity")),
        Spec(n: 6, name: "Coffee by the fountain", at: "07T12:05", photo: "fountain.jpg",
             pebble: PebbleSpec(intensity: 1, positiveness: 0, emotion: "peaceful",
                                souls: ["jules"], glyph: "friends")),
        Spec(n: 7, name: "Rooftop birthday", at: "08T21:00", photo: "rooftop-group.jpg",
             pebble: PebbleSpec(intensity: 3, positiveness: 1, emotion: "joyful",
                                souls: ["nadia", "jules", "mara"], glyph: "hobbies")),
        Spec(n: 12, name: "Picnic on the square, the whole street", at: "08T13:10",
             pebble: PebbleSpec(intensity: 2, positiveness: 1, emotion: "joyful",
                                souls: ["mara", "elio", "nadia", "jules", "bruno"] + neighbours, glyph: "friends")),
        Spec(n: 8, name: "Said no to the extra project", at: "10T09:20",
             pebble: PebbleSpec(intensity: 2, positiveness: 0, emotion: "proud",
                                souls: [], glyph: "identity")),
        Spec(n: 9, name: "Mara on the seawall", at: "10T17:45", photo: "seawall-portrait.jpg",
             pebble: PebbleSpec(intensity: 2, positiveness: 1, emotion: "joyful",
                                souls: ["mara"], glyph: "partner")),
        Spec(n: 13, name: "", at: "10T19:05", photo: "fountain.jpg",
             pebble: PebbleSpec(intensity: 1, positiveness: 0, emotion: "peaceful",
                                souls: ["mara"], glyph: "partner")),
        Spec(n: 10, name: "Grandma’s call went quiet", at: "11T14:00",
             pebble: PebbleSpec(intensity: 2, positiveness: -1, emotion: "sad",
                                souls: [], glyph: "family")),
        Spec(n: 11, name: "Bruno asleep on my feet", at: "11T22:30",
             pebble: PebbleSpec(intensity: 1, positiveness: 0, emotion: "peaceful",
                                souls: ["bruno"], glyph: nil)),
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
        guard let index = markNames.firstIndex(of: mark) else { preconditionFailure("unknown mark \(mark)") }
        return PebbleGlyph(id: id(0xA000, index + 1), name: mark,
                           strokes: SpineFixtureMarks.strokes[mark] ?? [], viewBox: "0 0 200 200", source: .domain)
    }

    private static func id(_ group: Int, _ n: Int) -> UUID {
        let text = String(format: "00000000-0000-4000-%04lX-%012ld", group, n)
        guard let uuid = UUID(uuidString: text) else { preconditionFailure("bad fixture id \(text)") }
        return uuid
    }

    private static func date(_ iso: String) -> Date {
        ISO8601Flexible.parse(iso) ?? .distantPast
    }
}

extension SpineFixtureWeek {
    /// A `PathModel` holding the fixture week, its souls in the reference cache,
    /// for the lab and the references. Its words read the model's default, the date locale.
    @MainActor
    static func model() async -> PathModel {
        let model = PathModel(calendar: calendar, now: { today })
        await model.load { moments }
        model.updateSouls(names: soulNames, glyphs: soulGlyphs)
        return model
    }

    /// The feed context over the fixture: its palettes, its primed photo ratios,
    /// and (#1226) its wrap and lookups when given.
    @MainActor
    static func context(_ model: PathModel, page: SpinePage, width: CGFloat = 393,
                        wrap: WeekWrap? = nil) -> SpineFeedContext {
        SpineFeedContext(
            page: page, column: SpineLayout.column(width: width),
            palette: { palette(for: $0) },
            ratio: { $0.firstSnapPath.flatMap { photos[$0]?.ratio } },
            pressRequests: model.pressRequests,
            onOpen: { _, _ in }, onDelete: { _ in },
            wrap: wrap,
            lookups: lookups
        )
    }

    /// Twenty pebbles in one week: the fixture's thirteen and seven copies an
    /// hour later, for the 20-pebble scroll budget (§Risks).
    static var dense: [MomentSummary] {
        let pebbles = moments.filter { $0.pebble != nil }
        let copies = pebbles.prefix(7).enumerated().map { index, moment in
            let id = UUID(uuidString: String(format: "00000000-0000-4000-C000-%012ld", index + 1)) ?? UUID()
            return MomentSummary(id: id, name: moment.name, happenedAt: moment.happenedAt.addingTimeInterval(3600),
                                 createdAt: moment.createdAt, firstSnapPath: moment.firstSnapPath,
                                 pebble: moment.pebble)
        }
        return pebbles + copies
    }
}

// MARK: - The week's wrap (#1226)

extension SpineFixtureWeek {
    /// The fixture week's wrap as `path_week_wrap` would send it, worked by hand
    /// from the fixture's pebbles by the server's rules: persons by days, then
    /// pebbles, then name; families by worth (intensity × charge, even ½); weight
    /// by intensity. `live` is the week as it stands (today is its Sunday): only
    /// contents and persons; settled, every tile. The domains and the cards are
    /// invented, the fixture's pebbles carrying none.
    static func weekWrap(live: Bool) -> WeekWrap {
        let start = CalendarDay(weekStart, in: calendar.timeZone)
        func person(_ key: String, _ days: [Int], _ count: Int) -> PersonStat {
            PersonStat(soulId: soul(key).id.uuidString.lowercased(), count: count, days: DaySet(length: 7, days: days),
                       isNewSincePrevious: false)
        }
        let persons = [person("mara", [0, 1, 3, 5], 6), person("bruno", [1, 3, 6], 3), person("elio", [0, 1, 3], 3),
                       person("jules", [2, 3], 3), person("nadia", [0, 3], 3)]
            + neighbours.map { person($0, [3], 1) }
        func family(_ slug: String, _ count: Int, _ worth: Double, _ days: [Int]) -> FamilyStat {
            FamilyStat(slug: slug, count: count, worth: worth, share: worth / 22.5, days: DaySet(length: 7, days: days))
        }
        func moment(_ n: Int) -> String { id(0x8000, n).uuidString.lowercased() }
        func hour(_ n: Int) -> HourMark {
            let at = moments.first { $0.id == id(0x8000, n) }?.happenedAt ?? weekStart
            let parts = calendar.dateComponents([.hour, .minute], from: at)
            let decimal = Double(parts.hour ?? 0) + Double(parts.minute ?? 0) / 60
            return HourMark(momentId: moment(n), happened: decimal, collected: decimal)
        }
        let all: [WeekTile] = live ? [.contents, .persons] : WeekTile.allCases
        return WeekWrap(
            weekStart: start, isoWeek: 41, isoYear: 2026, isLive: live, pebbles: 13, people: 14, tiles: all,
            persons: persons, inviteMomentIds: nil,
            families: live ? nil : [family("joy", 4, 9, [0, 3, 5]), family("peace", 4, 4.5, [1, 2, 5, 6]),
                                    family("pride", 2, 4, [0, 5]), family("anger", 1, 2, [1]),
                                    family("sadness", 1, 2, [6]), family("fear", 1, 1, [2])],
            lead: live ? nil : Lead(order: ["joy", "peace", "pride", "anger", "sadness", "fear"], zero: ["shame"]),
            domains: live ? nil : fixtureDomains.enumerated().map { index, domain in
                DomainStat(domainId: domain.id.uuidString.lowercased(), count: [4, 3, 3][index],
                           days: DaySet(length: 7, days: [[0, 2, 3], [0, 1, 6], [1, 2, 5]][index]))
            },
            weight: live ? nil : WeightGrid(bright: [0, 3, 3], even: [3, 1, 0], heavy: [1, 2, 0]),
            hours: live ? nil : [1, 2, 3, 4, 5, 6, 7, 12, 8, 9, 13, 10, 11].map(hour),
            kept: live ? nil : Kept(photoMomentIds: [1, 4, 6, 7, 9, 13].map(moment),
                                    cards: [CardCount(momentId: moment(2), count: 1),
                                            CardCount(momentId: moment(9), count: 2)])
        )
    }

    /// The persons' order the week's people lines follow.
    static var personsOrder: [String] { (weekWrap(live: true).persons ?? []).map(\.soulId) }

    /// The fixture's families: one per category, its palette the sandbox's.
    static var families: [EmotionCategory] {
        emotionTable.compactMap { row in
            guard let palette = palettes[emotion(row.slug).id] else { return nil }
            return EmotionCategory(id: id(0xD000, emotionTable.firstIndex { $0.slug == row.slug } ?? 0),
                                   slug: row.category, name: row.category.capitalized, palette: palette)
        }
    }

    static let fixtureDomains: [Domain] = [("friends", "Friends"), ("family", "Family"), ("identity", "Identity")]
        .enumerated().map { index, row in
            Domain(id: id(0xE000, index + 1), slug: row.0, name: row.1, label: row.1)
        }

    /// What the fixture wrap's ids point at.
    static var lookups: WrapLookups {
        let byId = Dictionary(uniqueKeysWithValues: moments.map { ($0.id.uuidString.lowercased(), $0) })
        let families = Dictionary(uniqueKeysWithValues: Self.families.map { ($0.slug, $0) })
        let domains = Dictionary(uniqueKeysWithValues: fixtureDomains.map { ($0.id.uuidString.lowercased(), $0) })
        return WrapLookups(moment: { byId[$0.lowercased()] }, family: { families[$0] },
                           domain: { domains[$0.lowercased()] }, palette: { palette(for: $0) })
    }
}
#endif
