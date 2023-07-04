package io.teammistake.suzume

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SuzumeApplication

fun main(args: Array<String>) {
	runApplication<SuzumeApplication>(*args)
}
