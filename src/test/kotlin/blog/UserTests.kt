package blog

import blog.Constants.email
import blog.Constants.name
import blog.Constants.password
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.runApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.body
import org.valiktor.ConstraintViolationException
import reactor.core.publisher.Mono
import java.time.LocalDate

// See https://www.callicoder.com/spring-5-reactive-webclient-webtestclient-examples/

class UserTests {
    @Test
    fun validations() {
        val user = CreateUser("tester@test.er", "Tester", "welkom123", "1999-10-01")
        assertThat(user.toUser().born).isEqualTo(LocalDate.of(1999, 10, 1))

        assertThrows<ConstraintViolationException> { CreateUser("tester@test.er", "Tester", "De Tester", "1999-10-01") }
        assertThrows<ConstraintViolationException> { CreateUser("", "Tester", "De Tester", "1999-10-01") }
        assertThrows<ConstraintViolationException> { CreateUser("tester@test.er", "", "De Tester", "1999-10-01") }
        assertThrows<ConstraintViolationException> { CreateUser("tester@test.er", "Tester", "", "1999-10-01") }
        assertThrows<ConstraintViolationException> { CreateUser("tester@test.er", "Tester", "De Tester", "1899-10-01") }
    }
}

class UserWebTests() {

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
    internal fun `register, login, get and logout`() {
        val cu = CreateUser(email, name,  password, "1999-09-01")
        client.post()
              .uri("/api/register")
              .body(Mono.just(cu))
              .exchange()
              .expectStatus().isOk

        val response = client.post()
              .uri("/api/login")
              .body(Mono.just(UserCredentials(email, password)))
              .exchange()
              .expectStatus().isOk
              .returnResult(Token::class.java)

        assertThat(response).isNotNull
        assertThat(response.responseBody).isNotNull
        val userToken = response.responseBody.blockFirst()
        assertThat(userToken?.email).isEqualTo(email)
        val token = userToken?.token ?: "prut"

        client.get()
              .uri("/api/users")
              .exchange()
              .expectStatus().isOk

        client.get()
              .uri("/api/logout")
              .header("Authorization", token)
              .exchange()
              .expectStatus().isNoContent
    }

    @Test
    fun `register with plain json`() {
        client.post()
              .uri("/api/register")
              .bodyValue(
                  """{"email":"ab@cd.ef","name":"ab","password":"password","born":"2001-09-11"}"""
              )
              .header("Content-Type", "application/json")
              .exchange()
              .expectStatus().isOk
    }

    @Test
    fun `find one with existing id`() {
        client.get()
              .uri("/api/user/$email")
              .exchange()
              .expectStatus().isOk
              .expectBody(UserResponse::class.java)
    }

    @Test
    fun `find no one without passing id`() {
        client.get()
              .uri("/api/user")
              .exchange()
              .expectStatus().isNotFound            // this path does not exist, so 404 is correct
        client.get()
              .uri("/api/user/")
              .exchange()
              .expectStatus().isNotFound            // this path does not exist, so 404 is correct
    }

    @Test
    fun `find no one with non-email id`() {
        client.get()
              .uri("/api/user/xyz")
              .exchange()
              .expectStatus().isBadRequest
    }
}
