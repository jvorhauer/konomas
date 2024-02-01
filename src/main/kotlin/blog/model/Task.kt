package blog.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.hypersistence.tsid.TSID
import java.time.LocalDateTime

enum class TaskStatus {
  TODO, DOING, REVIEW, DONE
}

data class Task(
  override val id: TSID,
  val user: TSID,
  val title: String,
  val slug: String,
  val body: String,
  val due: LocalDateTime,
  val status: TaskStatus = TaskStatus.TODO,
  val private: Boolean = true
) : Entity {
  fun update(tu: TaskUpdated): Task = this.copy(
    title = tu.title ?: this.title,
    slug = slugify(tu.title ?: this.title),
    body = tu.body ?: this.body,
    due = tu.due ?: this.due,
    status = tu.status ?: this.status
  )
  fun toResponse() = TaskResponse(id.toLong(), title, body, due, status.name)
}

data class CreateTaskRequest(
  val user: TSID,
  val title: String,
  val body: String,
  val due: LocalDateTime
) {
  fun validate() = Validator.validate(this)
  fun toCommand(user: TSID, replyTo: ActorRef<StatusReply<TaskResponse>>) = CreateTask(user, title, body, due, replyTo)
}

data class UpdateTaskRequest(
  val user: TSID,
  val id: TSID,
  val title: String?,
  val body: String?,
  val due: LocalDateTime?,
  val status: TaskStatus?
) {
  fun toCommand(replyTo: ActorRef<StatusReply<TaskResponse>>) = UpdateTask(user, id, title, body, due, status, replyTo)
}

data class CreateTask(
  val user: TSID,
  val title: String,
  val body: String,
  val due: LocalDateTime,
  val replyTo: ActorRef<StatusReply<TaskResponse>>,
  val id: TSID = nextId()
) : Command {
  fun toEvent() = TaskCreated(id, user, title, body, due)
}

data class UpdateTask(
  val user: TSID,
  val id: TSID,
  val title: String?,
  val body: String?,
  val due: LocalDateTime?,
  val status: TaskStatus?,
  val replyTo: ActorRef<StatusReply<TaskResponse>>
) : Command {
  fun toEvent() = TaskUpdated(user, id, title, body, due, status)
}

data class TaskCreated(
  val id: TSID,
  val user: TSID,
  val title: String,
  val body: String,
  val due: LocalDateTime
) : Event {
  fun toEntity() = Task(id, user, title, slugify(title), body, due)
}

data class TaskUpdated(
  val user: TSID,
  val id: TSID,
  val title: String?,
  val body: String?,
  val due: LocalDateTime?,
  val status: TaskStatus?,
) : Event

data class TaskResponse(
  override val id: Long,
  val title: String,
  val body: String,
  val due: LocalDateTime,
  val status: String
) : Response