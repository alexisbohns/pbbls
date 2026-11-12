import Foundation
import Supabase

/// Which RPC a report goes through, with its wire params. Pure, so the routing
/// (glyph → `report_content`, person → `report_person`, invites never block)
/// is tested without a network.
enum ReportRequest {
    case content(ReportContentParams)
    case person(ReportPersonParams)

    var function: String {
        switch self {
        case .content: return "report_content"
        case .person: return "report_person"
        }
    }

    static func make(
        target: ReportTarget,
        reason: ReportReason,
        detail: String?,
        removeAndBlock: Bool
    ) -> ReportRequest {
        switch target {
        case .glyph(let id):
            return .content(ReportContentParams(
                pTargetKind: "glyph", pTargetId: id.uuidString, pReason: reason.rawValue, pDetail: detail))
        case .connection(let id, _):
            return .person(ReportPersonParams(
                pReason: reason.rawValue, pDetail: detail, pConnectionId: id.uuidString,
                pInviteToken: nil, pRemoveAndBlock: removeAndBlock))
        case .invite(let token, _):
            return .person(ReportPersonParams(
                pReason: reason.rawValue, pDetail: detail, pConnectionId: nil,
                pInviteToken: token, pRemoveAndBlock: false))
        }
    }
}

/// `nil` optionals are omitted by the synthesized encoder, so the RPC's
/// defaults apply.
struct ReportContentParams: Encodable {
    let pTargetKind: String
    let pTargetId: String
    let pReason: String
    let pDetail: String?

    enum CodingKeys: String, CodingKey {
        case pTargetKind = "p_target_kind"
        case pTargetId = "p_target_id"
        case pReason = "p_reason"
        case pDetail = "p_detail"
    }
}

struct ReportPersonParams: Encodable {
    let pReason: String
    let pDetail: String?
    let pConnectionId: String?
    let pInviteToken: String?
    let pRemoveAndBlock: Bool

    enum CodingKeys: String, CodingKey {
        case pReason = "p_reason"
        case pDetail = "p_detail"
        case pConnectionId = "p_connection_id"
        case pInviteToken = "p_invite_token"
        case pRemoveAndBlock = "p_remove_and_block"
    }
}

/// Files a report. Both RPCs return scalar `jsonb`, so the receipt decodes
/// straight from the body (no `.single()`).
struct ReportService {
    let supabase: SupabaseService

    func send(
        _ target: ReportTarget,
        reason: ReportReason,
        detail: String?,
        removeAndBlock: Bool
    ) async throws -> ReportReceipt {
        let request = ReportRequest.make(
            target: target, reason: reason, detail: detail, removeAndBlock: removeAndBlock)
        switch request {
        case .content(let params):
            return try await supabase.client.rpc(request.function, params: params).execute().value
        case .person(let params):
            return try await supabase.client.rpc(request.function, params: params).execute().value
        }
    }
}
