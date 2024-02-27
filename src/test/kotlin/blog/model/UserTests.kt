package blog.model

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.pattern.StatusReply
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.util.UUID

class UserTests {

  companion object {
    const val email: String = "jurjen@vorhauer.nl"
    const val name: String = "Jurjen"
    const val password: String = "password"
  }

  private val testKit = TestKitJunitResource(
    """akka.persistence.journal.plugin = "akka.persistence.journal.inmem" 
       akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"  
       akka.persistence.snapshot-store.local.dir = "build/snapshot-${UUID.randomUUID()}"  
    """
  )
  private val probe = testKit.createTestProbe<StatusReply<User>>().ref

  @Test
  fun `request to command`() {
    val r = RegisterUserRequest(email, name, password)
    val c = r.toCommand(probe)
    assertThat(c.email).isEqualTo(r.email)
    assertThat(c.name).isEqualTo(r.name)
    assertThat(c.password).isEqualTo(r.password)
  }

  @Test
  fun `command to event`() {
    val c = CreateUser(email, name, password, probe)
    val e = c.toEvent
    assertThat(e.email).isEqualTo(c.email)
    assertThat(e.name).isEqualTo(c.name)
    assertThat(e.id).isNotNull
    assertThat(e.password).isEqualTo(Hasher.hash(c.password))
  }

  @Test
  fun `event to entity`() {
    val uc = UserCreated(nextId, email, name, password)
    val u = uc.toEntity
    assertThat(u.id).isEqualTo(uc.id)
    assertThat(u.email).isEqualTo(uc.email)
    assertThat(u.name).isEqualTo(uc.name)
    assertThat(u.password).isEqualTo(uc.password)
    assertThat(u.gravatar).isEqualTo("6c8e85364ba2cae4fc908189bee6fa566f148957c42dd778c1cd6e0af03cb0aa")
  }

  @Test
  fun `update entity`() {
    val u = User(nextId, email, name, password.hashed)
    var uu = UpdateUser(u.id, "Anders", null, probe)
    var v = u.update(uu.toEvent)
    assertThat(v.id).isEqualTo(u.id)
    assertThat(v.email).isEqualTo(u.email).isEqualTo(email)
    assertThat(v.name).isEqualTo("Anders")
    assertThat(v.password).isEqualTo(u.password).isEqualTo(password.hashed)

    uu = UpdateUser(u.id, null, "anders!?", probe)
    v = u.update(uu.toEvent)
    assertThat(v.id).isEqualTo(u.id)
    assertThat(v.email).isEqualTo(u.email).isEqualTo(email)
    assertThat(v.name).isEqualTo(u.name).isEqualTo(name)
    assertThat(v.password).isNotEqualTo(u.password).isEqualTo("anders!?".hashed)

    uu = UpdateUser(u.id, null, null, probe)
    v = u.update(uu.toEvent)
    assertThat(v.id).isEqualTo(u.id)
    assertThat(v.email).isEqualTo(u.email).isEqualTo(email)
    assertThat(v.name).isEqualTo(u.name).isEqualTo(name)
    assertThat(v.password).isEqualTo(u.password).isEqualTo(password.hashed)
  }

  @AfterAll
  fun cleanup() {
    testKit.system().terminate()
  }
}
