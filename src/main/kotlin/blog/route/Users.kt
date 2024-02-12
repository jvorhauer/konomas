package blog.route

import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern
import blog.model.Command
import blog.model.Konfig
import blog.model.RegisterUserRequest
import blog.model.User
import blog.read.Reader
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.future.await
import java.time.Duration

fun Route.usersRoute(processor: ActorRef<Command>, reader: Reader, scheduler: Scheduler, kfg: Konfig): Route =
  route("/api/users") {
    get {
      val start = call.request.queryParameters["start"]?.toInt() ?: 0
      val rows = call.request.queryParameters["rows"]?.toInt() ?: 10
      call.respond(reader.allUsers(rows, start).map { it.toResponse(reader) })
    }
    get("{id?}") {
      val id = call.parameters["id"] ?: return@get call.respondText("no user id specified", status = HttpStatusCode.BadRequest)
      val user: User = reader.find(id.toLong()) ?: return@get call.respondText("user not found for $id", status = HttpStatusCode.NotFound)
      call.respond(user.toResponse(reader))
    }
    authenticate(kfg.realm) {
      get("/tasks") {
        val userId = user(call) ?: return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        call.respond(reader.findTasksForUser(userId).map { it.toResponse() })
      }
    }
    post {
      val cnu = call.receive<RegisterUserRequest>()
      AskPattern.ask(processor, { rt -> cnu.toCommand(rt) }, Duration.ofMinutes(1), scheduler).await().let {
        if (it.isSuccess) {
          call.response.header("Location", "/api/users/${it.value.id}")
          call.respond(HttpStatusCode.Created, it.value)
        } else {
          call.respond(HttpStatusCode.BadRequest, it.error.localizedMessage)
        }
      }
    }
  }
