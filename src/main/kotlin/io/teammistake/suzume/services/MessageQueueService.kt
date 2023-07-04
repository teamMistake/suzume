package io.teammistake.suzume.services

import io.teammistake.suzume.data.APIInferenceRequest
import io.teammistake.suzume.data.APIResponseHeader
import io.teammistake.suzume.data.InferenceRequest
import io.teammistake.suzume.data.InferenceResponse
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
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.suspendCoroutine

@Service
class MessageQueueService {

    @Value(value = "#{environment.KAFKA_REQ_TOPIC}")
    lateinit var producerTopic: String;

    @Value(value = "#{environment.KAFKA_RESP_TOPIC}")
    lateinit var consumerTopic: String;

    val correlationMap: MutableMap<String, Request> = ConcurrentHashMap();


    data class Request(
        val req: APIInferenceRequest,
        val reqId: String,
        val sink: FluxSink<String>,
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

        return Pair(flux, APIResponseHeader(reqId, req.model));
    }

    fun response(resp: ConsumerRecord<String, InferenceResponse>) {
        val reqId = resp.headers().lastHeader("req_id").value().toString(Charset.defaultCharset())
        val req = correlationMap[reqId];
        if (req != null) {
            val sink = req.sink;

            var value = resp.value().respPartial;
            if (value == null) value = resp.value().respFull;

            if (value != null)
                sink.next(value);
            else sink.error(IllegalArgumentException("Inference server returned $resp for $req / ID: ${req.reqId}"))
            // TODO: better exception
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
        result.setupMessageListener(this::response as MessageListener<String, InferenceResponse>);
        return result;
    }

}