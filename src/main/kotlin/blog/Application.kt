package blog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NoviblogApplication

fun main(args: Array<String>) {
    runApplication<NoviblogApplication>(*args)
}
