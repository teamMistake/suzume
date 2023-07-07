package io.teammistake.suzume.services

import io.teammistake.suzume.data.*
import io.teammistake.suzume.exception.InferenceServerResponseException
import io.teammistake.suzume.exception.RequestTimeoutException
import io.teammistake.suzume.repository.AIGeneration
import io.teammistake.suzume.repository.AIGenerationRepository
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.kafka.support.TopicPartitionOffset
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import java.nio.charset.Charset
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

@Service
class MessageQueueService: MessageListener<String, InferenceResponse> {

    @Value(value = "#{environment.KAFKA_REQ_TOPIC ?: ''}")
    lateinit var producerTopic: String;

    @Value(value = "#{environment.KAFKA_RESP_TOPIC ?: ''}")
    lateinit var consumerTopic: String;

    @Value(value = "#{environment.FIRST_TIMEOUT}")
    lateinit var timeout: Integer;

    @Value(value = "#{environment.MODELS}")
    lateinit var models: String;

    val correlationMap: MutableMap<String, Request> = ConcurrentHashMap();

    val model by lazy {
        models.split(",").toSet()
    }

    data class Request(
        val req: APIInferenceRequest,
        val reqId: String,
        val sink: FluxSink<InferenceResponse>,
        @Volatile var received: Boolean = false
    );

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, InferenceRequest>;
    @Autowired lateinit var aiGenerationRepository: AIGenerationRepository;
    suspend fun request(req: APIInferenceRequest, uid: String?): Pair<Flux<InferenceResponse>, APIResponseHeader> {
        require(req.maxToken <= 0) {"Max token must be posistive: ${req.maxToken}"}


        val reqId = UUID.randomUUID().toString();

        if (!model.contains(req.model)) {
            throw IllegalArgumentException("Invalid Model: ${req.model}, must be one of $models")
        }

        var aiGeneration = AIGeneration(
            id = reqId,
            req = req.req,
            context = req.context,
            model = req.model,
            maxToken = req.maxToken,
            stream =  req.stream,
            uid= uid,
            timestamp = Instant.now(),
            response = false
        );
        aiGeneration = aiGenerationRepository.save(aiGeneration).awaitSingleOrNull()!!;




        val flux = Flux.create {
            correlationMap[reqId] = Request(
                req,
                reqId,
                it
            );
        };

        val result = kafkaTemplate.send(
            ProducerRecord<String, InferenceRequest>(
                producerTopic,
                null,
                InferenceRequest(req.req, req.context, req.stream, req.maxToken)
            ).apply {
                headers().add("target_model", req.model.toByteArray(Charset.defaultCharset()))
                headers().add("req_id", reqId.toByteArray(Charset.defaultCharset()))
            }
        ).await()

        val withTimeout = flux
            .timeout(Mono.just(0L).delayElement(Duration.ofMillis(timeout.toLong())))
            .doOnError {
                if (it is TimeoutException) {
                    correlationMap.remove(reqId)
                }
            }
            .onErrorResume({t -> t is TimeoutException}, {_ -> Mono.error(RequestTimeoutException(req, reqId))})
            .doFinally {
                correlationMap.remove(reqId)
            }.share()
        val start = System.currentTimeMillis();


        withTimeout
            .onErrorResume { Mono.just(InferenceResponse("", null, true, it.message)) }
            .reduce { s: InferenceResponse, s1: InferenceResponse -> InferenceResponse(s.respPartial + s1.respPartial, s1.respFull ?: s.respFull, s1.eos, s1.error) }
            .flatMap {
                aiGeneration.resp = it.respPartial
                aiGeneration.error = it.error
                aiGeneration.response = true;
                aiGeneration.respMillis = (System.currentTimeMillis() - start).toInt()
                aiGenerationRepository.save(
                    aiGeneration
                );
            }.subscribe()

        return Pair(withTimeout, APIResponseHeader(reqId, req.model));
    }

    override fun onMessage(resp: ConsumerRecord<String, InferenceResponse>) {
        val reqId = resp.headers().lastHeader("req_id").value().toString(Charset.defaultCharset())
        val req = correlationMap[reqId];
        if (req != null) {
            req.received = true;
            val sink = req.sink;

            var value = resp.value().respPartial;
            if (value == null) value = resp.value().respFull;

            if (value != null) sink.next(resp.value());
            else sink.error(InferenceServerResponseException(req.req, req.reqId, resp))
            if (resp.value().eos) {
                sink.complete()
                correlationMap.remove(reqId)
            }
        }
    }

    @Bean
    fun respListener(consumerFactory: ConsumerFactory<String, InferenceResponse>): MessageListenerContainer {
        val cProps: ContainerProperties = ContainerProperties(
            TopicPartitionOffset(consumerTopic, 0)
        );
        val result: KafkaMessageListenerContainer<String, InferenceResponse> = KafkaMessageListenerContainer(consumerFactory,
            cProps);
        result.setupMessageListener(this);
        return result;
    }

}