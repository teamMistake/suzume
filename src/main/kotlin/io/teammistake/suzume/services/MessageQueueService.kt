package io.teammistake.suzume.services

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.teammistake.suzume.data.*
import io.teammistake.suzume.exception.InferenceServerResponseException
import io.teammistake.suzume.exception.RequestTimeoutException
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactor.mono
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
        val sink: FluxSink<InferenceResponse2>,
        @Volatile var received: Boolean = false,
        val timer: Timer.Sample
    );

    @Autowired lateinit var storageService: StorageService;

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, InferenceRequest>;


    @Autowired lateinit var meterRegistry: MeterRegistry


    @PostConstruct
    fun registerConcurrent() {
        meterRegistry.gauge("suzume_concurrent", this) {t -> t.correlationMap.size.toDouble()}
    }
    val firstResponseSuccess by lazy {
        Timer.builder("suzume_stream_first")
            .tag("type", "success")
            .publishPercentiles(0.3, 0.5, 0.95)
            .publishPercentileHistogram()
            .register(meterRegistry)
    }
    val firstResponseTimeout by lazy {
        Timer.builder("suzume_stream_first")
            .tag("type", "timeout")
            .publishPercentiles(0.3, 0.5, 0.95)
            .publishPercentileHistogram()
            .register(meterRegistry)
    }
    val endResponseSuccess  by lazy {
        Timer.builder("suzume_stream_end")
            .tag("type", "success")
            .publishPercentiles(0.3, 0.5, 0.95)
            .publishPercentileHistogram()
            .register(meterRegistry)
    }

    suspend fun request(req: APIInferenceRequest, uid: String?): Pair<Flux<InferenceResponse2>, APIResponseHeader> {
        require(req.maxToken > 0) {"Max token must be posistive: ${req.maxToken}"}
        require( 0 < req.temperature && req.temperature <= 1) {"Temperature must be in (0, 1]"}
        require(req.topK > 0) {"Top k must be positive"}
        require(!req.req.isEmpty()) {"Request can not be empty"}

        val reqId = UUID.randomUUID().toString();

        if (!model.contains(req.model)) {
            throw IllegalArgumentException("Invalid Model: ${req.model}, must be one of $models")
        }

        storageService.createNewRequest(reqId, req, uid)




        val flux = Flux.create {
            correlationMap[reqId] = Request(
                req,
                reqId,
                it,
                false,
                Timer.start(meterRegistry)
            );
        };

        val result = kafkaTemplate.send(
            ProducerRecord<String, InferenceRequest>(
                producerTopic,
                null,
                InferenceRequest(req.req, req.context, req.stream, req.maxToken, req.temperature, req.topK)
            ).apply {
                headers().add("target_model", req.model.toByteArray(Charset.defaultCharset()))
                headers().add("req_id", reqId.toByteArray(Charset.defaultCharset()))
            }
        ).await()

        val withTimeout = flux
            .timeout(Mono.just(0L).delayElement(Duration.ofMillis(timeout.toLong())))
            .doOnError {
                if (it is TimeoutException) {
                    correlationMap[reqId]?.timer?.stop(firstResponseTimeout)
                    correlationMap.remove(reqId)
                }
            }
            .onErrorResume({t -> t is TimeoutException}, {_ -> Mono.error(RequestTimeoutException(req, reqId))})
            .doFinally {
                correlationMap.remove(reqId)
            }.share()
        val start = System.currentTimeMillis();


        withTimeout
            .onErrorResume { Mono.just(InferenceResponse2("", null, true, it.message)) }
            .reduce { s: InferenceResponse2, s1: InferenceResponse2 -> InferenceResponse2(s.respPartial + s1.respPartial, s1.respFull ?: s.respFull, s1.eos, s1.error) }
            .flatMap {
                mono {
                    if (it.error != null)
                        storageService.error(
                            reqId = reqId,
                            elapsedTime = (System.currentTimeMillis() - start).toInt(),
                            error = it.error,
                            resp = it.respFull
                        )
                    else
                        storageService.editResponse(
                            reqId = reqId,
                            elapsedTime = (System.currentTimeMillis() - start).toInt(),
                            resp = it.respFull);
                }
            }.subscribe()

        return Pair(withTimeout, APIResponseHeader(reqId, req.model));
    }

    override fun onMessage(resp: ConsumerRecord<String, InferenceResponse>) {
        val reqId = resp.headers().lastHeader("req_id").value().toString(Charset.defaultCharset())
        val req = correlationMap[reqId];
        if (req != null) {
            if (!req.received) {
                req.timer.stop(firstResponseSuccess)
            }
            req.received = true;
            val sink = req.sink;

            var value = resp.value().respPartial;
            if (value == null) value = resp.value().respFull;

            if (value != null) sink.next(InferenceResponse2(
                resp.value().respPartial,
                resp.value().respFull,
                resp.value().eos,
                resp.value().error
            ));
            else sink.error(InferenceServerResponseException(req.req, req.reqId, resp))
            if (resp.value().eos) {
                sink.complete()
                correlationMap.remove(reqId)
                req.timer.stop(endResponseSuccess)
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