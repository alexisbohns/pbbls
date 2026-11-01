import SwiftUI

/// You's Lab (#1382 D21): the latest announcement as a card, the three latest
/// shipped features as a dated mini-timeline with an "All updates" capsule to
/// the Lab page, and the community stone, which always shows. Each read fails
/// alone: a missing announcement or changelog leaves the rest.
struct YouLabSection: View {
    let announcement: Log?
    let changelog: [Log]
    var coverURL: (Log) -> URL? = { _ in nil }

    @Environment(\.openURL) private var openURL
    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            PebblesSectionHead(String(localized: "Lab"))
                .padding(.bottom, 18)
            if let announcement {
                NavigationLink(value: YouRoute.announcement(announcement.id)) { card(announcement) }
                    .buttonStyle(.plain)
                    .padding(.bottom, 22)
            }
            if !changelog.isEmpty {
                timeline
                    .padding(.bottom, 14)
                NavigationLink(value: YouRoute.lab) { allUpdates }
                    .buttonStyle(.plain)
                    .padding(.leading, Self.dateColumn + 16)
                    .padding(.bottom, 24)
            }
            community
        }
    }

    // MARK: - The announcement

    private func card(_ log: Log) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            cover(log)
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: kicker(log))
                    .spineType(.labKicker)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                Text(verbatim: log.title(for: locale))
                    .spineType(.labTitle)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .lineLimit(1)
                Text(verbatim: log.summary(for: locale))
                    .spineType(.rowLine)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(2)
            }
            .padding(EdgeInsets(top: 14, leading: 18, bottom: 18, trailing: 18))
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .pebblesCard()
        .accessibilityElement(children: .combine)
    }

    /// The log's cover when it has one, else c3's drawn band: a stone cut in
    /// three on the stock, washed with the accent surface.
    @ViewBuilder
    private func cover(_ log: Log) -> some View {
        if let url = coverURL(log) {
            AsyncImage(url: url) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    drawnBand
                }
            }
            .frame(height: 96)
            .frame(maxWidth: .infinity)
            .clipped()
            .accessibilityHidden(true)
        } else {
            drawnBand
        }
    }

    private var drawnBand: some View {
        SplitStone(count: 3, seed: "camera|fixed|rock|q11",
                   options: StoneOptions(corner: 22, gutter: 6, fillet: 14),
                   fills: [Color.pebbles.primaryContainer], minHeight: 64) { _ in
            Color.clear.frame(height: 64)
        }
        .frame(width: 286)
        .padding(.vertical, 22)
        .frame(maxWidth: .infinity)
        .background {
            ZStack {
                Color.pebbles.stock
                Color.pebbles.primaryContainer.opacity(0.35)
            }
        }
        .accessibilityHidden(true)
    }

    private func kicker(_ log: Log) -> String {
        let date = (log.publishedAt ?? log.createdAt).formatted(.dateTime.day().month(.wide).locale(locale))
        return SpineStrings.format("you.lab.kicker", locale: locale, date)
    }

    // MARK: - The timeline

    /// The date column's width; the dot sits right of it.
    static let dateColumn: CGFloat = 46

    private var timeline: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(Array(changelog.enumerated()), id: \.element.id) { index, log in
                entry(log, first: index == 0)
            }
        }
        .background(alignment: .topLeading) {
            // The rule behind the dots, from the first dot to the last.
            Rectangle()
                .fill(Color.pebbles.rule)
                .frame(width: 1)
                .padding(.leading, Self.dateColumn + 12 + 4)
                .padding(.top, 10)
                .padding(.bottom, 30)
                .accessibilityHidden(true)
        }
    }

    private func entry(_ log: Log, first: Bool) -> some View {
        let date = log.releasedAt ?? log.publishedAt ?? log.createdAt
        return HStack(alignment: .top, spacing: 12) {
            Text(verbatim: date.formatted(.dateTime.day().month(.abbreviated).locale(locale)))
                .spineType(.labDate)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .multilineTextAlignment(.trailing)
                .frame(minWidth: Self.dateColumn, alignment: .trailing)
                .padding(.top, 1)
            dot(first: first).padding(.top, 6)
            VStack(alignment: .leading, spacing: 1) {
                Text(verbatim: log.title(for: locale))
                    .spineType(.labEntry)
                    .foregroundStyle(Color.pebbles.onSurface)
                Text(verbatim: log.summary(for: locale))
                    .spineType(.labEntryLine)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: [date.formatted(.dateTime.day().month(.wide).locale(locale)),
                                            log.title(for: locale), log.summary(for: locale)]
            .joined(separator: ", ")))
    }

    @ViewBuilder
    private func dot(first: Bool) -> some View {
        if first {
            Circle().fill(Color.pebbles.primary).frame(width: 9, height: 9)
        } else {
            Circle()
                .fill(Color.pebbles.surface)
                .overlay(Circle().strokeBorder(Color.pebbles.outline, lineWidth: 1.5))
                .frame(width: 9, height: 9)
        }
    }

    private var allUpdates: some View {
        HStack(spacing: 4) {
            Text("you.lab.allUpdates")
                .spineType(.pillLabel)
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .accessibilityHidden(true)
        }
        .foregroundStyle(Color.pebbles.primary)
        .padding(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 12))
        .overlay(Capsule().strokeBorder(Color.pebbles.outlineVariant, lineWidth: 1))
        .contentShape(Capsule())
    }

    // MARK: - The community

    private var community: some View {
        Button { openURL(LabConfig.whatsappInviteURL) } label: {
            HStack(spacing: 14) {
                PbblsIconImage(.whatsapp, size: 32)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Join the community")
                        .spineType(.communityTitle)
                    Text("you.lab.community.line")
                        .spineType(.rowLine)
                        .opacity(0.85)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .accessibilityHidden(true)
            }
            .foregroundStyle(Color.pebbles.onTertiaryContainer)
            .padding(EdgeInsets(top: 18, leading: 20, bottom: 18, trailing: 18))
            .background {
                StoneShape(seed: "whatsapp", options: StoneOptions(corner: 30))
                    .fill(Color.pebbles.tertiaryContainer)
                    .accessibilityHidden(true)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isLink)
    }
}
