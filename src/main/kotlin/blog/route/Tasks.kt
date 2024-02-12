package blog.route

import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern.ask
import blog.model.Command
import blog.model.CreateTaskRequest
import blog.model.DeleteTask
import blog.model.Konfig
import blog.model.UpdateTaskRequest
import blog.read.Reader
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.future.await

fun Route.tasksRoute(processor: ActorRef<Command>, reader: Reader, scheduler: Scheduler, kfg: Konfig) =
  authenticate(kfg.realm) {
    route("/api/tasks") {
      post {
        val ctr = call.receive<CreateTaskRequest>()
        val userId = user(call) ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
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
        call.respond(reader.allTasks(rows, start).map { it.toResponse() })
      }
      get("{id?}") {
        val id = call.parameters["id"]?.toLong() ?: return@get call.respond(HttpStatusCode.NotFound, "no task id specified")
        (reader.findTask(id) ?: return@get call.respond(HttpStatusCode.NotFound, "task not found for $id")).let {
          call.respond(it.toResponse())
        }
      }
      put {
        val utr = call.receive<UpdateTaskRequest>()
        val userId = user(call) ?: return@put call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        ask(processor, { rt -> utr.toCommand(userId, rt) }, timeout, scheduler).await().let {
          when {
            it.isSuccess -> call.respond(HttpStatusCode.OK, it.value)
            else -> call.respond(HttpStatusCode.BadRequest, it.error.localizedMessage)
          }
        }
      }
      delete("{id?}") {
        val id = call.parameters["id"]?.toLong() ?: return@delete call.respond(HttpStatusCode.NotFound, "no task id specified")
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
