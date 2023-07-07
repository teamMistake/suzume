package io.teammistake.suzume.repository

import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux

@Repository
interface AIGenerationRepository: ReactiveMongoRepository<AIGeneration, String> {
    fun findAllByResponse(response: Boolean): Flux<AIGeneration>;
}
