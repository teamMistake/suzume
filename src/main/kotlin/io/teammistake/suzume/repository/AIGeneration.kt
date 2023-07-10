package io.teammistake.suzume.repository

import io.teammistake.suzume.data.APIInferenceRequest
import io.teammistake.suzume.data.ContextPart
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Document(collection = "ai-generation")
class AIGeneration(
    @Id val id: String,
    val request: APIInferenceRequest,
    val uid: String?,
    val timestamp: Instant,
    var response: Boolean,
    var error: String? = null,
    var respMillis: Int = -1,
    var resp: String? = null,
    var feedbackScore: Double? = null,
    var feedback: Boolean = false
)
