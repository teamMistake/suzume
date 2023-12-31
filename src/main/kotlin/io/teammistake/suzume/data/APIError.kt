package io.teammistake.suzume.data

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy::class)
data class APIError(
    val error: String?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val reqId: String? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val request: APIInferenceRequest? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val data: Any? = null
): SuzumeResponse()