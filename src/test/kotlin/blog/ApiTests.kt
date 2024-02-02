package blog

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.actor.typed.ActorRef
import blog.model.Command
import blog.model.NoteResponse
import blog.model.OAuthToken
import blog.model.UserResponse
import blog.read.Reader
import blog.write.Processor
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
    """akka.persistence.journal.plugin = "akka.persistence.journal.inmem" 
       akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"  
       akka.persistence.snapshot-store.local.dir = "build/snapshot-${UUID.randomUUID()}"  
    """)

  private var ub: ActorRef<Command>? = null
  private var app: ConfigurableApplicationContext? = null
  private val client = WebTestClient.bindToServer()
    .baseUrl("http://localhost:8181")
    .defaultHeader("Content-Type", "application/json")
    .build()
  private var reader: Reader = Reader()

  @BeforeEach
  fun before() {
    reader = Reader()
    ub = testKit.spawn(Processor.create("1", reader), "user-behavior")
    app = runApplication<Application> {
      addInitializers(beans(ub!!, testKit.system(), reader))
    }
  }

  @AfterEach
  fun after() {
    app?.close()
    testKit.stop(ub)
  }


  @Test
  fun `new user with new note`() {
    val email = "one@test.er"
    client.get("/api/users").exchange().expectStatus().isOk.expectBodyList(UserResponse::class.java).hasSize(0)
    val user = client.post("/api/user")
      .bodyValue("""{"email":"$email","name":"Tester","password":"welkom123","born":"1999-09-11"}""")
      .exchange()
      .expectStatus().isCreated
      .expectBody(UserResponse::class.java)
      .returnResult().responseBody
    assertThat(user).isNotNull
    client.get("/api/users").exchange().expectStatus().isOk.expectBodyList(UserResponse::class.java).hasSize(1)

    client.get("/api/user/id/${user?.id}").exchange().expectStatus().isOk
    client.get("/api/user/email/${user?.email}").exchange().expectStatus().isOk

    val oAuthToken = client.post("/api/login")
      .bodyValue("""{"username":"$email","password":"welkom123"}""")
      .exchange()
      .expectStatus().isOk
      .expectBody(OAuthToken::class.java)
      .returnResult().responseBody

    val note = client.post("/api/note")
      .header("Authorization", "Bearer ${oAuthToken!!.access_token}")
      .bodyValue("""{"user":"${user!!.id}","title":"Titel","body":"Body tekst"}""")
      .exchange()
      .expectStatus().isCreated
      .expectBody(NoteResponse::class.java)
      .returnResult().responseBody

    client.get("/api/note/${note?.id}").exchange().expectStatus().isOk
  }

  @Test
  fun `login with non-existent credentials`() {
    client.get("/api/users").exchange().expectStatus().isOk.expectBodyList(UserResponse::class.java).hasSize(0)
    client.post("/api/login")
      .bodyValue("""{"username":"fout@test.er","password":"welkom124"}""")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `update note`() {
    val email = "two@test.er"
    val user = client.post("/api/user")
      .bodyValue("""{"email":"$email","name":"Tester","password":"welkom123","born":"1999-09-11"}""")
      .exchange()
      .expectStatus().isCreated.expectBody(UserResponse::class.java).returnResult().responseBody
    assertThat(user).isNotNull
    val oAuthToken = client.post("/api/login")
      .bodyValue("""{"username":"$email","password":"welkom123"}""")
      .exchange()
      .expectStatus().isOk.expectBody(OAuthToken::class.java).returnResult().responseBody
    assertThat(oAuthToken).isNotNull

    val note = client.post("/api/note")
      .header("Authorization", "Bearer ${oAuthToken!!.access_token}")
      .bodyValue("""{"user":"${user!!.id}","title":"Titel","body":"Body tekst"}""")
      .exchange()
      .expectStatus().isCreated.expectBody(NoteResponse::class.java).returnResult().responseBody
    assertThat(note).isNotNull

    val user2 = client.get("/api/user/id/${user.id}").exchange().expectStatus().isOk.expectBody(UserResponse::class.java).returnResult().responseBody
    assertThat(user2?.id).isEqualTo(user.id)

    val note2 = client.get("/api/note/${note?.id}").exchange().expectStatus().isOk.expectBody(NoteResponse::class.java).returnResult().responseBody
    assertThat(note2?.id).isEqualTo(note?.id)

    val updated = client.put("/api/note")
      .header("Authorization", "Bearer ${oAuthToken.access_token}")
      .bodyValue("""{"user":"${user.id}","id":"${note?.id}","title":"Nieuwe titel","body":"Nieuwe body tekst"}""")
      .exchange()
      .expectStatus().isOk
      .expectBody(NoteResponse::class.java).returnResult().responseBody
    assertThat(updated).isNotNull
    assertThat(updated!!.title).isEqualTo("Nieuwe titel")
  }
}
