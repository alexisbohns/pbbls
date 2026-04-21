import SwiftUI

/// Compact row rendering a log's localized title and summary. Used inside
/// the Lab tab's changelog, initiatives and backlog sections. The trailing
/// slot lets the parent attach contextual controls (e.g. a reaction button
/// for backlog items) without coupling the row to any specific action.
struct LogRow<Trailing: View>: View {
    let log: Log
    @ViewBuilder var trailing: () -> Trailing

    @Environment(\.locale) private var locale

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(log.title(for: locale))
                    .font(.body)
                    .foregroundStyle(Color.pebblesForeground)
                Text(log.summary(for: locale))
                    .font(.footnote)
                    .foregroundStyle(Color.pebblesMutedForeground)
                    .lineLimit(3)
            }
            Spacer(minLength: 0)
            trailing()
        }
        .padding(.vertical, 4)
    }
}

extension LogRow where Trailing == EmptyView {
    init(log: Log) {
        self.init(log: log, trailing: { EmptyView() })
    }
}
