import Testing
@testable import Pebbles

@Suite("Glyph forms")
struct GlyphFormTests {
    @Test(arguments: [
        ("regular", GlyphForm(.regular)),
        ("marked", GlyphForm(.regular, marked: true)),
        ("solid", GlyphForm(.solid)),
        ("solid-marked", GlyphForm(.solid, marked: true)),
        ("dotted", GlyphForm(.dotted)),
        ("parts-left+middle", GlyphForm(.parts(["left", "middle"]))),
        ("parts-moon-marked", GlyphForm(.parts(["moon"]), marked: true)),
        ("progress-2", GlyphForm(.progress(2))),
        ("progress-12-marked", GlyphForm(.progress(12), marked: true))
    ])
    func keysRoundTrip(key: String, form: GlyphForm) {
        #expect(form.key == key)
        #expect(GlyphForm(key: key) == form)
    }

    @Test(arguments: [
        "", "marked-marked", "regular-marked", "dotted-marked", "progress-0", "progress-02",
        "parts-", "parts-Moon", "parts-a++b", "half"
    ])
    func refusesWhatIsNotAFormKey(key: String) {
        #expect(GlyphForm(key: key) == nil)
    }

    @Test func partCombinationsFollowTheGenerator() {
        #expect(GlyphData.partCombinations([]).isEmpty)
        #expect(GlyphData.partCombinations(["a"]).isEmpty)
        let expected = [["l"], ["m"], ["r"], ["l", "m"], ["l", "r"], ["m", "r"]]
        #expect(GlyphData.partCombinations(["l", "m", "r"]) == expected)
    }
}
