package blog.model

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.pattern.StatusReply
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.util.UUID

class UserTests {

  private val testKit = TestKitJunitResource(
    """akka.persistence.journal.plugin = "akka.persistence.journal.inmem" 
       akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"  
       akka.persistence.snapshot-store.local.dir = "build/snapshot-${UUID.randomUUID()}"  
    """
  )
  private val probe = testKit.createTestProbe<StatusReply<UserResponse>>().ref

  @Test
  fun `request to command`() {
    val r = RegisterUserRequest("jurjen@vorhauer.nl", "a", "abcdefghij")
    val c = r.toCommand(probe)
    assertThat(c.email).isEqualTo(r.email)
    assertThat(c.name).isEqualTo(r.name)
    assertThat(c.password).isEqualTo(r.password)
  }

  @Test
  fun `command to event`() {
    val c = CreateUser("a@b.c", "a", "p", probe)
    val e = c.toEvent()
    assertThat(e.email).isEqualTo(c.email)
    assertThat(e.name).isEqualTo(c.name)
    assertThat(e.id).isNotNull
    assertThat(e.password).isEqualTo(Hasher.hash(c.password))
  }

  @Test
  fun `event to entity`() {
    val uc = UserCreated(1L, "jurjen@vorhauer.nl", "Jurjen", "password")
    val u = uc.toEntity()
    assertThat(u.id).isEqualTo(uc.id)
    assertThat(u.email).isEqualTo(uc.email)
    assertThat(u.name).isEqualTo(uc.name)
    assertThat(u.password).isEqualTo(uc.password)
    assertThat(u.gravatar).isEqualTo("6c8e85364ba2cae4fc908189bee6fa566f148957c42dd778c1cd6e0af03cb0aa")
  }

  @AfterAll
  fun cleanup() {
    testKit.system().terminate()
  }
}
