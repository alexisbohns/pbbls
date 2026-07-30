import Foundation
import Observation
import Supabase
import os

/// Mutual connections (M49). Every write goes through a `security definer`
/// RPC — the three tables carry SELECT-only RLS, so there is no client write
/// path by design. All five RPCs return scalar `jsonb`, so responses decode
/// directly from the body (no `.single()`, which is for `SETOF` functions).
///
/// The service also parks the token from an invite universal link that landed
/// before a session existed: `RootView` flushes `pendingInviteToken` once
/// `supabase.session` appears.
@Observable
@MainActor
final class ConnectionsService {
    private let supabase: SupabaseService

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "connections")

    /// Token captured from a universal link. Held until the accept sheet can
    /// be presented — i.e. until there is a session to accept with.
    var pendingInviteToken: String?

    init(supabase: SupabaseService) {
        self.supabase = supabase
    }

    /// `get_connections()` returns a jsonb array, newest first.
    func list() async throws -> [Connection] {
        try await supabase.client
            .rpc("get_connections")
            .execute()
            .value
    }

    /// Returns the caller's live invite, minting one only when none exists.
    /// `rotate` revokes the live invite and issues a fresh token — the whole
    /// revocation surface (a shared link stays valid until then).
    func createInvite(rotate: Bool = false) async throws -> ConnectionInvite {
        try await supabase.client
            .rpc("create_connection_invite", params: CreateInviteParams(pRotate: rotate))
            .execute()
            .value
    }

    /// Anon-callable preview: who is inviting, before accepting (or signing up).
    /// Never raises — an unusable token comes back as a status, not an error.
    func preview(token: String) async throws -> InvitePreview {
        try await supabase.client
            .rpc("preview_connection_invite", params: ["p_token": token])
            .execute()
            .value
    }

    /// Accepting is the mutual consent. A repeat accept succeeds with
    /// `alreadyConnected` — re-scanning a shared QR is the normal case, not an
    /// error. A block in either direction surfaces as `invite_expired`.
    func accept(token: String) async throws -> AcceptInviteResult {
        try await supabase.client
            .rpc("accept_connection_invite", params: ["p_token": token])
            .execute()
            .value
    }

    /// Severs the connection for both sides. `block` additionally records a
    /// one-way block that stops the peer re-entering through a live invite.
    func remove(connectionId: UUID, block: Bool = false) async throws {
        try await supabase.client
            .rpc(
                "remove_connection",
                params: RemoveConnectionParams(pConnectionId: connectionId.uuidString, pBlock: block)
            )
            .execute()
            .value
    }

    /// Best-effort refresh used by incidental callers; failures are logged and
    /// swallowed rather than surfaced.
    func refreshIgnoringFailure() async -> [Connection] {
        do {
            return try await list()
        } catch {
            logger.error("connections refresh failed: \(error.localizedDescription, privacy: .private)")
            return []
        }
    }
}

// MARK: - Wire params

/// The RPCs take heterogeneous params (a bool, a uuid string), so these use
/// `Encodable` structs rather than the `[String: String]` form the older
/// single-argument RPC call sites use.
private struct CreateInviteParams: Encodable {
    let pRotate: Bool

    enum CodingKeys: String, CodingKey {
        case pRotate = "p_rotate"
    }
}

private struct RemoveConnectionParams: Encodable {
    let pConnectionId: String
    let pBlock: Bool

    enum CodingKeys: String, CodingKey {
        case pConnectionId = "p_connection_id"
        case pBlock = "p_block"
    }
}

// MARK: - Errors

/// Postgres raises short slugs (`invite_expired`, …) and PostgREST delivers
/// them inside the message body, so the mapping is a substring match on the
/// lowercased text — the same contract the glyph market uses.
enum ConnectionsError: Error, LocalizedError, Equatable {
    case missingSession
    case inviteNotFound
    case inviteUnusable
    case ownInvite
    case unknown

    static func from(_ error: Error) -> ConnectionsError {
        let message = error.localizedDescription.lowercased()
        if message.contains("cannot_accept_own_invite") { return .ownInvite }
        // `invite_expired` also covers revoked tokens and a block in either
        // direction — deliberately indistinguishable, so a block is never
        // confirmed by the accept path.
        if message.contains("invite_expired") { return .inviteUnusable }
        if message.contains("invite_not_found") { return .inviteNotFound }
        if message.contains("not_authenticated") { return .missingSession }
        return .unknown
    }

    var errorDescription: String? {
        switch self {
        case .missingSession: return String(localized: "Please sign in again.")
        case .inviteNotFound, .inviteUnusable:
            return String(localized: "This invite link isn't valid anymore. Ask for a fresh one.")
        case .ownInvite: return String(localized: "That's your own invite link.")
        case .unknown: return String(localized: "Something went wrong. Please try again.")
        }
    }
}
