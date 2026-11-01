import SwiftUI

/// One section head for Home and You (#1382 D15): the title in `sectionTitle`,
/// sentence case, and an optional figure on its first baseline, the numbers in
/// `sectionFigure` (`primary`) and a word between them in `sectionFigureWord`.
/// One header element whose label joins title and figure ("Achievements, 12
/// earned"); a caller that needs a different spoken figure overrides the label.
/// With `action`, the whole head is a plain button. The Path, the wraps and
/// People keep `SpineSectionHeader`.
struct PebblesSectionHead: View {
    enum Figure: Equatable {
        /// "3"
        case count(Int)
        /// "2 of 5"
        case ratio(done: Int, total: Int, word: String)
        /// "12 earned"
        case counted(Int, word: String)
    }

    let title: String
    var figure: Figure?
    var action: (() -> Void)?

    init(_ title: String, figure: Figure? = nil, action: (() -> Void)? = nil) {
        self.title = title
        self.figure = figure
        self.action = action
    }

    var body: some View {
        if let action {
            Button(action: action) { head.contentShape(Rectangle()) }
                .buttonStyle(.plain)
        } else {
            head
        }
    }

    private var head: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(verbatim: title)
                .spineType(.sectionTitle)
                .foregroundStyle(Color.pebbles.onSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
                .layoutPriority(1)
            if let figure { figureView(figure).fixedSize() }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: spoken))
        .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder
    private func figureView(_ figure: Figure) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 0) {
            switch figure {
            case .count(let value):
                number(value)
            case let .ratio(done, total, word):
                number(done)
                self.word(word).padding(.leading, 6).padding(.trailing, 5)
                number(total)
            case let .counted(value, word):
                number(value)
                self.word(word).padding(.leading, 6)
            }
        }
    }

    private func number(_ value: Int) -> some View {
        Text(value, format: .number)
            .spineType(.sectionFigure)
            .foregroundStyle(Color.pebbles.primary)
    }

    private func word(_ text: String) -> some View {
        Text(verbatim: text)
            .spineType(.sectionFigureWord)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
    }

    private var spoken: String {
        switch figure {
        case nil: title
        case .count(let value): "\(title), \(value.formatted())"
        case let .ratio(done, total, word): "\(title), \(done.formatted()) \(word) \(total.formatted())"
        case let .counted(value, word): "\(title), \(value.formatted()) \(word)"
        }
    }
}
