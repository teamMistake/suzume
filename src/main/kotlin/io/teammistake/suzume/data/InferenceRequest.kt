package io.teammistake.suzume.data

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy::class)
data class InferenceRequest(
    val req: String,
    val context: String,
    val stream: Boolean,
    val maxToken: Int
);


@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy::class)
data class APIInferenceRequest(
    val req: String,
    val context: String,
    val stream: Boolean,
    val maxToken: Int,
    val model: String
);

