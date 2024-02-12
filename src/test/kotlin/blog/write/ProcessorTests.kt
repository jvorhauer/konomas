package blog.write

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.pattern.StatusReply
import blog.model.CreateNote
import blog.model.CreateUser
import blog.model.NoteResponse
import blog.model.UpdateNote
import blog.model.UserResponse
import blog.read.Reader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class ProcessorTests {
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
    val reader = Reader()
    val prc = testKit.spawn(Processor.create(aid(), reader))
    val probe = testKit.createTestProbe<StatusReply<UserResponse>>()

    prc.tell(CreateUser("a@b.c","a", "welkom123", probe.ref))
    val result = probe.receiveMessage()
    assertThat(result.isSuccess).isTrue
    assertThat(result.value.javaClass.simpleName).isEqualTo("UserResponse")
    val ur = reader.findUserByEmail("a@b.c")
    assertThat(ur).isNotNull
    assertThat(ur?.id).isEqualTo(result.value.id)
    assertThat(ur?.name).isEqualTo("a")

    assertThat(reader.allUsers()).hasSize(1)
  }

  @Test
  fun `should not register a user twice`() {
    val reader = Reader()
    val prc = testKit.spawn(Processor.create(aid(), reader))
    val probe = testKit.createTestProbe<StatusReply<UserResponse>>()

    prc.tell(CreateUser("a@b.c","a", "welkom123", probe.ref))
    val result = probe.receiveMessage()
    assertThat(result.isSuccess).isTrue
    assertThat(result.value.id).isNotNull

    assertThat(reader.allUsers()).hasSize(1)

    prc.tell(CreateUser("a@b.c","a", "welkom123", probe.ref))
    val again = probe.receiveMessage()
    assertThat(again.isError).isTrue
    assertThat(again.error.message).isEqualTo("a@b.c already registered")

    assertThat(reader.allUsers()).hasSize(1)
    assertThat(reader.findUserByEmail("a@b.c")).isNotNull
  }

  @Test
  fun `should save a note`() {
    val reader = Reader()
    val prc = testKit.spawn(Processor.create(aid(), reader))
    val userProbe = testKit.createTestProbe<StatusReply<UserResponse>>()
    val noteProbe = testKit.createTestProbe<StatusReply<NoteResponse>>()

    prc.tell(CreateUser("a@b.c","a", "welkom123", userProbe.ref))
    val result = userProbe.receiveMessage()
    assertThat(result.isSuccess).isTrue
    assertThat(reader.findUser(result.value.id)).isNotNull

    prc.tell(CreateNote(user = result.value.id, title = "Test Title", body = "Test Body", replyTo = noteProbe.ref))
    val note = noteProbe.receiveMessage()
    assertThat(note.isSuccess).isTrue
    assertThat(note.value.id).isNotNull

    val read = reader.findUser(result.value.id)
    assertThat(read).isNotNull

    prc.tell(UpdateNote(result.value.id, note.value.id, "New Title", body = "New Body", replyTo = noteProbe.ref))
    val updated = noteProbe.receiveMessage()
    assertThat(updated).isNotNull
    assertThat(updated.value.title).isEqualTo("New Title")
  }
}
