package io.teammistake.suzume.data

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy::class)
data class InferenceResponse(
    val respPartial: String?,
    val respFull: String?,
    val eos: Boolean,
    val error: String? = null
): SuzumeResponse()

@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy::class)
data class APIResponse(
    val reqId: String,
    val model: String,
    val resp: String?
)

@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy::class)
data class APIResponseHeader(
    val reqId: String,
    val model: String
): SuzumeResponse()
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(name="header", value=APIResponseHeader::class),
    JsonSubTypes.Type(name="response", value=InferenceResponse::class)
)
open class SuzumeResponse;