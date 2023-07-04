package io.teammistake.suzume.exception

import io.teammistake.suzume.data.APIInferenceRequest
import io.teammistake.suzume.data.InferenceRequest
import io.teammistake.suzume.data.InferenceResponse
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.lang.RuntimeException

class InferenceServerResponseException(
    val request: APIInferenceRequest,
    val reqId: String,
    val resp: ConsumerRecord<String, InferenceResponse>
): RuntimeException("Inference server returned weird response: $resp") {}