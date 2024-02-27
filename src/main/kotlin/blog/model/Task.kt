package blog.model

import java.time.LocalDateTime
import java.time.ZonedDateTime
import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern.ask
import akka.pattern.StatusReply
import io.hypersistence.tsid.TSID
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.future.await
import blog.config.Konfig
import blog.read.Reader

enum class TaskStatus {
  TODO, DOING, REVIEW, DONE
}

data class Task(
  override val id: String,
  val user: String,
  val title: String,
  val slug: String,
  val body: String,
  val due: LocalDateTime,
  val status: TaskStatus = TaskStatus.TODO,
  val private: Boolean = true,
  val created: ZonedDateTime = TSID.from(id).instant.atZone(CET),
  val updated: ZonedDateTime = znow
) : Entity {
  fun update(tu: TaskUpdated): Task = this.copy(
    title = tu.title ?: this.title,
    slug = tu.title?.slug ?: this.slug,
    body = tu.body ?: this.body,
    due = tu.due ?: this.due,
    status = tu.status ?: this.status,
    updated = znow
  )

  val toResponse get() = TaskResponse(id, created.fmt, updated.fmt, user, title, body, due.fmt, status.name)
  override fun equals(other: Any?): Boolean = equals(this, other)
  override fun hashCode(): Int = id.hashCode()
}

data class CreateTaskRequest(val title: String, val body: String, val due: LocalDateTime): Request {
  fun toCommand(user: String, replyTo: ActorRef<StatusReply<TaskResponse>>) = CreateTask(user, title.encode, body.encode, due, replyTo)
}

data class UpdateTaskRequest(val id: String, val title: String?, val body: String?, val due: LocalDateTime?, val status: TaskStatus?): Request {
  fun toCommand(user: String, replyTo: ActorRef<StatusReply<TaskResponse>>) = UpdateTask(user, id, title.mencode, body.mencode, due, status, replyTo)
}


data class CreateTask(
  val user: String,
  val title: String,
  val body: String,
  val due: LocalDateTime,
  val replyTo: ActorRef<StatusReply<TaskResponse>>,
  val id: String = nextId
) : Command {
  val toEvent get() = TaskCreated(id, user, title, body, due)
}

data class UpdateTask(
  val user: String,
  val id: String,
  val title: String?,
  val body: String?,
  val due: LocalDateTime?,
  val status: TaskStatus?,
  val replyTo: ActorRef<StatusReply<TaskResponse>>
) : Command {
  val toEvent get() = TaskUpdated(user, id, title, body, due, status)
}

data class DeleteTask(val id: String, val replyTo: ActorRef<StatusReply<Done>>): Command {
  val toEvent get() = TaskDeleted(id)
}


data class TaskCreated(
  val id: String,
  val user: String,
  val title: String,
  val body: String,
  val due: LocalDateTime
) : Event {
  val toEntity get() = Task(id, user, title, title.slug, body, due)
  val toResponse get() = toEntity.toResponse
}

data class TaskUpdated(
  val user: String,
  val id: String,
  val title: String?,
  val body: String?,
  val due: LocalDateTime?,
  val status: TaskStatus?,
) : Event

data class TaskDeleted(val id: String): Event


data class TaskResponse(
  val id: String,
  val created: String,
  val updated: String,
  val user: String,
  val title: String,
  val body: String,
  val due: String,
  val status: String
)

fun Route.tasksRoute(processor: ActorRef<Command>, reader: Reader, scheduler: Scheduler, kfg: Konfig) =
  authenticate(kfg.jwt.realm) {
    route("/api/tasks") {
      post {
        val ctr = call.receive<CreateTaskRequest>()
        val userId = userIdFromJWT(call) ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        ask(processor, { rt -> ctr.toCommand(userId, rt) }, timeout, scheduler).await().let {
          when {
              it.isSuccess -> {
                  call.response.header("Location", "/api/tasks/${it.value.id}")
                  call.respond(HttpStatusCode.Created, it.value)
              }
              else -> call.respond(HttpStatusCode.BadRequest, it.error.localizedMessage)
          }
        }
      }
      get {
        val rows = call.request.queryParameters["rows"]?.toInt() ?: 10
        val start = call.request.queryParameters["start"]?.toInt() ?: 0
        call.respond(reader.allTasks(rows, start).map { it.toResponse })
      }
      get("{id?}") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.NotFound, "no task id specified")
        (reader.find<Task>(id) ?: return@get call.respond(HttpStatusCode.NotFound, "task not found for $id")).let {
          call.respond(it.toResponse)
        }
      }
      put {
        val utr = call.receive<UpdateTaskRequest>()
        val userId = userIdFromJWT(call) ?: return@put call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        ask(processor, { rt -> utr.toCommand(userId, rt) }, timeout, scheduler).await().let {
          when {
            it.isSuccess -> call.respond(HttpStatusCode.OK, it.value)
            else -> call.respond(HttpStatusCode.BadRequest, it.error.localizedMessage)
          }
        }
      }
      delete("{id?}") {
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.NotFound, "no task id specified")
        reader.findTask(id) ?: return@delete call.respond(HttpStatusCode.NotFound, "task not found for $id")
        ask(processor, { rt -> DeleteTask(id, rt) }, timeout, scheduler).await().let {
          when {
              it.isSuccess -> call.respond(HttpStatusCode.OK, mapOf("task" to id))
              else -> call.respond(HttpStatusCode.InternalServerError, it.error.localizedMessage)
          }
        }
      }
    }
  }
