package blog

import blog.Constants.email
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.runApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.data.cassandra.core.query.Criteria
import org.springframework.data.cassandra.core.query.Query
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.body
import org.valiktor.ConstraintViolationException
import reactor.core.publisher.Mono
import java.util.UUID

class PostTests {
    @Test
    fun validations() {
        assertThat(CreatePost("test", "content")).isNotNull
        assertThrows<ConstraintViolationException> { CreatePost("", "empty title") }
        assertThrows<ConstraintViolationException> { CreatePost("ab", "title too short") }
        assertThrows<ConstraintViolationException> { CreatePost("empty body", "") }
    }
}

class WebPostTests {

    private val client = WebTestClient.bindToServer()
          .baseUrl("http://localhost:8181")
          .build()

    private lateinit var app: ConfigurableApplicationContext

    @BeforeAll
    fun before() {
        app = runApplication<Application>() {
            addInitializers(beans)
        }
    }

    @AfterAll
    fun after() = app.close()


    @Test
    fun `login, save and get new post`() {

        // val q = Query.query(Criteria.where("email").isNotNull).limit(5).withAllowFiltering()

        val loggedIn = client.post()
              .uri("/api/login")
              .body(Mono.just(UserCredentials(Constants.email, Constants.password)))
              .exchange()
              .expectStatus().isOk
              .returnResult(Token.klass)
        val userToken = loggedIn.responseBody.blockFirst()
        val token = userToken?.token ?: "prut"

        val posted = client.post()
              .uri("/api/blogs")
              .body(Mono.just(CreatePost("title", "body and content")))
              .header("Authorization", token)
              .exchange()
              .expectStatus().isOk
              .returnResult(PostResponse.klass)
        assertThat(posted).isNotNull
        assertThat(posted.responseBody).isNotNull
        val postedId = posted.responseBody.blockFirst()
        val id = postedId?.id ?: UUID.randomUUID().toString()

        client.get()
              .uri("/api/blog/$id")
              .header("Authorization", token)
              .exchange()
              .expectStatus().isOk
              .expectBody()
              .jsonPath("$.viewed").isEqualTo(0)
              .jsonPath("$.author.name").isEqualTo("Web Tester")

        val res = client.get()
              .uri("/api/blogs/$email")
              .exchange()
              .expectStatus().isOk
              .returnResult(PostResponse.klass)
        assertThat(res).isNotNull
        assertThat(res.responseBody).isNotNull
        res.responseBody.subscribe { println("prs: $it")}
    }
}
