package io.teammistake.suzume

import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories

@SpringBootApplication(exclude=[
//	MongoAutoConfiguration::class,
//	MongoDataAutoConfiguration::class,
//	MongoReactiveAutoConfiguration::class,
//	MongoReactiveDataAutoConfiguration::class
])
class SuzumeApplication

fun main(args: Array<String>) {
	runApplication<SuzumeApplication>(*args)
}
