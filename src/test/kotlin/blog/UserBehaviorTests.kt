package blog

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.pattern.StatusReply
import blog.model.CreateNote
import blog.model.RegisterUser
import blog.model.UserState
import blog.write.UserBehavior
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class UserBehaviorTests {
  private val counter = AtomicInteger(0)
  private val testKit = TestKitJunitResource(
    """akka.persistence.journal.plugin = "akka.persistence.journal.inmem" 
       akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"  
       akka.persistence.snapshot-store.local.dir = "build/snapshot-${UUID.randomUUID()}"  
    """
  )

  private fun aid() = counter.incrementAndGet().toString()

  @Test
  fun `should register user`() {
    val state = UserState()
    val prc = testKit.spawn(UserBehavior.create(state, aid()))
    val probe = testKit.createTestProbe<StatusReply<Response>>()
    val reader = UserReader(state)

    val newid = UUID.randomUUID()
    prc.tell(RegisterUser(newid, "a@b.c","a", "welkom123", LocalDate.of(1967, 4, 1), probe.ref))
    val result = probe.receiveMessage()
    assertThat(result.isSuccess).isTrue
    assertThat(result.value.id).isEqualTo(newid)
    assertThat(result.value.javaClass.simpleName).isEqualTo("UserResponse")
    val ur = reader.find("a@b.c").block()
    assertThat(ur).isNotNull
    assertThat(ur?.id).isEqualTo(result.value.id)
    assertThat(ur?.name).isEqualTo("a")
  }

  @Test
  fun `shoud not register a user twice`() {
    val state = UserState()
    val prc = testKit.spawn(UserBehavior.create(state, aid()))
    val probe = testKit.createTestProbe<StatusReply<Response>>()

    val newid = UUID.randomUUID()
    prc.tell(RegisterUser(newid, "a@b.c","a", "welkom123", LocalDate.of(1967, 4, 1), probe.ref))
    val result = probe.receiveMessage()
    assertThat(result.isSuccess).isTrue
    assertThat(result.value.id).isNotNull

    val otherid = UUID.randomUUID()
    prc.tell(RegisterUser(otherid, "a@b.c","a", "welkom123", LocalDate.of(1967, 4, 1), probe.ref))
    val again = probe.receiveMessage()
    assertThat(again.isError).isTrue
    assertThat(again.error.message).isEqualTo("a@b.c already registered")
  }

  @Test
  fun `should save a note`() {
    val state = UserState()
    val prc = testKit.spawn(UserBehavior.create(state, aid()))
    val probe = testKit.createTestProbe<StatusReply<Response>>()
    val reader = UserReader(state)

    val newid = UUID.randomUUID()
    prc.tell(RegisterUser(newid, "a@b.c","a", "welkom123", LocalDate.of(1967, 4, 1), probe.ref))
    val result = probe.receiveMessage()
    assertThat(result.isSuccess).isTrue
    val userid = result.value.id
    assertThat(reader.find(userid)).isNotNull

    prc.tell(CreateNote(user = userid, title = "Test Title", body = "Test Body", replyTo = probe.ref))
    val note = probe.receiveMessage()
    assertThat(note.isSuccess).isTrue
    assertThat(note.value.id).isNotNull

    val read = reader.find(userid).block()
    assertThat(read).isNotNull
    assertThat(read?.id).isEqualTo(userid)
    assertThat(read?.notes).hasSize(1)
  }
}
