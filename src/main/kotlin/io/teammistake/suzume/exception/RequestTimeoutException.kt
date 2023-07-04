package io.teammistake.suzume.exception

import io.teammistake.suzume.data.APIInferenceRequest
import io.teammistake.suzume.data.InferenceRequest
import java.lang.RuntimeException

class RequestTimeoutException(
    val request: APIInferenceRequest,
    val reqId: String
): RuntimeException("Request timed out") {}