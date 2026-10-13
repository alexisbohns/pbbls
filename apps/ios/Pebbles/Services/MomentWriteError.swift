import Foundation
import Supabase

/// The slugs `save_moment` / `complete_pebble` / `delete_moment` raise
/// (migration 20261012090100). They arrive in the PostgREST error `message`.
enum MomentWriteError: String, CaseIterable {
    case momentEmpty = "moment_empty"
    case mediaQuotaExceeded = "media_quota_exceeded"
    case pebbleNeedsEmotion = "pebble_needs_emotion"
    case pebbleNeedsValence = "pebble_needs_valence"
    case pebbleNeedsDomain = "pebble_needs_domain"
    case soulNotOwned = "soul_not_owned"
    case collectionNotOwned = "collection_not_owned"
    case momentNotFound = "moment_not_found"
    case notAuthenticated = "not_authenticated"
    case invalidPayload = "invalid_payload"
    case invalidSnap = "invalid_snap"

    static func slug(in message: String) -> MomentWriteError? {
        allCases.first { message.contains($0.rawValue) }
    }

    var userMessage: String {
        switch self {
        case .momentEmpty:         return String(localized: "Add a name or a photo first.")
        case .mediaQuotaExceeded:  return String(localized: "Photo limit reached on this moment.")
        case .pebbleNeedsEmotion:  return String(localized: "Pick an emotion first.")
        case .pebbleNeedsValence:  return String(localized: "Pick how it landed first.")
        case .pebbleNeedsDomain:   return String(localized: "Pick a part of life first.")
        case .soulNotOwned:        return String(localized: "One of those souls is gone. Remove it and try again.")
        case .collectionNotOwned:  return String(localized: "That collection is gone. Pick another one.")
        case .momentNotFound:      return String(localized: "This moment no longer exists.")
        case .notAuthenticated:    return String(localized: "You must be signed in to save.")
        case .invalidPayload, .invalidSnap:
            return String(localized: "Couldn't save. Please try again.")
        }
    }
}

/// Message → copy. Pure, so the mapping is testable without a PostgrestError.
func userMessageForMomentWriteMessage(_ message: String) -> String {
    MomentWriteError.slug(in: message)?.userMessage ?? String(localized: "Couldn't save. Please try again.")
}

/// Any thrown error from a moment write → copy (R10). Image pipeline errors
/// keep the copy the old composer used.
func userMessageForMomentWriteError(_ error: Error) -> String {
    if let postgrest = error as? PostgrestError {
        return userMessageForMomentWriteMessage(postgrest.message)
    }
    if let pipelineError = error as? ImagePipelineError {
        switch pipelineError {
        case .unsupportedFormat:    return String(localized: "That image format isn't supported.")
        case .decodeFailed:         return String(localized: "Couldn't read the image.")
        case .encodeFailed:         return String(localized: "Couldn't process the image.")
        case .tooLargeAfterResize:  return String(localized: "That image is too large to attach.")
        }
    }
    return userMessageForMomentWriteMessage(error.localizedDescription)
}
