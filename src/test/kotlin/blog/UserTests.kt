package blog

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.pattern.StatusReply
import blog.Constants.born
import blog.Constants.email
import blog.Constants.password
import blog.Constants.name
import blog.model.RegisterUser
import blog.model.RegisterUserRequest
import blog.model.UserEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.valiktor.ConstraintViolationException
import java.time.LocalDate
import java.util.UUID

class UserTests {

  private val testKit = TestKitJunitResource(
    """akka.persistence.journal.plugin = "akka.persistence.journal.inmem" 
       akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"  
       akka.persistence.snapshot-store.local.dir = "build/snapshot-${UUID.randomUUID()}"  
    """
  )
  private val probe = testKit.createTestProbe<StatusReply<Response>>().ref

  @Test
  fun `request to command`() {
    try {
      val r = RegisterUserRequest("jurjen@vorhauer.nl", "a", "abcdefghij", born)
      val c = r.toCommand(probe)
      assertThat(c.email).isEqualTo(r.email)
      assertThat(c.name).isEqualTo(r.name)
      assertThat(c.password).isEqualTo(r.password)
      assertThat(c.born).isEqualTo(r.born)
    } catch (e: ConstraintViolationException) {
      e.constraintViolations.map { "${it.property}: ${it.constraint.name}" }.forEach(::println)
    }
  }

  @Test
  fun `command to event`() {
    val c = RegisterUser(UUID.randomUUID(),"a@b.c", "a", "p", born, probe)
    val e = c.toEvent()
    assertThat(e.email).isEqualTo(c.email)
    assertThat(e.name).isEqualTo(c.name)
    assertThat(e.born).isEqualTo(c.born)
    assertThat(e.id).isNotNull
    assertThat(e.joined).isBeforeOrEqualTo(LocalDate.now())
    assertThat(e.password).isEqualTo(RegisterUser.hash(c.password))
  }

  @Test
  fun `event to forward`() {
    val c = UserEvent(UUID.randomUUID(), email, name, password, born)
    val f = c.toForward()
    assertThat(f.id).isEqualTo(c.id)
    assertThat(f.email).isEqualTo(c.email)
  }
}
