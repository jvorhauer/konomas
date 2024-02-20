package blog.model

import akka.Done
import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.pattern.StatusReply
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import org.owasp.encoder.Encode

class NoteTests {

  private val testKit = TestKitJunitResource(
    """akka.persistence.journal.plugin = "akka.persistence.journal.inmem" 
       akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"  
       akka.persistence.snapshot-store.local.dir = "build/snapshot-${UUID.randomUUID()}"  
    """
  )
  private val probeNoteRes = testKit.createTestProbe<StatusReply<NoteResponse>>().ref
  private val probeNoteDel = testKit.createTestProbe<StatusReply<Done>>().ref

  private val userId = nextId()

  @Test
  fun `create request to command to event to entity`() {
    val cnr = CreateNoteRequest("title", "body")

    val cn = cnr.toCommand(userId, probeNoteRes)
    assertThat(cn.id).isNotNull
    assertThat(cn.user).isEqualTo(userId)
    assertThat(cn.title).isEqualTo("title")
    assertThat(cn.body).isEqualTo("body")

    val nc = cn.toEvent()
    assertThat(nc.id).isNotNull
    assertThat(nc.user).isEqualTo(userId)
    assertThat(nc.title).isEqualTo("title")
    assertThat(nc.body).isEqualTo("body")

    val note = nc.toEntity()
    assertThat(note.id).isNotNull()
    assertThat(note.user).isEqualTo(userId)
    assertThat(note.title).isEqualTo("title")
    assertThat(note.body).isEqualTo("body")
  }

  @Test
  fun `update request to command to event`() {
    val run = UpdateNoteRequest(nextId(), "title", "body")

    val un: UpdateNote = run.toCommand(userId, probeNoteRes)
    assertThat(un.id).isNotNull
    assertThat(un.user).isEqualTo(userId)
    assertThat(un.title).isEqualTo("title")
    assertThat(un.body).isEqualTo("body")

    val nu: NoteUpdated = un.toEvent()
    assertThat(nu.id).isNotNull
    assertThat(nu.user).isEqualTo(userId)
    assertThat(nu.title).isEqualTo("title")
    assertThat(nu.body).isEqualTo("body")

    val note = Note(nextId(), userId, "Title", "Body")
    val updated = note.update(nu)
    assertThat(updated.id).isEqualTo(note.id)
    assertThat(updated.user).isEqualTo(userId)
    assertThat(updated.title).isEqualTo(run.title)
    assertThat(updated.body).isEqualTo(run.body)

    var str = Encode.forHtml("")
    assertThat(str).isEmpty()
    str = Encode.forHtml(null)
    assertThat(str).isNotNull();
  }

  @Test
  fun `delete command to event`() {
    val dn = DeleteNote(nextId(), probeNoteDel)
    val de: NoteDeleted = dn.toEvent()
    assertThat(de.id).isNotNull
  }
}
