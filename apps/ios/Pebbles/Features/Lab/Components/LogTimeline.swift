import SwiftUI

/// Vertical timeline used by the Lab tab's changelog, in-progress and
/// backlog sections. Mirrors `apps/web/components/lab/LogTimeline.tsx`:
/// a leading icon column with a connecting line, and a content column
/// showing the localized title and summary. Changelog rows also display
/// the localized release date above the title. The trailing slot lets
/// the caller attach contextual controls (e.g. a reaction button for
/// backlog items) without coupling the row to any specific action.
///
/// Render inside a `List` section. Each entry sets `.listRowSeparator(.hidden)`
/// so the connecting line stays continuous across rows.
struct LogTimeline<Trailing: View>: View {
    enum Mode {
        case changelog
        case inProgress
        case backlog
    }

    let mode: Mode
    let logs: [Log]
    @ViewBuilder var trailing: (Log) -> Trailing

    @Environment(\.locale) private var locale

    var body: some View {
        ForEach(Array(logs.enumerated()), id: \.element.id) { index, log in
            row(log: log, isLast: index == logs.count - 1)
                .listRowSeparator(.hidden)
                .listRowBackground(Color.pebblesListRow)
        }
    }

    @ViewBuilder
    private func row(log: Log, isLast: Bool) -> some View {
        HStack(alignment: .top, spacing: 12) {
            iconColumn(isLast: isLast)
            VStack(alignment: .leading, spacing: 4) {
                if mode == .changelog, let date = log.releasedAt ?? log.publishedAt {
                    Text(date, format: Date.FormatStyle(date: .long, time: .omitted))
                        .font(.footnote)
                        .foregroundStyle(Color.pebblesMutedForeground)
                }
                Text(log.title(for: locale))
                    .font(.body)
                    .foregroundStyle(Color.pebblesForeground)
                Text(log.summary(for: locale))
                    .font(.footnote)
                    .foregroundStyle(Color.pebblesMutedForeground)
                    .lineLimit(3)
            }
            Spacer(minLength: 0)
            trailing(log)
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func iconColumn(isLast: Bool) -> some View {
        VStack(spacing: 0) {
            Image(systemName: iconName)
                .font(.system(size: 14, weight: .regular))
                .foregroundStyle(iconColor)
                .padding(.top, 2)
            if !isLast {
                Rectangle()
                    .fill(Color.pebblesBorder)
                    .frame(width: 1)
                    .frame(maxHeight: .infinity)
                    .padding(.top, 4)
            }
        }
        .frame(width: 16)
    }

    private var iconName: String {
        switch mode {
        case .changelog:  return "checkmark.circle"
        case .inProgress: return "circle.inset.filled"
        case .backlog:    return "circle.dashed"
        }
    }

    private var iconColor: Color {
        switch mode {
        case .changelog:  return Color.pebblesAccent
        case .inProgress, .backlog: return Color.pebblesMutedForeground
        }
    }
}

extension LogTimeline where Trailing == EmptyView {
    init(mode: Mode, logs: [Log]) {
        self.init(mode: mode, logs: logs, trailing: { _ in EmptyView() })
    }
}
