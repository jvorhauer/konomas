package blog

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import blog.model.NoteResponse
import blog.model.UserResponse
import blog.model.UserState
import blog.write.UserBehavior
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.runApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class ApiTests {
  private val testKit = TestKitJunitResource(
    """akka.actor.provider = "cluster"
       akka.persistence.journal.plugin = "akka.persistence.journal.inmem" 
       akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"  
       akka.persistence.snapshot-store.local.dir = "build/snapshot-${UUID.randomUUID()}"  
    """)

  private var app: ConfigurableApplicationContext? = null
  private val client = WebTestClient.bindToServer()
    .baseUrl("http://localhost:8181")
    .defaultHeader("Content-Type", "application/json")
    .build()

  @BeforeEach
  fun before() {
    val state= UserState()
    val ub = testKit.spawn(UserBehavior.create(state, "1"), "user-behavior")
    app = runApplication<Application> {
      addInitializers(beans(ub, testKit.system(), state))
    }
  }

  @AfterEach
  fun after() {
    app?.close()
  }


  @Test
  fun `new user with new note`() {
    client.get("/api/users").exchange().expectStatus().isOk.expectBodyList(UserResponse::class.java).hasSize(0)
    val user = client.post("/api/user")
      .bodyValue("""{"email":"test@test.er","name":"Tester","password":"welkom123","born":"1999-09-11"}""")
      .exchange()
      .expectStatus().isCreated
      .expectBody(UserResponse::class.java)
      .returnResult().responseBody
    assertThat(user).isNotNull
    client.get("/api/users").exchange().expectStatus().isOk.expectBodyList(UserResponse::class.java).hasSize(1)

    val session = client.post("/api/login")
      .bodyValue("""{"username":"test@test.er","password":"welkom123"}""")
      .exchange()
      .expectStatus().isOk
      .expectBody(String::class.java)
      .returnResult().responseBody

    client.post("/api/login")
      .bodyValue("""{"username":"test@test.er","password":"welkom124"}""")
      .exchange()
      .expectStatus().isForbidden

    val note = client.post("/api/note/${user?.id}")
      .header("X-Auth", session)
      .bodyValue("""{"user":"${user?.id}","title":"Titel","body":"Body tekst"}""")
      .exchange()
      .expectStatus().isCreated
      .expectBody(NoteResponse::class.java)
      .returnResult().responseBody

    client.get("/api/note/${note?.id}").exchange().expectStatus().isOk
  }
}
