import Foundation
import Testing
@testable import Pebbles

@Suite("AssiduityGrid layout helper")
struct AssiduityGridLayoutTests {

    @Test("chunks 28 booleans into 4 rows of 7")
    func chunksIntoRows() {
        let data = (0..<28).map { $0 % 2 == 0 }
        let rows = chunkAssiduity(data, columns: 7)
        #expect(rows.count == 4)
        #expect(rows.allSatisfy { $0.count == 7 })
        #expect(rows[0][0] == data[0])
        #expect(rows[3][6] == data[27])
    }

    @Test("pads the last row with false when count not divisible by columns")
    func padsShortFinalRow() {
        let data = Array(repeating: true, count: 10)
        let rows = chunkAssiduity(data, columns: 7)
        #expect(rows.count == 2)
        #expect(rows[0] == Array(repeating: true, count: 7))
        #expect(rows[1] == [true, true, true, false, false, false, false])
    }

    @Test("handles empty input")
    func handlesEmpty() {
        let rows = chunkAssiduity([], columns: 7)
        #expect(rows.isEmpty)
    }
}
