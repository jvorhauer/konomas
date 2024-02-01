package blog.model

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.pattern.StatusReply
import io.hypersistence.tsid.TSID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class NoteTests {

  private val testKit = TestKitJunitResource(
    """akka.persistence.journal.plugin = "akka.persistence.journal.inmem" 
       akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"  
       akka.persistence.snapshot-store.local.dir = "build/snapshot-${UUID.randomUUID()}"  
    """
  )
  private val probeNoteRes = testKit.createTestProbe<StatusReply<NoteResponse>>().ref
  private val probeNoteDel = testKit.createTestProbe<StatusReply<NoteDeletedResponse>>().ref

  private val userId: TSID = nextId()

  @Test
  fun `create request to command to event to entity`() {
    val cnr = CreateNoteRequest(userId.toLong(), "title", "body")
    assertThat(cnr.validate()).isEmpty()

    val cn = cnr.toCommand(probeNoteRes)
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

    assertThat(cnr.copy(title = "").validate()).hasSize(1)
    assertThat(cnr.copy(body = "").validate()).hasSize(1)
    assertThat(cnr.copy(title = "", body = "").validate()).hasSize(2)
  }

  @Test
  fun `update request to command to event`() {
    val run = UpdateNoteRequest(userId, nextId(), "title", "body")
    assertThat(run.validate()).isEmpty()

    val un: UpdateNote = run.toCommand(probeNoteRes)
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
  }

  @Test
  fun `delete command to event`() {
    val dn = DeleteNote(userId, nextId(), probeNoteDel)
    val de: NoteDeleted = dn.toEvent()
    assertThat(de.id).isNotNull
    assertThat(de.user).isEqualTo(userId)
  }
}
