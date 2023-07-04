package io.teammistake.suzume.configuration

import io.teammistake.suzume.data.InferenceRequest
import io.teammistake.suzume.data.InferenceResponse
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.*
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.config.SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG
import org.apache.kafka.common.security.plain.PlainLoginModule
import org.apache.kafka.common.security.scram.ScramLoginModule
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer


//@EnableKafka


@Configuration
class KafkaConfiguration {
    @Value(value = "#{environment.KAFKA_BOOTSTRAP_ADDRESS ?: ''}")
    lateinit var bootstrapAddress: String;

    @Value(value = "#{environment.KAFKA_USERNAME ?: ''}")
    lateinit var username: String;

    @Value(value = "#{environment.KAFKA_PASSWORD ?: ''}")
    lateinit var password: String;

    @Value(value = "#{environment.NODE_ID ?: ''}")
    lateinit var nodeId: String;



    @Bean
    fun requestProducerFactory(): ProducerFactory<String, InferenceRequest> {
        return mapOf<String, Any?>(
            BOOTSTRAP_SERVERS_CONFIG to bootstrapAddress,
            KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            SaslConfigs.SASL_MECHANISM to "SCRAM-SHA-512",
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SASL_PLAINTEXT",
            SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
            SaslConfigs.SASL_JAAS_CONFIG to "${ScramLoginModule::class.java.name} required username=\"${username}\" password=\"${password}\";"
        ).let(::DefaultKafkaProducerFactory)
    }

    @Bean
    fun requestKafkaTemplate(): KafkaTemplate<String, InferenceRequest> = KafkaTemplate(requestProducerFactory())

    @Bean
    fun responseConsumerFactory(): ConsumerFactory<String, InferenceResponse> {
        val props =  mapOf<String, Any?>(
            BOOTSTRAP_SERVERS_CONFIG to bootstrapAddress,
            KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
            SaslConfigs.SASL_MECHANISM to "SCRAM-SHA-512",
            ConsumerConfig.GROUP_ID_CONFIG to "gateway-$nodeId",
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SASL_PLAINTEXT",
            SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
            SaslConfigs.SASL_JAAS_CONFIG to "${ScramLoginModule::class.java.name} required username=\"${username}\" password=\"${password}\";"
        )

        return DefaultKafkaConsumerFactory(props, StringDeserializer(), JsonDeserializer(InferenceResponse::class.java))
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, InferenceResponse> {
        return ConcurrentKafkaListenerContainerFactory<String, InferenceResponse>().apply {
            consumerFactory = responseConsumerFactory()
        }
    }
}