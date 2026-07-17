package app.pbbls.android.features.lab.models

import app.pbbls.android.features.lab.models.LabMarkdown.Block
import app.pbbls.android.features.lab.models.LabMarkdown.Span
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the V1 markdown subset (M44 design D5) — match iOS, don't exceed. */
class LabMarkdownTest {
    @Test
    fun splitsOnDoubleNewlineTrimsAndDropsEmpties() {
        val blocks = LabMarkdown.parse("First.\n\n\n\n  Second.  \n\nThird.")
        assertEquals(3, blocks.size)
        assertEquals(
            listOf("First.", "Second.", "Third."),
            blocks.map { (it as Block.Paragraph).spans.single().text },
        )
    }

    @Test
    fun headingLevelsParseWithPlainText() {
        val blocks = LabMarkdown.parse("# One\n\n## Two\n\n### Three")
        assertEquals(
            listOf(
                Block.Heading(level = 1, text = "One"),
                Block.Heading(level = 2, text = "Two"),
                Block.Heading(level = 3, text = "Three"),
            ),
            blocks,
        )
    }

    @Test
    fun headingTextGetsNoInlineParsing() {
        val block = LabMarkdown.parse("## Bold **stays** literal").single()
        assertEquals(Block.Heading(level = 2, text = "Bold **stays** literal"), block)
    }

    @Test
    fun headingRequiresTrailingSpaceAndOwnBlock() {
        // No trailing space → paragraph; a heading prefix mid-chunk is not a
        // block start (the chunk keeps its newline as a line break).
        val noSpace = LabMarkdown.parse("#NoSpace").single()
        assertEquals(Block.Paragraph(listOf(Span(text = "#NoSpace"))), noSpace)
        val midChunk = LabMarkdown.parse("Line one\n# not a heading").single()
        assertEquals(Block.Paragraph(listOf(Span(text = "Line one\n# not a heading"))), midChunk)
    }

    @Test
    fun singleNewlinesStayInsideParagraphs() {
        val block = LabMarkdown.parse("Line one\nLine two").single()
        assertEquals(Block.Paragraph(listOf(Span(text = "Line one\nLine two"))), block)
    }

    @Test
    fun inlineConstructsParse() {
        val spans = LabMarkdown.parseInline("a **b** *c* `d` ~e~ [f](https://x.test)")
        assertEquals(
            listOf(
                Span(text = "a "),
                Span(text = "b", bold = true),
                Span(text = " "),
                Span(text = "c", italic = true),
                Span(text = " "),
                Span(text = "d", code = true),
                Span(text = " "),
                Span(text = "e", strikethrough = true),
                Span(text = " "),
                Span(text = "f", linkUrl = "https://x.test"),
            ),
            spans,
        )
    }

    @Test
    fun nestedConstructsComposeFlags() {
        val spans = LabMarkdown.parseInline("**bold *both* bold**")
        assertEquals(
            listOf(
                Span(text = "bold ", bold = true),
                Span(text = "both", bold = true, italic = true),
                Span(text = " bold", bold = true),
            ),
            spans,
        )
    }

    @Test
    fun unmatchedOrEmptyDelimitersStayLiteral() {
        assertEquals(listOf(Span(text = "a * b")), LabMarkdown.parseInline("a * b"))
        assertEquals(listOf(Span(text = "a ` b")), LabMarkdown.parseInline("a ` b"))
        assertEquals(listOf(Span(text = "[no](")), LabMarkdown.parseInline("[no]("))
    }

    @Test
    fun codeSpansDoNotParseTheirContent() {
        val spans = LabMarkdown.parseInline("`**not bold**`")
        assertEquals(listOf(Span(text = "**not bold**", code = true)), spans)
    }

    @Test
    fun unsupportedConstructsRenderLiteral() {
        val blocks = LabMarkdown.parse("- item one\n- item two\n\n> quoted")
        assertEquals(
            listOf(
                Block.Paragraph(listOf(Span(text = "- item one\n- item two"))),
                Block.Paragraph(listOf(Span(text = "> quoted"))),
            ),
            blocks,
        )
    }
}
