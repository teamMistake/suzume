package io.teammistake.suzume.controllers

import io.teammistake.suzume.data.APIInferenceRequest
import io.teammistake.suzume.data.APIResponse
import io.teammistake.suzume.services.MessageQueueService
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.*
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
class APIController {
    @Autowired lateinit var messageQueueService: MessageQueueService;
    @PostMapping("/stream", produces = [APPLICATION_NDJSON_VALUE, TEXT_EVENT_STREAM_VALUE])
    suspend fun generateResponse(request: APIInferenceRequest): Flux<Any> {
        if (!request.stream) throw IllegalArgumentException("Invalid Accept Header, should be either $APPLICATION_NDJSON_VALUE or $TEXT_EVENT_STREAM_VALUE")
        val pair = messageQueueService.request(request);
        return Flux.concat(Mono.just(pair.second), pair.first);
    }

    @PostMapping("/stream", produces = [ APPLICATION_JSON_VALUE])
    suspend fun generateResponseJson(request: APIInferenceRequest): APIResponse {
        if (request.stream) throw IllegalArgumentException("Invalid Accept Header, should be $APPLICATION_JSON_VALUE")

        val pair = messageQueueService.request(request);
        val resp = pair.first.reduce { s: String, s1: String -> s+s1 }
            .awaitSingleOrNull();
        return APIResponse(pair.second.reqId, pair.second.model, resp);
    }
}