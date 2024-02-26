package blog.model

import java.time.ZonedDateTime
import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern
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

data class CreateNoteRequest(val title: String, val body: String) {
  fun toCommand(user: String, replyTo: ActorRef<StatusReply<NoteResponse>>) = CreateNote(user, title.encode, body.encode, replyTo)
}

data class UpdateNoteRequest(val id: String, val title: String?, val body: String?) {
  fun toCommand(user: String, rt: ActorRef<StatusReply<NoteResponse>>) = UpdateNote(user, id, title.mencode, body.mencode, rt)
}

data class CreateNote(
  val user: String,
  val title: String,
  val body: String,
  val replyTo: ActorRef<StatusReply<NoteResponse>>,
  val id: String = nextId()
) : Command {
  fun toEvent() = NoteCreated(id, user, title, body)
}

data class UpdateNote(val user: String, val id: String, val title: String?, val body: String?, val replyTo: ActorRef<StatusReply<NoteResponse>>): Command {
  fun toEvent() = NoteUpdated(id, user, title, body)
}

data class DeleteNote(val id: String, val rt: ActorRef<StatusReply<Done>>): Command {
  fun toEvent() = NoteDeleted(id)
}

data class NoteCreated(val id: String, val user: String, val title: String, val body: String) : Event {
  fun toEntity() = Note(id, user, title, title.slug, body)
  fun toResponse() = this.toEntity().toResponse()
}

data class NoteUpdated(val id: String, val user: String, val title: String?, val body: String?): Event

data class NoteDeleted(val id: String): Event

data class Note(
  override val id: String,
  val user: String,
  val title: String,
  val slug: String,
  val body: String,
  val created: ZonedDateTime = TSID.from(id).instant.atZone(CET),
  val updated: ZonedDateTime = znow()
): Entity {
  constructor(id: String, user: String, title: String, body: String): this(id, user, title, title.slug, body)
  fun update(nu: NoteUpdated): Note = this.copy(
    title = nu.title ?: this.title, slug = nu.title?.slug ?: this.slug, body = nu.body ?: this.body, updated = znow()
  )
  fun toResponse() = NoteResponse(id, user, DTF.format(created), DTF.format(updated), title, slug, body)

  override fun equals(other: Any?): Boolean = equals(this, other)
  override fun hashCode(): Int = id.hashCode()
}

data class NoteResponse(
  val id: String,
  val user: String,
  val created: String,
  val updated: String,
  val title: String,
  val slug: String,
  val body: String
)

fun Route.notesRoute(processor: ActorRef<Command>, reader: Reader, scheduler: Scheduler, kfg: Konfig) =
  authenticate(kfg.jwt.realm) {
    route("/api/notes") {
      post {
        val cnr = call.receive<CreateNoteRequest>()
        val userId = userIdFromJWT(call) ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        AskPattern.ask(processor, { rt -> cnr.toCommand(userId, rt) }, timeout, scheduler).await().let {
          if (it.isSuccess) {
            call.response.header("Location", "/api/notes/${it.value.id}")
            call.respond(HttpStatusCode.Created, it.value)
          } else {
            call.respond(HttpStatusCode.BadRequest, it.error.localizedMessage)
          }
        }
      }
      get {
        val rows = call.request.queryParameters["rows"]?.toInt() ?: 10
        val start = call.request.queryParameters["start"]?.toInt() ?: 0
        call.respond(reader.allNotes(rows, start).map { it.toResponse() })
      }
      get("{id?}") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.NotFound, "note id not specified")
        val note = reader.findNote(id) ?: return@get call.respond(HttpStatusCode.NotFound, "note not found for $id")
        call.respond(note.toResponse())
      }
      put {
        val unr = call.receive<UpdateNoteRequest>()
        val userId = userIdFromJWT(call) ?: return@put call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        AskPattern.ask(processor, { rt -> unr.toCommand(userId, rt) }, timeout, scheduler).await().let {
          when {
            it.isSuccess -> call.respond(HttpStatusCode.OK, it.value)
            else -> call.respond(HttpStatusCode.BadRequest, it.error.localizedMessage)
          }
        }
      }
      delete("{id?}") {
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.NotFound, "note id not specified")
        reader.findNote(id) ?: return@delete call.respond(HttpStatusCode.NotFound, "note not found for $id")
        AskPattern.ask(processor, { rt -> DeleteNote(id, rt) }, timeout, scheduler).await().let {
          when {
            it.isSuccess -> call.respond(HttpStatusCode.OK, mapOf("note" to id))
            else -> call.respond(HttpStatusCode.InternalServerError, it.error.localizedMessage)
          }
        }
      }
    }
  }
