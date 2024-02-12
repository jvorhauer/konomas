package blog.route

import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern
import blog.model.Command
import blog.model.CreateNoteRequest
import blog.model.DeleteNote
import blog.model.Konfig
import blog.model.Note
import blog.read.Reader
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.future.await
import java.time.Duration

fun Route.notesRoute(processor: ActorRef<Command>, reader: Reader, scheduler: Scheduler, kfg: Konfig) =
  authenticate(kfg.realm) {
    route("/api/notes") {
      post {
        val cnr = call.receive<CreateNoteRequest>()
        val userId = user(call) ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        AskPattern.ask(processor, { rt -> cnr.toCommand(userId, rt) }, Duration.ofMinutes(1), scheduler).await().let {
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
        AskPattern.ask(processor, { rt -> DeleteNote(id, rt) }, Duration.ofMinutes(1), scheduler).await().let {
          when {
            it.isSuccess -> call.respond(HttpStatusCode.OK, mapOf("note" to id))
            else -> call.respond(HttpStatusCode.InternalServerError, it.error.localizedMessage)
          }
        }
      }
    }
  }
