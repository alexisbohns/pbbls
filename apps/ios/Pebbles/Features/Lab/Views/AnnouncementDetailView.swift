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
                            Rectangle().fill(Color.pebbles.surfaceContainerHighest)
                        @unknown default:
                            Rectangle().fill(Color.pebbles.surfaceContainerHighest)
                        }
                    }
                    .frame(height: 200)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }

                Text(log.title(for: locale))
                    .font(.largeTitle.bold())
                    .foregroundStyle(Color.pebbles.onSurface)

                Text(log.summary(for: locale))
                    .font(.title3)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)

                if let body = log.body(for: locale), !body.isEmpty {
                    MarkdownBlocksView(markdown: body)
                }
            }
            .padding()
        }
        .pebblesScreen()
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    let json = """
    {
        "id": "00000000-0000-0000-0000-000000000001",
        "species": "announcement",
        "platform": "ios",
        "status": "shipped",
        "title_en": "New feature: color system refactor",
        "title_fr": null,
        "summary_en": "We have rebuilt the design system around a system + accent palette.",
        "summary_fr": null,
        "body_md_en": "# Heading 1\\n\\nFirst paragraph with some **bold** text and a [link](https://example.com).\\n\\n## Heading 2\\n\\nSecond paragraph after a heading. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\\n\\n### Heading 3\\n\\nThird paragraph under a smaller heading.",
        "body_md_fr": null,
        "cover_image_path": null,
        "external_url": null,
        "published": true,
        "published_at": "2026-05-15T10:00:00Z",
        "released_at": null,
        "created_at": "2026-05-15T10:00:00Z",
        "reaction_count": 0
    }
    """
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .iso8601
    // Preview fixture: a literal that stops decoding should break the preview
    // loudly rather than render a silently empty announcement.
    // swiftlint:disable:next force_try
    let log = try! decoder.decode(Log.self, from: Data(json.utf8))
    return NavigationStack {
        AnnouncementDetailView(log: log, coverImageURL: nil)
    }
}
