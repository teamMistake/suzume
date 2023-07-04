package io.teammistake.suzume.data

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class InferenceResponse(
    val respPartial: String?,
    val respFull: String?,
    val eos: Boolean
)

data class APIResponse(
    val reqId: String,
    val model: String,
    val resp: String?
)

data class APIResponseHeader(
    val reqId: String,
    val model: String
)