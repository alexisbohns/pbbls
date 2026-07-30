import Foundation

/// A peer as projected by the connection RPCs. Never a `profiles` row — the
/// definer RPCs hand back exactly these display fields (M49, design D7).
struct ConnectionPeer: Decodable, Hashable {
    let displayName: String?
    let glyph: PeerGlyph?

    enum CodingKeys: String, CodingKey {
        case displayName = "display_name"
        case glyph
    }
}

/// The peer's avatar geometry — strokes plus viewBox, the render payload and
/// nothing else (no id, no name, no ownership).
struct PeerGlyph: Decodable, Hashable {
    let strokes: [GlyphStroke]
    let viewBox: String

    enum CodingKeys: String, CodingKey {
        case strokes
        case viewBox = "view_box"
    }
}

/// One row of `get_connections()`.
struct Connection: Decodable, Identifiable, Hashable {
    let connectionId: UUID
    let connectedAt: Date
    let peer: ConnectionPeer

    var id: UUID { connectionId }

    enum CodingKeys: String, CodingKey {
        case connectionId = "connection_id"
        case connectedAt = "connected_at"
        case peer
    }
}

/// `create_connection_invite()` — the live invite, re-displayed on every open
/// until it is rotated or expires.
struct ConnectionInvite: Decodable, Hashable {
    let token: String
    let expiresAt: Date

    enum CodingKeys: String, CodingKey {
        case token
        case expiresAt = "expires_at"
    }

    /// The one canonical invite URL. No custom scheme: this https link opens
    /// the app through universal links when installed, and the web accept page
    /// when not (M49, design D11).
    var url: URL {
        URL(string: "https://www.pbbls.app/invite/\(token)")!
    }
}

/// `preview_connection_invite()` — never an error, always a status.
struct InvitePreview: Decodable, Hashable {
    enum Status: String, Decodable {
        case valid
        case expired
        case notFound = "not_found"
    }

    let status: Status
    /// Present only for `valid`: a withdrawn or expired token goes fully dark.
    let inviter: ConnectionPeer?
}

/// `accept_connection_invite()`. `alreadyConnected` is a success state — the
/// multi-use QR makes re-scans and retries ordinary.
struct AcceptInviteResult: Decodable, Hashable {
    let connectionId: UUID
    let alreadyConnected: Bool
    let peer: ConnectionPeer

    enum CodingKeys: String, CodingKey {
        case connectionId = "connection_id"
        case alreadyConnected = "already_connected"
        case peer
    }
}
