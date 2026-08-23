import Foundation
import Supabase
import Testing
@testable import Pebbles

@Suite("PebblePublisher — soft success")
struct PebblePublisherSoftSuccessTests {

    @Test("a 5xx body carrying pebble_id is recovered as a published pebble")
    func recoversPebbleId() {
        let id = UUID()
        let body = Data(#"{"pebble_id":"\#(id.uuidString)","error":"compose failed"}"#.utf8)

        #expect(PebblePublisher.softSuccessPebbleId(from: .httpError(code: 500, data: body)) == id)
    }

    @Test("a 5xx body without pebble_id is a hard failure")
    func noPebbleIdIsHardFailure() {
        let body = Data(#"{"error":"boom"}"#.utf8)
        #expect(PebblePublisher.softSuccessPebbleId(from: .httpError(code: 500, data: body)) == nil)
    }

    @Test("an empty body is a hard failure")
    func emptyBodyIsHardFailure() {
        #expect(PebblePublisher.softSuccessPebbleId(from: .httpError(code: 500, data: Data())) == nil)
    }

    @Test("a relay error is a hard failure")
    func relayErrorIsHardFailure() {
        #expect(PebblePublisher.softSuccessPebbleId(from: .relayError) == nil)
    }
}

@Suite("PebblePublisher — user-facing messages")
struct PebblePublisherMessageTests {

    @Test("a media quota rejection names the real limit")
    func quotaMessage() {
        let body = Data(#"{"error":"media_quota_exceeded"}"#.utf8)
        let message = userMessageForPebbleSaveError(FunctionsError.httpError(code: 400, data: body))
        #expect(message == "Photo limit reached on this pebble.")
    }

    @Test("a raised P0001 maps to the same quota message")
    func p0001Message() {
        let body = Data(#"{"message":"P0001: media_quota"}"#.utf8)
        let message = userMessageForPebbleSaveError(FunctionsError.httpError(code: 400, data: body))
        #expect(message == "Photo limit reached on this pebble.")
    }

    @Test("every image pipeline failure has its own message")
    func pipelineMessages() {
        #expect(userMessageForPebbleSaveError(ImagePipelineError.unsupportedFormat)
                == "That image format isn't supported.")
        #expect(userMessageForPebbleSaveError(ImagePipelineError.decodeFailed)
                == "Couldn't read the image.")
        #expect(userMessageForPebbleSaveError(ImagePipelineError.encodeFailed)
                == "Couldn't process the image.")
        #expect(userMessageForPebbleSaveError(ImagePipelineError.tooLargeAfterResize)
                == "That image is too large to attach.")
    }

    @Test("an unrecognized error falls back to the generic message")
    func fallbackMessage() {
        struct Unknown: Error {}
        #expect(userMessageForPebbleSaveError(Unknown())
                == "Couldn't save your pebble. Please try again.")
    }
}
