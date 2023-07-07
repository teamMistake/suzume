package io.teammistake.suzume.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Document(collection = "ai-generation")
class AIGeneration(
    @Id val id: String,
    val req: String,
    val context: String,
    val model: String,
    val maxToken: Int,
    val stream: Boolean,
    val uid: String?,
    val timestamp: Instant,
    var error: String? = null,
    var respMillis: Int = -1,
    var resp: String? = null,
)
