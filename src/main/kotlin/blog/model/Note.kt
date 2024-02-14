package blog.model

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import org.owasp.encoder.Encode
import java.time.LocalDateTime
import java.time.ZoneId
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.future.await
import blog.read.Reader

data class CreateNoteRequest(val title: String, val body: String) {
  fun toCommand(user: Long, replyTo: ActorRef<StatusReply<NoteResponse>>) = CreateNote(user, title, body, replyTo)
}

data class UpdateNoteRequest(val id: Long, val title: String?, val body: String?) {
  fun toCommand(user: Long, rt: ActorRef<StatusReply<NoteResponse>>) = UpdateNote(user, id, title, body, rt)
}

data class CreateNote(
  val user: Long,
  val title: String,
  val body: String,
  val replyTo: ActorRef<StatusReply<NoteResponse>>,
  val id: Long = nextId()
) : Command {
  fun toEvent() = NoteCreated(id, user, Encode.forHtml(title), Encode.forHtml(body))
}

data class UpdateNote(val user: Long, val id: Long, val title: String?, val body: String?, val replyTo: ActorRef<StatusReply<NoteResponse>>): Command {
  fun toEvent() = NoteUpdated(id, user, title, body)
}

data class DeleteNote(val id: Long, val rt: ActorRef<StatusReply<Done>>): Command {
  fun toEvent() = NoteDeleted(id)
}

data class NoteCreated(val id: Long, val user: Long, val title: String, val body: String) : Event {
  fun toEntity() = Note(id, user, title, slugify(title), body)
  fun toResponse() = this.toEntity().toResponse()
}

data class NoteUpdated(val id: Long, val user: Long, val title: String?, val body: String?): Event

data class NoteDeleted(val id: Long): Event

data class Note(
  override val id: Long,
  val user: Long,
  val title: String,
  val slug: String,
  val body: String
): Entity {
  constructor(id: Long, user: Long, title: String, body: String): this(id, user, title, slugify(title), body)
  fun update(nu: NoteUpdated): Note = this.copy(title = nu.title ?: this.title, body = nu.body ?: this.body)
  fun toResponse() = NoteResponse(id, user, DTF.format(LocalDateTime.ofInstant(id.toTSID().instant, ZoneId.of("CET"))), title, body)
}

data class NoteResponse(
  val id: Long,
  val user: Long,
  val created: String,
  val title: String,
  val body: String
)

fun Route.notesRoute(processor: ActorRef<Command>, reader: Reader, scheduler: Scheduler, kfg: Konfig) =
  authenticate(kfg.realm) {
    route("/api/notes") {
      post {
        val cnr = call.receive<CreateNoteRequest>()
        val userId = user(call) ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
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
        val id = call.parameters["id"]?.toLong() ?: return@get call.respond(HttpStatusCode.NotFound, "note id not specified")
        val note: Note = reader.find(id) ?: return@get call.respond(HttpStatusCode.NotFound, "note not found for $id")
        call.respond(note.toResponse())
      }
      delete("{id?}") {
        val id = call.parameters["id"]?.toLong() ?: return@delete call.respond(HttpStatusCode.NotFound, "note id not specified")
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
