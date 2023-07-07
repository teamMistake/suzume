package io.teammistake.suzume.repository

import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository

@Repository
interface AIGenerationRepository: ReactiveMongoRepository<AIGeneration, String>
