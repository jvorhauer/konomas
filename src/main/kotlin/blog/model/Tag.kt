package blog.model

import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern.ask
import akka.pattern.StatusReply
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.future.await
import blog.config.Konfig
import blog.read.Reader

data class Tag(override val id: String, val label: String) : Entity {
  fun toResponse() = TagResponse(id, label)
  override fun equals(other: Any?): Boolean = equals(this, other)
  override fun hashCode(): Int = id.hashCode()
}

data class CreateTagRequest(val label: String) {
  fun toCommand(replyTo: ActorRef<StatusReply<TagResponse>>) = CreateTag(nextId, label, replyTo)
}

data class TagResponse(override val id: String, val label: String) : Response

data class CreateTag(val id: String, val label: String, val replyTo: ActorRef<StatusReply<TagResponse>>) : Command {
  val toEvent get() = TagCreated(id, label)
}

data class TagCreated(val id: String, val label: String) : Event {
  val toEntity get() = Tag(id, label)
}

fun Route.tagRoute(processor: ActorRef<Command>, reader: Reader, scheduler: Scheduler, kfg: Konfig) {
  authenticate(kfg.jwt.realm) {
    route("/api/tags") {
      post {
        val ctr = call.receive<CreateTagRequest>()
        ask(processor, { rt -> ctr.toCommand(rt) }, timeout, scheduler).await().let {
          if (it.isSuccess) {
            call.response.header("Location", "/api/tags/${it.value.id}")
            call.respond(HttpStatusCode.Created, it.value)
          } else {
            call.respond(HttpStatusCode.BadRequest, it.error.localizedMessage)
          }
        }
      }
      get {
        call.respond(reader.allTags.map { it.toResponse() })
      }
      get("{id?}") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.NotFound, "no tag id specified")
        (reader.find<Tag>(id) ?: return@get call.respond(HttpStatusCode.NotFound, "tag not found for $id")).let {
          call.respond(it.toResponse())
        }
      }
    }
  }
}
