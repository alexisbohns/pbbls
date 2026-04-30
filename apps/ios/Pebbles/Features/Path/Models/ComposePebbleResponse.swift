import Foundation

/// Decodable wrapper for the `compose-pebble` edge function response.
///
/// Fields are optional because the edge function may return a soft-success
/// 5xx body with only `pebble_id` set when the insert succeeded but the
/// compose step failed. The iOS client advances to the detail sheet in that
/// case; the sheet renders text-only when `renderSvg` is nil.
struct ComposePebbleResponse: Decodable {
    let pebbleId: UUID
    let renderSvg: String?
    let renderVersion: String?

    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
        case renderSvg = "render_svg"
        case renderVersion = "render_version"
    }
}
