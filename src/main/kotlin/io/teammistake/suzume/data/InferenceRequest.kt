package io.teammistake.suzume.data

data class InferenceRequest(
    val req: String,
    val context: String,
    val stream: Boolean,
    val maxToken: Int
);


data class APIInferenceRequest(
    val req: String,
    val context: String,
    val stream: Boolean,
    val maxToken: Int,
    val model: String
);

