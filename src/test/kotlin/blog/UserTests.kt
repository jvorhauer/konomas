package blog

import blog.UserWebTests.Companion.born
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.mock.web.reactive.function.server.MockServerRequest
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.body
import org.valiktor.ConstraintViolationException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDate
import java.time.Month

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

@SpringBootTest
class UserRepoTests(@Autowired private val repo: UserRepo) {
    @Test
    fun `save and find`() {
        val user = User(email = "repo@test.er", name = "Tester", password = "welkom123", born = born)
        StepVerifier.create(repo.save(user)).expectNextCount(1).verifyComplete()
        StepVerifier.create(repo.findById("repo@test.er")).expectNextCount(1).verifyComplete()
    }
}

@SpringBootTest
class UserHandlerTests(@Autowired private val handler: UserHandler) {
    @Test
    fun `register and login`() {
        val cu = CreateUser("handler@test.er", "Handler", "welkom123",  "1999-09-11")
        val uc = UserCredentials("handler@test.er", "welkom123")

        var req = MockServerRequest.builder().body(Mono.just(cu))
        StepVerifier.create(handler.register(req)).expectNextCount(1).verifyComplete()

        req = MockServerRequest.builder().body(Mono.just(uc))
        StepVerifier.create(handler.login(req)).expectNextCount(1).verifyComplete()
        StepVerifier.create(handler.login(req)).assertNext {
            assertThat(it.statusCode()).isEqualTo(HttpStatus.OK)
        }.verifyComplete()
    }
}


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class UserWebTests(@Autowired private val client: WebTestClient) {

    @Test
    internal fun `register, login, get and logout`() {
        val cu = CreateUser(email, name,  password, "1999-09-01")
        client.post()
              .uri("/register")
              .body(Mono.just(cu))
              .exchange()
              .expectStatus().isOk

        val response = client.post()
              .uri("/login")
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
              .uri("/users")
              .exchange()
              .expectStatus().isOk

        client.get()
              .uri("/logout")
              .header("Authorization", token)
              .exchange()
              .expectStatus().isNoContent
    }

    @Test
    fun `register with plain json`() {
        client.post()
              .uri("/register")
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
              .uri("/user/$email")
              .exchange()
              .expectStatus().isOk
    }

    @Test
    fun `find no one without passing id`() {
        client.get()
              .uri("/user")
              .exchange()
              .expectStatus().isNotFound            // this path does not exist, so 404 is correct
        client.get()
              .uri("/user/")
              .exchange()
              .expectStatus().isNotFound            // this path does not exist, so 404 is correct
    }

    @Test
    fun `find no one with non-email id`() {
        client.get()
              .uri("/user/xyz")
              .exchange()
              .expectStatus().isBadRequest
    }

    companion object {
        const val email = "web@test.er"
        const val password = "welkom123"
        const val name = "Web Tester"
        val  born: LocalDate = LocalDate.of(1999, Month.SEPTEMBER, 1)
    }
}
