import SwiftUI

/// The recto's leaf (#1146 §Recto): under a dashed score, where the card was
/// folded on the Path, the full dateline and the words; with no words, the
/// dateline alone. VoiceOver reads the dateline with the emotion straight
/// after the title, and the words last (§VoiceOver).
struct CardLeaf: View {
    let dateline: String
    /// What VoiceOver reads for the dateline: the date and time, then the emotion.
    let datelineLabel: String
    let words: String?
    let u: CGFloat

    var body: some View {
        VStack(spacing: 0) {
            Canvas { context, size in
                var path = Path()
                path.move(to: CGPoint(x: 0, y: size.height / 2))
                path.addLine(to: CGPoint(x: size.width, y: size.height / 2))
                context.stroke(path, with: .color(Color.pebbles.cardRule),
                               style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
            }
            .frame(height: 1)
            .accessibilityHidden(true)
            VStack(spacing: 14) {
                Text(verbatim: dateline)
                    .spineType(.cardLeafDateline)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel(Text(verbatim: datelineLabel))
                    .accessibilitySortPriority(4)
                if let words, !words.isEmpty {
                    Text(verbatim: words)
                        .spineType(.cardLeafBody)
                        .foregroundStyle(Color.pebbles.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilitySortPriority(1)
                }
            }
            .padding(EdgeInsets(top: 4.4 * u, leading: 3.6 * u, bottom: 6.2 * u, trailing: 3.6 * u))
        }
    }
}
