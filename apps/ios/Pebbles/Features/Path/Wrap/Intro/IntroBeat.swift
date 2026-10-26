import Foundation

/// What enters in a month's intro (#1241): the cover, or one of its sections.
enum IntroSequence: Hashable, Sendable {
    case cover
    case section(MonthSection)
}

/// One element of a sequence, named by what it is; the figures ask for these.
enum IntroKey: Hashable, Sendable {
    /// The cover: the month's name, the garland's dotted course and leaf, the souls' glyphs, the texts; a day's
    /// slot (its socket or its first seal), a stacked seal (day, index), a day's numeral.
    case kicker, monthName, year, course, leaf, summary, names, colophon
    case slot(Int), seal(Int, Int), numeral(Int), soul(Int)
    /// Any section: the insights head (over tone or hours), the head, its lead line, *more*, a row. The two
    /// heads are drawn from the start, never revealed (D8): their beats carry only the arrival's tick.
    case insights, header, lead, more, row(Int)
    /// A person's row: its glyph, its name, its ring's line, a day on its ring (row, day).
    case glyph(Int), name(Int), ring(Int), dot(Int, Int)
    /// The gem: the blank stone, the count in its table, a facet, a family's name and share.
    case blank, count, facet(String), label(String)
    /// Tone and hours: the sweep, the dial's face, a day of the chart, a stone by its rank in the day.
    case sweep, dial, day(Int), stone(Int)
}

/// How firmly a beat ticks: Core Haptics' intensity and sharpness, 0…1 (#1241 §Choreography).
struct IntroTick: Hashable, Sendable {
    let intensity: Float
    let sharpness: Float

    /// An empty day on the garland.
    static let faint = IntroTick(intensity: 0.22, sharpness: 0.25)
    /// Rows, texts, glyphs, ring dots, tone days, hour stones.
    static let light = IntroTick(intensity: 0.40, sharpness: 0.45)
    static let facet = IntroTick(intensity: 0.60, sharpness: 0.55)
    /// The garland's leaf, tying the ring.
    static let knot = IntroTick(intensity: 0.75, sharpness: 0.35)
    /// The gem's count.
    static let firm = IntroTick(intensity: 0.90, sharpness: 0.70)
    /// A chapter arriving at the top of the page (D8): firm, rounded.
    static let arrive = IntroTick(intensity: 0.6, sharpness: 0.15)

    /// A seal on the garland, firmer with its pebble's intensity, 1…3.
    static func seal(_ intensity: Int) -> IntroTick {
        IntroTick(intensity: [0.50, 0.68, 0.85][min(max(intensity - 1, 0), 2)], sharpness: 0.60)
    }
}

/// One beat: `key` starts landing `at` seconds into its sequence, takes
/// `duration` to land, and ticks `tick` as it starts.
struct IntroBeat: Hashable, Sendable {
    let key: IntroKey
    let at: TimeInterval
    let duration: TimeInterval
    var tick: IntroTick?
}

/// Each key's linear progress, 0…1, for a figure to draw (#1241 D6). The
/// default everywhere is `.settled`: every key landed, today's render.
struct IntroReveal: Sendable {
    static let settled = IntroReveal { _ in 1 }
    static let hidden = IntroReveal { _ in 0 }

    private let progress: @Sendable (IntroKey) -> Double

    init(_ progress: @escaping @Sendable (IntroKey) -> Double) {
        self.progress = progress
    }

    func callAsFunction(_ key: IntroKey) -> Double {
        progress(key)
    }
}
