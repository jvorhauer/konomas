package blog.write

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.pattern.StatusReply
import blog.model.CreateNote
import blog.model.NoteResponse
import blog.model.RegisterUser
import blog.model.UpdateNote
import blog.model.UserResponse
import blog.model.nextId
import blog.read.Reader
import io.hypersistence.tsid.TSID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
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

    val newid = nextId()
    prc.tell(RegisterUser(newid, "a@b.c","a", "welkom123", LocalDate.of(1967, 4, 1), probe.ref))
    val result = probe.receiveMessage()
    assertThat(result.isSuccess).isTrue
    assertThat(result.value.id).isEqualTo(newid.toLong())
    assertThat(result.value.javaClass.simpleName).isEqualTo("UserResponse")
    val ur = reader.findUserByEmail("a@b.c").block()
    assertThat(ur).isNotNull
    assertThat(ur?.id?.toLong()).isEqualTo(result.value.id)
    assertThat(ur?.name).isEqualTo("a")
  }

  @Test
  fun `should not register a user twice`() {
    val reader = Reader()
    val prc = testKit.spawn(Processor.create(aid(), reader))
    val probe = testKit.createTestProbe<StatusReply<UserResponse>>()

    prc.tell(RegisterUser(nextId(), "a@b.c","a", "welkom123", LocalDate.of(1967, 4, 1), probe.ref))
    val result = probe.receiveMessage()
    assertThat(result.isSuccess).isTrue
    assertThat(result.value.id).isNotNull

    prc.tell(RegisterUser(nextId(), "a@b.c","a", "welkom123", LocalDate.of(1967, 4, 1), probe.ref))
    val again = probe.receiveMessage()
    assertThat(again.isError).isTrue
    assertThat(again.error.message).isEqualTo("a@b.c already registered")
  }

  @Test
  fun `should save a note`() {
    val reader = Reader()
    val prc = testKit.spawn(Processor.create(aid(), reader))
    val userProbe = testKit.createTestProbe<StatusReply<UserResponse>>()
    val noteProbe = testKit.createTestProbe<StatusReply<NoteResponse>>()

    prc.tell(RegisterUser(nextId(), "a@b.c","a", "welkom123", LocalDate.of(1967, 4, 1), userProbe.ref))
    val result = userProbe.receiveMessage()
    assertThat(result.isSuccess).isTrue
    val userId = TSID.from(result.value.id)
    assertThat(reader.findUserById(userId)).isNotNull

    prc.tell(CreateNote(user = userId, title = "Test Title", body = "Test Body", replyTo = noteProbe.ref))
    val note = noteProbe.receiveMessage()
    assertThat(note.isSuccess).isTrue
    assertThat(note.value.id).isNotNull

    val read = reader.findUserById(userId)
    assertThat(read).isNotNull
    assertThat(read?.id).isEqualTo(userId)

    prc.tell(UpdateNote(userId, TSID.from(note.value.id), "New Title", body = "New Body", replyTo = noteProbe.ref))
    val updated = noteProbe.receiveMessage()
    assertThat(updated).isNotNull
    assertThat(updated.value.title).isEqualTo("New Title")
  }
}
