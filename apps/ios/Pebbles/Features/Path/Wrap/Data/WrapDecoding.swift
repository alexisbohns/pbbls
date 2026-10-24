import Foundation

/// The wraps' decoder (#1226). The payloads grow additively across clients, so
/// a key this build does not know is ignored (`Decodable`'s own behaviour) and a
/// missing key or an explicit null takes the field's default: kotlinx's
/// `ignoreUnknownKeys` + `coerceInputValues`, which Android decodes with.
/// Production and the decoding tests use this one entry point, so the tests read
/// the payloads exactly as the app does.
enum WrapDecoding {
    static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        try JSONDecoder().decode(type, from: data)
    }
}

extension KeyedDecodingContainer {
    /// The value at `key`, or `fallback` when the key is missing or null.
    func decode<T: Decodable>(_ key: Key, or fallback: @autoclosure () -> T) throws -> T {
        try decodeIfPresent(T.self, forKey: key) ?? fallback()
    }
}
