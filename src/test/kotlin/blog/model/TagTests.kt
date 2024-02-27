package blog.model

import java.util.UUID
import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.pattern.StatusReply
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test


class TagTests {

  private val testKit = TestKitJunitResource(
    """akka.persistence.journal.plugin = "akka.persistence.journal.inmem" 
       akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"  
       akka.persistence.snapshot-store.local.dir = "build/snapshot-${UUID.randomUUID()}"  
    """
  )
  private val probe = testKit.createTestProbe<StatusReply<TagResponse>>().ref

  @Test
  fun `request to command to event to entity to response`() {
    val ctr = CreateTagRequest("test label")
    val ct = ctr.toCommand(probe)
    assertThat(ct).isNotNull
    assertThat(ct.id).isNotNull
    assertThat(ct.label).isEqualTo("test label").isEqualTo(ctr.label)

    val tc = ct.toEvent
    assertThat(tc.id).isEqualTo(ct.id)
    assertThat(tc.label).isEqualTo(ct.label).isEqualTo(ctr.label)

    val tag = tc.toEntity
    assertThat(tag.id).isEqualTo(tc.id).isEqualTo(ct.id)
    assertThat(tag.label).isEqualTo(tc.label).isEqualTo(ct.label).isEqualTo(ctr.label)

    val tr = tag.toResponse()
    assertThat(tr.id).isEqualTo(tc.id).isEqualTo(ct.id).isEqualTo(tag.id)
    assertThat(tr.label).isEqualTo(tc.label).isEqualTo(ct.label).isEqualTo(ctr.label).isEqualTo(tag.label)
  }

  @AfterAll
  fun cleanup() {
    testKit.system().terminate()
  }
}
