package blog

import evented.ExampleHandler
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.runApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.web.reactive.server.WebTestClient

class ActorTests {
    private val client = WebTestClient.bindToServer()
          .baseUrl("http://localhost:8181")
          .build()

    private lateinit var app: ConfigurableApplicationContext

    @BeforeAll
    fun before() {
        app = runApplication<Application>() {
            addInitializers(beans)
        }
        println(app.getBean(ExampleHandler::class.java).toString())
    }

    @AfterAll
    fun after() {
        app.close()
    }

    @Test
    fun print() {
        client.get()
              .uri("/api/act/World")
              .exchange()
              .expectStatus().isOk
              .expectBody(String::class.java).isEqualTo("Thanks for World")

        client.get()
              .uri("/api/act/Kruidnoten")
              .exchange()
              .expectStatus().isOk
              .expectBody(String::class.java).isEqualTo("Thanks for Kruidnoten")
    }

    @Test
    fun direct() {
        client.get()
              .uri("/api/act/direct/Testing")
              .exchange()
              .expectStatus().isOk
              .expectBody(String::class.java).isEqualTo("Thanks for Testing")
    }

    @Test
    fun persist() {
        client.get()
              .uri("/api/es/persistence")
              .exchange()
              .expectStatus().isOk
              .expectBody(String::class.java).isEqualTo("persistence-0")

        client.get()
              .uri("/api/es/check")
              .exchange()
              .expectStatus().isOk
              .expectBody(String::class.java).isEqualTo("check-1")

        client.get()
              .uri("/api/es/print")
              .exchange()
              .expectStatus().isOk
              .expectBody(String::class.java).isEqualTo("[persistence-0, check-1]")
    }
}
