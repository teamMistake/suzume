package io.teammistake.suzume

import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories

@SpringBootApplication
@EnableReactiveMongoRepositories
@EnableMongoRepositories
@EnableAutoConfiguration(exclude=[MongoAutoConfiguration::class])
class SuzumeApplication

fun main(args: Array<String>) {
	runApplication<SuzumeApplication>(*args)
}
