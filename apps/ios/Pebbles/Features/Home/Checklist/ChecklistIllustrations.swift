import SwiftUI

/// A task sheet's picture (spec §1.4), drawn with the real parts the task
/// makes: the contents row and its seals, the collections row, soul marks, a
/// glyph. No colour but what those parts carry. Decorative inside; the
/// container reads one short label.
struct ChecklistIllustration: View {
    let task: HomeChecklist.Task

    static let height: CGFloat = 120

    var body: some View {
        content
            .accessibilityHidden(true)
            .frame(maxWidth: .infinity)
            .frame(height: Self.height)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(task.illustrationLabel))
    }

    @ViewBuilder
    private var content: some View {
        switch task {
        case .moment:
            ChecklistContentsLine(name: String(localized: "home.task.sample.moment")) { blankSeal }
        case .pebble:
            ChecklistContentsLine(name: String(localized: "home.task.sample.moment")) {
                HStack(spacing: 6) {
                    blankSeal
                    Image(systemName: "arrow.right")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Color.pebbles.outline)
                    litSeal(.highlightMedium)
                }
            }
        case .collection:
            VStack(alignment: .leading, spacing: Spacing.md) {
                HStack(spacing: Spacing.sm) {
                    litSeal(.highlightSmall)
                    litSeal(.neutralLarge)
                    litSeal(.lowlightMedium)
                }
                CollectionRow(collection: Collection(
                    id: UUID(uuidString: "00000000-0000-0000-0000-00000000c011")!,
                    name: String(localized: "home.task.sample.collection"), mode: .stack, pebbleCount: 3))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.lg)
        case .soul:
            VStack(spacing: Spacing.md) {
                HStack(spacing: Spacing.lg) {
                    ForEach(Array(ChecklistMarks.souls.enumerated()), id: \.offset) { _, strokes in
                        SoulMark(strokes: strokes, side: 28)
                    }
                }
                ChecklistContentsLine(name: String(localized: "home.task.sample.moment"),
                                      soul: ChecklistMarks.souls[0]) { litSeal(.neutralMedium) }
            }
        case .glyph:
            ZStack {
                Circle()
                    .strokeBorder(Color.pebbles.outline, style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                    .frame(width: 56, height: 56)
                GlyphThumbnail(strokes: ChecklistMarks.souls[1], side: 36, strokeColor: Color.pebbles.onSurfaceVariant)
                    .frame(width: 36, height: 36)
            }
        }
    }

    private var blankSeal: some View {
        StoneSeal(valence: .neutralMedium, glyph: nil, palette: SpineColors.palette(nil, isBlank: true),
                  isBlank: true, size: .contents)
    }

    private func litSeal(_ valence: Valence) -> some View {
        StoneSeal(valence: valence, glyph: nil, palette: SpineColors.palette(nil, isBlank: false),
                  isBlank: false, size: .contents)
    }
}

/// One contents row as the week's contents draw it (`WeekContents`): the
/// day in small caps, the seal, the name in the contents rank, the time.
/// It mirrors the row in `WeekContents.swift`; edit the two together.
private struct ChecklistContentsLine<Seal: View>: View {
    let name: String
    var soul: [GlyphStroke]?
    @ViewBuilder let seal: () -> Seal

    private static var sample: Date {
        let calendar = SpineCalendar.current
        return calendar.date(from: DateComponents(year: 2026, month: 10, day: 27, hour: 12, minute: 40)) ?? .now
    }

    var body: some View {
        let calendar = SpineCalendar.current
        HStack(alignment: .center, spacing: 0) {
            Text(verbatim: SpineDates.shortWeekday(Self.sample, calendar: calendar, locale: SpineStrings.appLocale))
                .spineType(.contentsDay)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .frame(width: SpineLayout.contentsDayColumn, alignment: .leading)
            seal()
                .frame(minWidth: SpineLayout.contentsSealColumn)
                .padding(.trailing, 6)
            HStack(spacing: 6) {
                if let soul { SoulMark(strokes: soul, side: 16) }
                Text(verbatim: name)
                    .spineType(.contentsName(EntryTypes.contentsNameType(intensity: 2, valence: 0)))
                    .foregroundStyle(Color.pebbles.onSurface)
                    .lineLimit(1)
            }
            .padding(.trailing, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(verbatim: SpineDates.time(Self.sample, calendar: calendar, locale: SpineStrings.dateLocale))
                .spineType(.contentsTime)
                .foregroundStyle(Color.pebbles.outline)
        }
        .frame(minHeight: SpineLayout.contentsRowMin)
        .padding(.horizontal, Spacing.lg)
        .overlay(alignment: .top) { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
        .overlay(alignment: .bottom) { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
    }
}

/// Three small hand-drawn marks in glyph space (200 × 200, 6 pt strokes), so
/// the pictures need no fixture: a wave, a loop, a ridge.
enum ChecklistMarks {
    static let souls: [[GlyphStroke]] = [
        [GlyphStroke(d: "M30,110 Q65,55 100,110 Q135,165 170,110", width: 6)],
        [GlyphStroke(d: "M100,40 Q160,40 160,100 Q160,160 100,160 Q40,160 40,100 Q40,65 95,70", width: 6),
         GlyphStroke(d: "M100,100 L100,101", width: 6)],
        [GlyphStroke(d: "M30,160 L80,70 L115,125 L140,90 L175,160", width: 6)]
    ]
}
