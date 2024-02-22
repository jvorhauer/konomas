package blog

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.runBlocking
import blog.model.Counts
import blog.model.TaskResponse
import blog.model.UserResponse

class ApiTests {

  private val client: HttpClient = HttpClient(CIO) {
    install(ClientContentNegotiation) {
      jackson {
        registerModules(JavaTimeModule())
      }
    }
  }

  @BeforeAll
  fun before() {
    MainTest.run()
    var index = 0
    while (!MainTest.ready && index < 20) {
      Thread.sleep(100)
      index++
    }
  }

  @AfterAll
  fun after() {
    MainTest.stop()
    client.close()
  }

  @Test
  fun `register and retrieve new user`() {
    runBlocking {
      val email = "one@test.er"
      var response: HttpResponse = client.post("http://localhost:8181/api/users") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"$email","name":"Tester","password":"welkom123"}""")
      }
      assertThat(response.status.value).isEqualTo(201)
      assertThat(response.headers["Location"]).startsWith("/api/users")
      val loc = response.headers["Location"]
      response = client.get("http://localhost:8181$loc")
      assertThat(response.status.value).isEqualTo(200)
      assertThat(response.headers["Content-Type"]).isEqualTo("application/json; charset=UTF-8")
      val ur = response.body<UserResponse>()
      assertThat(ur.name).isEqualTo("Tester")
      assertThat(ur.email).isEqualTo(email)
    }
  }

  @Test
  fun `try to register user with bad json`() {
    runBlocking {
      var response = client.post("http://localhost:8181/api/users") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"bad email address @ where.here","name":"Tester","password":"welkom123"}""")
      }
      assertThat(response.status.value).isEqualTo(400)
      assertThat(response.body<String>()).isEqualTo("invalid email address")

      response = client.post("http://localhost:8181/api/users") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"good@where.here","name":"","password":"welkom123"}""")
      }
      assertThat(response.status.value).isEqualTo(400)
      assertThat(response.body<String>()).isEqualTo("invalid name")

      response = client.post("http://localhost:8181/api/users") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"good@where.here","name":"Toaster","password":""}""")
      }
      assertThat(response.status.value).isEqualTo(400)
      assertThat(response.body<String>()).isEqualTo("invalid password")

      response = client.post("http://localhost:8181/api/users") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"good@where.here","name":"Toaster","password":"passwo"}""")
      }
      assertThat(response.status.value).isEqualTo(400)
      assertThat(response.body<String>()).isEqualTo("password too short")

      response = client.post("http://localhost:8181/api/users") {
        contentType(ContentType.Application.Json)
        setBody("""{"eemail":"good@where.here","name":"Toaster","password":"passwo"}""")
      }
      assertThat(response.status.value).isEqualTo(400)
      assertThat(response.body<String>()).isEqualTo("invalid json in request body")

      response = client.post("http://localhost:8181/api/users") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"good@where.here","naame":"Toaster","password":"passwo"}""")
      }
      assertThat(response.status.value).isEqualTo(400)
      assertThat(response.body<String>()).isEqualTo("invalid json in request body")
    }
  }

  @Test
  fun `try to login`() {
    runBlocking {
      val email = "login@here.now"
      var response: HttpResponse = client.post("http://localhost:8181/api/users") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"$email","name":"Tester","password":"welkom123"}""")
      }
      assertThat(response.status.value).isEqualTo(201)

      response = client.post("http://localhost:8181/api/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"username":"$email","password":"welkom123"}""")
      }
      assertThat(response.status.value).isEqualTo(200)
      assertThat(response.headers["Content-Type"]).isEqualTo("application/json; charset=UTF-8")
      val token = response.body<Token>()
      assertThat(token).isNotNull

      response = client.get("http://localhost:8181/api/users/tasks") {
        header("Authorization", "Bearer ${token.token}")
      }
      assertThat(response.status.value).isEqualTo(200)
    }
  }

  @Test
  fun `fail to login`() {
    runBlocking {
      val email = "fail@to.login"
      var response: HttpResponse = client.post("http://localhost:8181/api/users") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"$email","name":"Tester","password":"welkom123"}""")
      }
      assertThat(response.status.value).isEqualTo(201)

      response = client.post("http://localhost:8181/api/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"username":"$email","password":""}""")
      }
      assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
      assertThat(response.body<String>()).isEqualTo("invalid password")

      response = client.post("http://localhost:8181/api/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"username":"$email","password":"verkeerd!"}""")
      }
      assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
      assertThat(response.body<String>()).isEqualTo("username or password not correct")

      response = client.post("http://localhost:8181/api/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"username":"","password":"password"}""")
      }
      assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
      assertThat(response.body<String>()).isEqualTo("invalid username")

      response = client.post("http://localhost:8181/api/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"username":"","password":""}""")
      }
      assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
      assertThat(response.body<String>()).isEqualTo("invalid username")

      response = client.post("http://localhost:8181/api/login") {
        contentType(ContentType.Application.Json)
        setBody("""{}""")
      }
      assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }
  }

  @Test
  fun `check some nice info`() {
    runBlocking {
      var response = client.get("http://localhost:8181/info/ready")
      assertThat(response.status).isEqualTo(HttpStatusCode.OK)

      response = client.get("http://localhost:8181/info/alive")
      assertThat(response.status).isEqualTo(HttpStatusCode.OK)

      response = client.get("http://localhost:8181/info/counts")
      assertThat(response.status).isEqualTo(HttpStatusCode.OK)
      val counts = response.body<Counts>()
      assertThat(counts.users).isGreaterThanOrEqualTo(0)
      assertThat(counts.notes).isGreaterThanOrEqualTo(0)
      assertThat(counts.tasks).isGreaterThanOrEqualTo(0)

      response = client.get("http://localhost:8181/info/stati")
      assertThat(response.status).isEqualTo(HttpStatusCode.OK)
      val stati: Stati = response.body<Stati>()
      assertThat(stati).isNotNull
      assertThat(stati.stati).hasSize(4)
    }
  }

  @Test
  fun `make a task`() {
    runBlocking {
      val email = "todo@test.er"
      var response: HttpResponse = client.post("http://localhost:8181/api/users") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"$email","name":"Tester","password":"welkom123"}""")
      }
      assertThat(response.status.value).isEqualTo(201)
      val loc = response.headers["Location"]

      response = client.post("http://localhost:8181/api/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"username":"$email","password":"welkom123"}""")
      }
      assertThat(response.status.value).isEqualTo(200)
      assertThat(response.headers["Content-Type"]).isEqualTo("application/json; charset=UTF-8")
      val token = response.body<Token>()
      assertThat(token).isNotNull

      response = client.get("http://localhost:8181/api/users/tasks") {
        header(HttpHeaders.Authorization, "Bearer ${token.token}")
      }
      assertThat(response.status.value).isEqualTo(200)

      response = client.post("http://localhost:8181/api/tasks") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Authorization, "Bearer ${token.token}")
        setBody("""{"title": "Title", "body": "Body Text", "due":"2025-01-01T00:00:00"}""")
      }
      assertThat(response.status.value).isEqualTo(201)
      val body = response.body<TaskResponse>()
      assertThat(body.title).isEqualTo("Title")

      response = client.get("http://localhost:8181$loc")
      assertThat(response.status).isEqualTo(HttpStatusCode.OK)
      val ur = response.body<UserResponse>()
      assertThat(ur.tasks).hasSize(1)

      response = client.get("http://localhost:8181/info/counts")
      println(response.body<Counts>())

      response = client.put("http://localhost:8181/api/tasks") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Authorization, "Bearer ${token.token}")
        setBody("""{"id": ${body.id},"status":"DOING"}""")
      }
      println("response: $response")
    }
  }
}

data class Token(val token: String)
data class Stati(val stati: List<String>)
