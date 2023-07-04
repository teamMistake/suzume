package io.teammistake.suzume.services

import io.teammistake.suzume.data.APIInferenceRequest
import io.teammistake.suzume.data.APIResponseHeader
import io.teammistake.suzume.data.InferenceRequest
import io.teammistake.suzume.data.InferenceResponse
import io.teammistake.suzume.exception.InferenceServerResponseException
import io.teammistake.suzume.exception.RequestTimeoutException
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.kafka.support.SendResult
import org.springframework.kafka.support.TopicPartitionOffset
import org.springframework.stereotype.Service
import org.springframework.util.concurrent.ListenableFutureCallback
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import java.nio.charset.Charset
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import kotlin.coroutines.suspendCoroutine

@Service
class MessageQueueService: MessageListener<String, InferenceResponse> {

    @Value(value = "#{environment.KAFKA_REQ_TOPIC ?: ''}")
    lateinit var producerTopic: String;

    @Value(value = "#{environment.KAFKA_RESP_TOPIC ?: ''}")
    lateinit var consumerTopic: String;

    @Value(value = "#{environment.FIRST_TIMEOUT}")
    lateinit var timeout: Integer;

    val correlationMap: MutableMap<String, Request> = ConcurrentHashMap();


    data class Request(
        val req: APIInferenceRequest,
        val reqId: String,
        val sink: FluxSink<String>,
        @Volatile var received: Boolean = false
    );

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, InferenceRequest>;
    suspend fun request(req: APIInferenceRequest): Pair<Flux<String>, APIResponseHeader> {
        val reqId = UUID.randomUUID().toString();

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
            }

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

            if (value != null)
                sink.next(value);
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