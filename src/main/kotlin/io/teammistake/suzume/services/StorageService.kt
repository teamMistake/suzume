package io.teammistake.suzume.services

import io.teammistake.suzume.data.APIInferenceRequest
import io.teammistake.suzume.data.InferenceRequest
import io.teammistake.suzume.exception.NotFoundException
import io.teammistake.suzume.repository.AIGeneration
import io.teammistake.suzume.repository.AIGenerationRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class StorageService {
    @Autowired
    lateinit var aiGenerationRepository: AIGenerationRepository;

    suspend fun createNewRequest(reqId: String, req: APIInferenceRequest, uid: String?): AIGeneration {
        var aiGeneration = AIGeneration(
            id = reqId,
            request = req,
            uid= uid,
            timestamp = Instant.now(),
            response = false
        );
        return aiGenerationRepository.save(aiGeneration).awaitSingleOrNull()!!;
    }

    suspend fun editResponse(reqId: String, elapsedTime: Int, resp: String?): AIGeneration? {
        val aiGeneration = aiGenerationRepository.findById(reqId).awaitSingleOrNull() ?: throw NotFoundException("$reqId not found")
        aiGeneration.resp = resp
        aiGeneration.response = true;
        aiGeneration.respMillis = elapsedTime.toInt()
        return aiGenerationRepository.save(aiGeneration).awaitSingleOrNull()

    }

    suspend fun error(reqId: String, elapsedTime: Int, error: String, resp: String?) : AIGeneration? {
        val aiGeneration = aiGenerationRepository.findById(reqId).awaitSingleOrNull() ?: throw NotFoundException("$reqId not found")
        aiGeneration.resp = resp
        aiGeneration.response = false;
        aiGeneration.error = error;
        aiGeneration.respMillis = elapsedTime.toInt()
        return aiGenerationRepository.save(aiGeneration).awaitSingleOrNull()
    }

    suspend fun feedback(reqId: String, score: Double): AIGeneration? {
        val aiGeneration = aiGenerationRepository.findById(reqId).awaitSingleOrNull() ?: throw NotFoundException("$reqId not found")
        aiGeneration.feedback = true
        aiGeneration.feedbackScore = score
        return aiGenerationRepository.save(aiGeneration).awaitSingleOrNull()
    }
}