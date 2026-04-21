import SwiftUI

/// Full-screen detail for a single announcement: cover image, title,
/// then the localized Markdown body rendered paragraph-by-paragraph.
///
/// V1 renderer: split the body on blank lines and feed each paragraph to
/// `AttributedString(markdown:)`. Headings (`#`, `##`, `###`) are
/// pre-extracted and shown with a larger font. Inline images, tables and
/// fenced code blocks are not supported — covered in a follow-up if needed.
struct AnnouncementDetailView: View {
    let log: Log
    let coverImageURL: URL?

    @Environment(\.locale) private var locale

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let coverImageURL {
                    AsyncImage(url: coverImageURL) { phase in
                        switch phase {
                        case .success(let image):
                            image.resizable().aspectRatio(contentMode: .fill)
                        case .empty, .failure:
                            Rectangle().fill(Color.pebblesMuted.opacity(0.3))
                        @unknown default:
                            Rectangle().fill(Color.pebblesMuted.opacity(0.3))
                        }
                    }
                    .frame(height: 200)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }

                Text(log.title(for: locale))
                    .font(.largeTitle.bold())
                    .foregroundStyle(Color.pebblesForeground)

                Text(log.summary(for: locale))
                    .font(.title3)
                    .foregroundStyle(Color.pebblesMutedForeground)

                if let body = log.body(for: locale), !body.isEmpty {
                    ForEach(Array(blocks(from: body).enumerated()), id: \.offset) { _, block in
                        render(block)
                    }
                }
            }
            .padding()
        }
        .pebblesScreen()
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Markdown blocks

    private enum Block: Hashable {
        case heading(level: Int, text: String)
        case paragraph(String)
    }

    private func blocks(from markdown: String) -> [Block] {
        markdown
            .components(separatedBy: "\n\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .map { para -> Block in
                if para.hasPrefix("### ") {
                    return .heading(level: 3, text: String(para.dropFirst(4)))
                }
                if para.hasPrefix("## ") {
                    return .heading(level: 2, text: String(para.dropFirst(3)))
                }
                if para.hasPrefix("# ") {
                    return .heading(level: 1, text: String(para.dropFirst(2)))
                }
                return .paragraph(para)
            }
    }

    @ViewBuilder
    private func render(_ block: Block) -> some View {
        switch block {
        case .heading(let level, let text):
            Text(text)
                .font(headingFont(for: level))
                .foregroundStyle(Color.pebblesForeground)
        case .paragraph(let text):
            Text(attributed(text))
                .font(.body)
                .foregroundStyle(Color.pebblesForeground)
        }
    }

    private func headingFont(for level: Int) -> Font {
        switch level {
        case 1: return .title.bold()
        case 2: return .title2.bold()
        default: return .title3.bold()
        }
    }

    private func attributed(_ markdown: String) -> AttributedString {
        (try? AttributedString(
            markdown: markdown,
            options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        )) ?? AttributedString(markdown)
    }
}
