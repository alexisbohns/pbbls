import Foundation

/// The persons of the week's fold (#1130 R15): the server ranks the persons
/// (#1226, `path_week_wrap`), and the list shows the first `cap` before "and N
/// others".
enum PersonsOfWeek {
    /// Rows shown before the rest fold into "and N others".
    static let cap = 8

    /// The first `cap` rows, and the rest behind "and N others".
    static func split<Row>(_ rows: [Row], cap: Int = cap) -> (shown: [Row], folded: [Row]) {
        (Array(rows.prefix(cap)), Array(rows.dropFirst(cap)))
    }
}
