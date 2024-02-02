package blog.model

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.pattern.StatusReply
import blog.Constants.born
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
    val r = RegisterUserRequest("jurjen@vorhauer.nl", "a", "abcdefghij", born)
    assertThat(r.validate()).isEmpty()
    val c = r.toCommand(probe)
    assertThat(c.email).isEqualTo(r.email)
    assertThat(c.name).isEqualTo(r.name)
    assertThat(c.password).isEqualTo(r.password)
    assertThat(c.born).isEqualTo(r.born)
  }

  @Test
  fun `command to event`() {
    val c = CreateUser(nextId(), "a@b.c", "a", "p", born, probe)
    val e = c.toEvent()
    assertThat(e.email).isEqualTo(c.email)
    assertThat(e.name).isEqualTo(c.name)
    assertThat(e.born).isEqualTo(c.born)
    assertThat(e.id).isNotNull
    assertThat(e.password).isEqualTo(Hasher.hash(c.password))
  }

  @AfterAll
  fun cleanup() {
    testKit.system().terminate()
  }
}
