import Foundation

// The parts the week and the month payloads share (#1226), as the server sends
// them. Every field takes its default when missing or null (`WrapDecoding`).

struct PersonPayload: Decodable {
    let soulId: String
    let count: Int
    let days: [Int]
    let newSincePrevious: Bool

    private enum CodingKeys: String, CodingKey {
        case count, days
        case soulId = "soul_id"
        case newSincePrevious = "new_since_previous"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        soulId = try c.decode(.soulId, or: "")
        count = try c.decode(.count, or: 0)
        days = try c.decode(.days, or: [])
        newSincePrevious = try c.decode(.newSincePrevious, or: false)
    }
}

struct FamilyPayload: Decodable {
    let slug: String
    let count: Int
    let worth: Double
    let share: Double
    let days: [Int]

    private enum CodingKeys: String, CodingKey { case slug, count, worth, share, days }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        slug = try c.decode(.slug, or: "")
        count = try c.decode(.count, or: 0)
        worth = try c.decode(.worth, or: 0)
        share = try c.decode(.share, or: 0)
        days = try c.decode(.days, or: [])
    }
}

struct LeadPayload: Decodable {
    let order: [String]
    let zero: [String]

    private enum CodingKeys: String, CodingKey { case order, zero }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        order = try c.decode(.order, or: [])
        zero = try c.decode(.zero, or: [])
    }
}

struct DomainPayload: Decodable {
    let domainId: String
    let count: Int
    let days: [Int]

    private enum CodingKeys: String, CodingKey {
        case count, days
        case domainId = "domain_id"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        domainId = try c.decode(.domainId, or: "")
        count = try c.decode(.count, or: 0)
        days = try c.decode(.days, or: [])
    }
}

struct HourPayload: Decodable {
    let momentId: String
    let happened: Double
    let collected: Double

    private enum CodingKeys: String, CodingKey {
        case happened, collected
        case momentId = "moment_id"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        momentId = try c.decode(.momentId, or: "")
        happened = try c.decode(.happened, or: 0)
        collected = try c.decode(.collected, or: 0)
    }
}
