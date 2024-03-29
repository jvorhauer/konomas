package blog.model

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.pattern.StatusReply
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID
import io.hypersistence.tsid.TSID

class TaskTests {

  private val testKit = TestKitJunitResource(
    """akka.persistence.journal.plugin = "akka.persistence.journal.inmem" 
       akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"  
       akka.persistence.snapshot-store.local.dir = "build/snapshot-${UUID.randomUUID()}"  
    """
  )
  private val probeTaskRes = testKit.createTestProbe<StatusReply<TaskResponse>>().ref
  private val userId = nextId

  @Test
  fun `create task request, command, event, entity and response`() {
    val ctr = CreateTaskRequest("title", "body", now.plusDays(2))
    val ct = ctr.toCommand(userId, probeTaskRes)
    assertThat(ct.id).isNotNull
    assertThat(ct.title).isEqualTo("title").isEqualTo(ctr.title)
    assertThat(ct.body).isEqualTo("body").isEqualTo(ctr.body)
    assertThat(ct.due).isAfter(znow).isEqualTo(ctr.due.atZone(CET))
    assertThat(ct.user).isEqualTo(userId)

    val tc = ct.toEvent
    assertThat(tc.id).isEqualTo(ct.id)
    assertThat(tc.title).isEqualTo("title").isEqualTo(ct.title).isEqualTo(ctr.title)
    assertThat(tc.body).isEqualTo("body").isEqualTo(ct.body).isEqualTo(ctr.body)
    assertThat(tc.due).isEqualTo(ct.due).isEqualTo(ctr.due.atZone(CET)).isAfter(znow)
    assertThat(tc.user).isEqualTo(userId).isEqualTo(ct.user)

    val task = tc.toEntity
    assertThat(task.id).isEqualTo(tc.id)
    assertThat(task.title).isEqualTo("title").isEqualTo(tc.title).isEqualTo(ct.title).isEqualTo(ctr.title)
    assertThat(task.body).isEqualTo("body").isEqualTo(tc.body).isEqualTo(ct.body).isEqualTo(ctr.body)
    assertThat(task.due).isEqualTo(tc.due).isEqualTo(ct.due).isEqualTo(ctr.due.atZone(CET)).isAfter(znow)
    assertThat(task.user).isEqualTo(userId).isEqualTo(tc.user).isEqualTo(ct.user)

    val res = task.toResponse
    assertThat(res.id).isEqualTo(TSID.from(task.id).toString())
    assertThat(res.title).isEqualTo(task.title)
    assertThat(res.body).isEqualTo(task.body)
    assertThat(res.due).isEqualTo(DTF.format(task.due))
    assertThat(res.status).isEqualTo(task.status.name)
  }

  @Test
  fun `update task request, command and event`() {
    val taskId = nextId
    val utr = UpdateTaskRequest(TSID.from(taskId).toString(), "new title", "new body", now.plusDays(3), TaskStatus.DOING)
    val ut = utr.toCommand(userId, probeTaskRes)
    assertThat(ut.id).isEqualTo(taskId)
    assertThat(ut.user).isEqualTo(userId)
    assertThat(ut.title).isEqualTo("new title")
    assertThat(ut.body).isEqualTo("new body")
    assertThat(ut.due).isEqualTo(utr.due?.atZone(CET)).isAfter(znow)
    assertThat(ut.status).isEqualTo(TaskStatus.DOING)

    val task = Task(taskId, userId, "title", "title".slug, "body", znow.plusDays(1), TaskStatus.REVIEW)
    var updated = task.update(ut.toEvent)
    assertThat(updated.title).isEqualTo("new title")
    assertThat(updated.body).isEqualTo("new body")

    updated = task.update(TaskUpdated(userId, taskId, null, "better body", null, null))
    assertThat(updated.body).isEqualTo("better body")
    assertThat(updated.title).isEqualTo(task.title)
    assertThat(updated.due).isEqualTo(task.due)
    assertThat(updated.status).isEqualTo(task.status)

    updated = task.update(TaskUpdated(userId, taskId, "better title", null, null, null))
    assertThat(updated.body).isEqualTo(task.body)
    assertThat(updated.title).isEqualTo("better title")
    assertThat(updated.due).isEqualTo(task.due)
    assertThat(updated.status).isEqualTo(task.status)

    val due = znow.plusHours(1)
    updated = task.update(TaskUpdated(userId, taskId, null, null, due, null))
    assertThat(updated.body).isEqualTo(task.body)
    assertThat(updated.title).isEqualTo(task.title)
    assertThat(updated.due).isEqualTo(due)
    assertThat(updated.status).isEqualTo(task.status)

    updated = task.update(TaskUpdated(userId, taskId, null, null, null, TaskStatus.DONE))
    assertThat(updated.body).isEqualTo(task.body)
    assertThat(updated.title).isEqualTo(task.title)
    assertThat(updated.due).isEqualTo(task.due)
    assertThat(updated.status).isEqualTo(TaskStatus.DONE)

    updated = task.update(TaskUpdated(userId, taskId, null, null, null, null))
    assertThat(updated.body).isEqualTo(task.body)
    assertThat(updated.title).isEqualTo(task.title)
    assertThat(updated.due).isEqualTo(task.due)
    assertThat(updated.status).isEqualTo(task.status)
  }

  @Test
  fun zonedDateTimeParsing() {
    var zonedDateTime: ZonedDateTime = ZonedDateTime.parse("2022-11-03T17:35:00Z")
    assertThat(zonedDateTime).isNotNull
    assertThat(zonedDateTime.year).isEqualTo(2022)
    assertThat(zonedDateTime.hour).isEqualTo(17)

    val localDateTime = LocalDateTime.parse("2023-10-02T16:24:11")
    assertThat(localDateTime).isNotNull
    zonedDateTime = localDateTime.atZone(CET)
    assertThat(zonedDateTime).isNotNull
    assertThat(zonedDateTime.dayOfMonth).isEqualTo(2)
    assertThat(zonedDateTime.hour).isEqualTo(16)
  }
}
