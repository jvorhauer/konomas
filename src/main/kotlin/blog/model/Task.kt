package blog.model

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import java.time.LocalDateTime

enum class TaskStatus {
  TODO, DOING, REVIEW, DONE
}

data class Task(
  override val id: Long,
  val user: Long,
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
  fun toResponse() = TaskResponse(id, user, title, body, DTF.format(due), status.name)
}

data class CreateTaskRequest(val title: String, val body: String, val due: LocalDateTime): Request {
  fun toCommand(user: Long, replyTo: ActorRef<StatusReply<TaskResponse>>) = CreateTask(user, title, body, due, replyTo)
}

data class UpdateTaskRequest(val id: Long, val title: String?, val body: String?, val due: LocalDateTime?, val status: TaskStatus?): Request {
  fun toCommand(user: Long, replyTo: ActorRef<StatusReply<TaskResponse>>) = UpdateTask(user, id, title, body, due, status, replyTo)
}


data class CreateTask(
  val user: Long,
  val title: String,
  val body: String,
  val due: LocalDateTime,
  val replyTo: ActorRef<StatusReply<TaskResponse>>,
  val id: Long = nextId()
) : Command {
  fun toEvent() = TaskCreated(id, user, title, body, due)
}

data class UpdateTask(
  val user: Long,
  val id: Long,
  val title: String?,
  val body: String?,
  val due: LocalDateTime?,
  val status: TaskStatus?,
  val replyTo: ActorRef<StatusReply<TaskResponse>>
) : Command {
  fun toEvent() = TaskUpdated(user, id, title, body, due, status)
}

data class DeleteTask(val id: Long, val replyTo: ActorRef<StatusReply<Done>>): Command {
  fun toEvent() = TaskDeleted(id)
}


data class TaskCreated(
  val id: Long,
  val user: Long,
  val title: String,
  val body: String,
  val due: LocalDateTime
) : Event {
  fun toEntity() = Task(id, user, title, slugify(title), body, due)
  fun toResponse() = toEntity().toResponse()
}

data class TaskUpdated(
  val user: Long,
  val id: Long,
  val title: String?,
  val body: String?,
  val due: LocalDateTime?,
  val status: TaskStatus?,
) : Event

data class TaskDeleted(val id: Long): Event


data class TaskResponse(
  val id: Long,
  val user: Long,
  val title: String,
  val body: String,
  val due: String,
  val status: String
)
