import SwiftUI

/// The Lab's V1 Markdown renderer, shared: split the body on blank lines and
/// feed each paragraph to `AttributedString(markdown:)`. Headings (`#`, `##`,
/// `###`) are pre-extracted and shown with a larger font. Inline images,
/// tables and fenced code blocks are not supported.
enum MarkdownBlocks {
    enum Block: Hashable {
        case heading(level: Int, text: String)
        case paragraph(String)
    }

    static func parse(_ markdown: String) -> [Block] {
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

    static func headingFont(for level: Int) -> Font {
        switch level {
        case 1: return .title.bold()
        case 2: return .title2.bold()
        default: return .title3.bold()
        }
    }

    static func attributed(_ markdown: String) -> AttributedString {
        (try? AttributedString(
            markdown: markdown,
            options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        )) ?? AttributedString(markdown)
    }
}

/// Renders `MarkdownBlocks.parse(markdown)` as a flat run of views; the
/// caller's stack supplies the spacing.
struct MarkdownBlocksView: View {
    let markdown: String

    var body: some View {
        ForEach(Array(MarkdownBlocks.parse(markdown).enumerated()), id: \.offset) { _, block in
            switch block {
            case .heading(let level, let text):
                Text(text)
                    .font(MarkdownBlocks.headingFont(for: level))
                    .foregroundStyle(Color.pebbles.onSurface)
            case .paragraph(let text):
                Text(MarkdownBlocks.attributed(text))
                    .font(.body)
                    .foregroundStyle(Color.pebbles.onSurface)
            }
        }
    }
}
