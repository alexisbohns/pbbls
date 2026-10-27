import Foundation
import Testing
@testable import Pebbles

@Suite("Markdown blocks") struct MarkdownBlocksTests {
    @Test func headingsAndParagraphsSplitOnBlankLines() {
        let blocks = MarkdownBlocks.parse("# A\n\nText one\nstill one\n\n## B\n\n### C\n\n   \n\nLast")
        #expect(blocks == [.heading(level: 1, text: "A"), .paragraph("Text one\nstill one"), .heading(level: 2, text: "B"), .heading(level: 3, text: "C"), .paragraph("Last")])
    }

    @Test func inlineMarkdownSurvivesInParagraphs() {
        let blocks = MarkdownBlocks.parse("Some *emphasis* here")
        #expect(blocks == [.paragraph("Some *emphasis* here")])
        #expect(String(MarkdownBlocks.attributed("Some *emphasis* here").characters) == "Some emphasis here")
    }
}
