package io.teammistake.suzume.data

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy::class)
data class InferenceRequest(
    val req: String,
    val context: List<ContextPart>,
    val stream: Boolean,
    val maxToken: Int,
    val temperature: Double,
    var timestamp: String,
    val topK: Int
);

data class ContextPart (
    val type: ContextType,
    val message: String
)

enum class ContextType {
    BOT, HUMAN
}


@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy::class)
data class APIInferenceRequest(
    val req: String,
    val context: List<ContextPart>,
    val stream: Boolean,
    val maxToken: Int,
    val model: String,
    val temperature: Double = 0.8,
    val topK: Int = 15
);

