package io.teammistake.suzume.controllers

import io.teammistake.suzume.data.APIInferenceRequest
import io.teammistake.suzume.data.APIResponse
import io.teammistake.suzume.services.MessageQueueService
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.*
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
class APIController {
    @Autowired lateinit var messageQueueService: MessageQueueService;
    @PostMapping("/generate", produces = [APPLICATION_NDJSON_VALUE, TEXT_EVENT_STREAM_VALUE], consumes = [APPLICATION_JSON_VALUE])
    suspend fun generateResponse(@RequestBody request: APIInferenceRequest, @RequestHeader("User-ID") uid: String?): Flux<Any> {
        if (!request.stream) throw IllegalArgumentException("Invalid Accept Header, should be either $APPLICATION_NDJSON_VALUE or $TEXT_EVENT_STREAM_VALUE")
        val pair = messageQueueService.request(request, uid);
        return Flux.concat(Mono.just(pair.second), pair.first);
    }

    @PostMapping("/generate", produces = [ APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    suspend fun generateResponseJson(@RequestBody request: APIInferenceRequest, @RequestHeader("User-ID") uid: String?): APIResponse {
        if (request.stream) throw IllegalArgumentException("Invalid Accept Header, should be $APPLICATION_JSON_VALUE")

        val pair = messageQueueService.request(request, uid);
        val resp = pair.first.last().awaitSingleOrNull();
        return APIResponse(pair.second.reqId, pair.second.model, resp?.respFull);
    }
}