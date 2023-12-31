package io.teammistake.suzume.controllers

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.teammistake.suzume.data.APIError
import io.teammistake.suzume.data.APIInferenceRequest
import io.teammistake.suzume.data.APIResponse
import io.teammistake.suzume.data.FeedbackRequest
import io.teammistake.suzume.exception.InferenceServerResponseException
import io.teammistake.suzume.exception.RequestTimeoutException
import io.teammistake.suzume.services.MessageQueueService
import io.teammistake.suzume.services.StorageService
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.*
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.cast

@RestController
class APIController {
    @Autowired lateinit var messageQueueService: MessageQueueService
    @Autowired lateinit var storageService: StorageService



    @Autowired lateinit var meterRegistry: MeterRegistry


    val streamSuccessCounter by lazy {meterRegistry.counter("suzume_response", listOf(Tag.of("type", "success"), Tag.of("stream", "true")))}
    val streamTimeoutCounter by lazy {meterRegistry.counter("suzume_response", listOf(Tag.of("type", "timeout"), Tag.of("stream", "true")))}
    val streamErrorCounter by lazy {meterRegistry.counter("suzume_response", listOf(Tag.of("type", "error"), Tag.of("stream", "true")))}

    val nonStreamSuccessCounter by lazy {meterRegistry.counter("suzume_response", listOf(Tag.of("type", "success"), Tag.of("stream", "false")))}
    val nonStreamTimeoutCounter by lazy {meterRegistry.counter("suzume_response", listOf(Tag.of("type", "timeout"), Tag.of("stream", "false")))}
    val nonStreamErrorCounter by lazy {meterRegistry.counter("suzume_response", listOf(Tag.of("type", "error"), Tag.of("stream", "false")))}



    @PostMapping("/generate", produces = [APPLICATION_NDJSON_VALUE, TEXT_EVENT_STREAM_VALUE], consumes = [APPLICATION_JSON_VALUE])
    suspend fun generateResponse(@RequestBody request: APIInferenceRequest, @RequestHeader("User-ID") uid: String?): Flux<Any> {
        if (!request.stream) throw IllegalArgumentException("Invalid Accept Header, should be either $APPLICATION_NDJSON_VALUE or $TEXT_EVENT_STREAM_VALUE")
        val pair = messageQueueService.request(request, uid)
        return Flux.concat(Mono.just(pair.second), pair.first
            .cast(Any::class.java)
            .doOnComplete { streamSuccessCounter.increment() }
            .doOnError(RequestTimeoutException::class.java) {streamTimeoutCounter.increment()}
            .doOnError(InferenceServerResponseException::class.java) {streamErrorCounter.increment()}
            .onErrorResume({t -> t is RequestTimeoutException}, {e ->
                e as RequestTimeoutException;
                e.printStackTrace()
                Mono.just(APIError(e.message, e.reqId, e.request))
            })
            .onErrorResume({t -> t is InferenceServerResponseException}, {e ->
                e as InferenceServerResponseException;
                e.printStackTrace()
                println(e.request)
                println(e.resp.toString())
                println("----------------------------------------------")

                Mono.just(APIError(e.message, e.reqId, e.request, e.resp))
            }))
    }

    @PostMapping("/generate", produces = [ APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    suspend fun generateResponseJson(@RequestBody request: APIInferenceRequest, @RequestHeader("User-ID") uid: String?): APIResponse {
        if (request.stream) throw IllegalArgumentException("Invalid Accept Header, should be $APPLICATION_JSON_VALUE")

        val pair = messageQueueService.request(request, uid)
        try {
            val resp = pair.first.last().awaitSingleOrNull()
            nonStreamSuccessCounter.increment()
            return APIResponse(pair.second.reqId, pair.second.model, resp?.respFull)
        } catch (e: RequestTimeoutException) {
            nonStreamTimeoutCounter.increment()
            throw e
        } catch (e: InferenceServerResponseException) {
            nonStreamErrorCounter.increment()
            throw e
        }
    }

    @PutMapping("/requests/{id}/feedback")
    suspend fun giveFeedback(@PathVariable("id") reqId: String,@RequestBody feedbackRequest: FeedbackRequest) {
        require(feedbackRequest.score in 0.0..1.0) {"Score must be in [0.0, 1.0]"}

        storageService.feedback(reqId, feedbackRequest.score)
    }
}