import SwiftUI

/// One prompted pebble (spec §2, D-plan-2): the Spine's contents row — day,
/// seal, name by intensity — with the "who?" affordance where the time would
/// be. The whole row is one button that opens the pebble on its Souls section;
/// Android copies the same row. A rule above; a rule below on the last row.
struct PromptRow: View {
    let prompt: PeoplePrompts.Prompt
    let palette: EmotionPalette?
    var isLast = false
    let onWho: () -> Void

    private var locale: Locale { SpineStrings.appLocale }
    private var calendar: Calendar { SpineCalendar.current }

    private var name: String? { prompt.name.flatMap { $0.isEmpty ? nil : $0 } }

    var body: some View {
        let cut = name == nil
            ? SpineType.Cut.untitled(size: 16.5)
            : EntryTypes.contentsNameType(intensity: prompt.intensity, valence: prompt.valence.positiveness)
        Button(action: onWho) {
            HStack(alignment: .center, spacing: 0) {
                Text(verbatim: SpineDates.shortWeekday(prompt.happenedAt, calendar: calendar, locale: locale))
                    .spineType(.contentsDay)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .frame(width: SpineLayout.contentsDayColumn, alignment: .leading)
                StoneSeal(valence: prompt.valence, glyph: nil, palette: SpineColors.palette(palette, isBlank: false),
                          isBlank: false, size: .contents)
                    .frame(width: SpineLayout.contentsSealColumn)
                Text(verbatim: name ?? SpineCopy.untitled(locale: locale))
                    .spineType(.contentsName(cut))
                    .foregroundStyle(name == nil ? Color.pebbles.onSurfaceVariant : Color.pebbles.onSurface)
                    .padding(.vertical, 6)
                    .padding(.trailing, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(verbatim: PeopleOnboardingCopy.who)
                    .spineType(.kicker)
                    .foregroundStyle(Color.pebbles.primary)
            }
            .frame(minHeight: SpineLayout.contentsRowMin)
            .padding(.vertical, 2)
            .overlay(alignment: .top) { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
            .overlay(alignment: .bottom) {
                if isLast { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: name ?? SpineCopy.untitled(locale: locale)))
        .accessibilityHint(Text(verbatim: PeopleOnboardingCopy.whoHint))
    }
}
