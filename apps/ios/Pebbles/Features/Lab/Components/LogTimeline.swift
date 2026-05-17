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
            row(log: log, isFirst: index == 0, isLast: index == logs.count - 1)
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
                // Zero out List's default vertical insets so rows touch
                // top-to-bottom. The row's own padding (lead-in line in the
                // icon column + bottom padding on the content column) becomes
                // the inter-icon gap, and the icon-column line bridges it.
                .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
        }
    }

    @ViewBuilder
    private func row(log: Log, isFirst: Bool, isLast: Bool) -> some View {
        HStack(alignment: .top, spacing: 12) {
            iconColumn(isFirst: isFirst, isLast: isLast)
            // The content + trailing pair shares one vertical-padding block so
            // both align to the icon (which sits 12pt below row top in
            // `iconColumn` due to the lead-in segment) and so the bottom
            // padding extends the row past the description, letting the icon
            // column's `Rectangle(maxHeight: .infinity)` bridge into the next
            // row's lead-in. Mirrors the web's `pb-5` on `<li>` content.
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    if mode == .changelog, let date = log.releasedAt ?? log.publishedAt {
                        Text(date, format: Date.FormatStyle(date: .long, time: .omitted))
                            .font(.footnote)
                            .foregroundStyle(Color.system.secondary)
                    }
                    Text(log.title(for: locale))
                        .font(.body)
                        .foregroundStyle(Color.system.foreground)
                    Text(log.summary(for: locale))
                        .font(.footnote)
                        .foregroundStyle(Color.system.secondary)
                        .lineLimit(3)
                }
                Spacer(minLength: 0)
                trailing(log)
            }
            .padding(.top, 12)
            .padding(.bottom, isLast ? 12 : 16)
        }
    }

    @ViewBuilder
    private func iconColumn(isFirst: Bool, isLast: Bool) -> some View {
        VStack(spacing: 0) {
            // Lead-in: 12pt segment above the icon. For non-first rows this
            // is a line segment that visually connects to the previous row's
            // bottom line (rows touch via `.listRowInsets(top: 0, ...)`). For
            // the first row it's transparent so the timeline starts cleanly
            // at the icon.
            Rectangle()
                .fill(isFirst ? Color.clear : Color.system.muted)
                .frame(width: 1, height: 12)
            Image(systemName: iconName)
                .font(.system(size: 14, weight: .regular))
                .foregroundStyle(iconColor)
            if !isLast {
                Rectangle()
                    .fill(Color.system.muted)
                    .frame(width: 1)
                    .frame(maxHeight: .infinity)
                    .padding(.top, 2)
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
        case .changelog:  return Color.accent.primary
        case .inProgress, .backlog: return Color.system.secondary
        }
    }
}

extension LogTimeline where Trailing == EmptyView {
    init(mode: Mode, logs: [Log]) {
        self.init(mode: mode, logs: logs, trailing: { _ in EmptyView() })
    }
}
