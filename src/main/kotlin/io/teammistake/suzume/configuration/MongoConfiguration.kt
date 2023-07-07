package io.teammistake.suzume.configuration

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories


@EnableReactiveMongoRepositories
@Configuration
class MongoConfiguration: AbstractReactiveMongoConfiguration() {

    @Value("#{environment.MONGO_URL}")
    lateinit var mongoUrl: String;

    @Value("#{environment.MONG_DATABASE_NAME}")
    lateinit var databaseName: String;

    @Bean
    fun mongoClient(): MongoClient {
        return MongoClients
            .create(mongoUrl)
    }

    override fun getDatabaseName(): String {
        return databaseName
    }


    @Bean
    fun reactiveMongoTemplate(mongoClient: MongoClient): ReactiveMongoTemplate? {
        return ReactiveMongoTemplate(mongoClient, databaseName)
    }
}