package blog

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern.ask
import akka.actor.typed.javadsl.Behaviors
import blog.model.Command
import blog.model.CreateNoteRequest
import blog.model.CreateTaskRequest
import blog.model.DeleteNote
import blog.model.LoginRequest
import blog.model.RegisterUserRequest
import blog.model.UpdateNoteRequest
import blog.model.UpdateTaskRequest
import blog.model.User
import blog.model.UserResponse
import blog.model.toTSID
import blog.read.Reader
import blog.write.Processor
import io.hypersistence.tsid.TSID
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.support.beans
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.fromCompletionStage
import reactor.core.publisher.Mono.justOrEmpty
import reactor.kotlin.core.publisher.toFlux
import java.net.URI
import java.time.Duration

@SpringBootApplication
class Application

fun main() {
  ActorSystem.create(Server.create(), "system")
}

object Server {
  fun create(): Behavior<Void> = Behaviors.setup { ctx ->
    val reader = Reader()
    val processor = ctx.spawn(Processor.create("2", reader), "konomas")
    runApplication<Application> {
      addInitializers(beans(processor, ctx.system, reader))
    }
    Behaviors.same()
  }
}

fun beans(processor: ActorRef<Command>, system: ActorSystem<Void>, reader: Reader) = beans {
  bean { ApiHandler(system.scheduler(), processor, reader) }
  bean { routes(ref()) }
}

fun ServerRequest.monoPathVar(s: String): Mono<String> = justOrEmpty(s).mapNotNull { runCatching { this.pathVariable(it) }.getOrNull() }
fun ServerRequest.pathAsTSID(s: String): Mono<TSID> = this.monoPathVar(s).map { it.toLong() }.map { it.toTSID() }

fun routes(handler: ApiHandler): RouterFunction<ServerResponse> =
  router {
    contentType(MediaType.APPLICATION_JSON)
    accept(MediaType.APPLICATION_JSON)
    "/api".nest {
      POST("/user", handler::register)
      POST("/login", handler::login)

      POST("/note", handler::createNote)
      GET("/note/{id}", handler::findNote)
      PUT("/note", handler::updateNote)
      DELETE("/note/{id}", handler::deleteNote)

      GET("/users") { _ -> handler.findAll() }
      GET("/user/id/{id}", handler::findById)
      GET("/user/email/{email}", handler::findByEmail)

      POST("/tasks", handler::createTask)
      PUT("/tasks", handler::updateTask)
    }
  }


class ApiHandler(private val scheduler: Scheduler, private val processor: ActorRef<Command>, private val reader: Reader) {

  private val timeout = Duration.ofSeconds(1)
  private val badRequest = ServerResponse.badRequest().build()
  private val ok = ServerResponse.ok()
  private val notFound = ServerResponse.notFound().build()
  private val unauthorized = ServerResponse.status(401).build()
  private val forbidden = ServerResponse.status(403).bodyValue("User unknown or wrong password")
  private fun created(url: String) = ServerResponse.created(uri(url))
  private fun badreq(bodyval: String?): Mono<ServerResponse> = ServerResponse.badRequest().bodyValue(bodyval ?: "Huh?")

  private fun uri(s: String) = URI.create(s)

  fun register(req: ServerRequest): Mono<ServerResponse> = req.bodyToMono(RegisterUserRequest::class.java)
      .flatMap { fromCompletionStage(ask(processor, { rt -> it.toCommand(rt) }, timeout, scheduler)) }
      .flatMap { if (it.isSuccess) created("/api/user/id/${it.value.id}").bodyValue(it.value) else badreq(it.error.message) }
      .switchIfEmpty(badRequest)

  fun login(req: ServerRequest): Mono<ServerResponse> = req.bodyToMono(LoginRequest::class.java)
      .flatMap { fromCompletionStage(ask(processor, { rt -> it.toCommand(rt) }, timeout, scheduler))}
      .flatMap { if (it.isSuccess) ok.bodyValue(it.value) else forbidden }
      .switchIfEmpty(forbidden)

  fun findById(req: ServerRequest): Mono<ServerResponse> = req.pathAsTSID("id")
      .flatMap { justOrEmpty(reader.findUserById(it)) }
      .map { it.toResponse(reader) }
      .flatMap { ServerResponse.ok().bodyValue(it) }
      .switchIfEmpty(notFound)

  fun findByEmail(req: ServerRequest): Mono<ServerResponse> = justOrEmpty(req.pathVariable("email"))
      .flatMap { reader.findUserByEmail(it) }
      .map { it.toResponse(reader) }
      .flatMap { ok.bodyValue(it) }
      .switchIfEmpty(notFound)

  fun findAll(): Mono<ServerResponse> = ok.contentType(MediaType.APPLICATION_JSON).body(reader.allUsers().toFlux(), UserResponse::class.java)

  fun createNote(req: ServerRequest): Mono<ServerResponse> = loggedin(req)
      .flatMap { principal -> req.bodyToMono(CreateNoteRequest::class.java).map { it.copy(user = principal.id.toLong()) } }
      .flatMap { fromCompletionStage(ask(processor, { rt -> it.toCommand(rt) }, timeout, scheduler)) }
      .flatMap {
        if (it.isSuccess) {
          ServerResponse.created(uri("/api/note/${it.value.id}")).bodyValue(it.value)
        } else {
          badRequest
        }
      }.switchIfEmpty(unauthorized)

  fun updateNote(req: ServerRequest): Mono<ServerResponse> = loggedin(req)
      .flatMap { principal -> req.bodyToMono(UpdateNoteRequest::class.java).map { it.copy(user = principal.id) } }
      .flatMap { fromCompletionStage(ask(processor, { rt -> it.toCommand(rt) }, timeout, scheduler)) }
      .flatMap { if (it.isSuccess) ok.bodyValue(it.value) else badreq(it.error.message) }
      .switchIfEmpty(unauthorized)

  fun deleteNote(req: ServerRequest): Mono<ServerResponse> =
    loggedin(req).zipWith(req.pathAsTSID("id").mapNotNull { reader.findNote(it) })
      .filter { tup -> tup?.t2?.user != null && tup.t2.user == tup.t1.id }
      .flatMap { tup -> fromCompletionStage(ask(processor, { DeleteNote(tup.t1.id, tup.t2.id, it) }, timeout, scheduler)) }
      .flatMap { if(it.isSuccess) ok.build() else badRequest }
      .switchIfEmpty(unauthorized)

  fun findNote(req: ServerRequest): Mono<ServerResponse> =
    req.pathAsTSID("id")
      .flatMap { justOrEmpty(reader.findNote(it)) }
      .flatMap { ok.bodyValue(it) }
      .switchIfEmpty(notFound)

  fun createTask(req: ServerRequest): Mono<ServerResponse> = loggedin(req)
    .flatMap { principal -> req.bodyToMono(CreateTaskRequest::class.java).map { it.copy(user = principal.id) } }
    .flatMap { ServerResponse.unprocessableEntity().build() }

  fun updateTask(req: ServerRequest): Mono<ServerResponse> = loggedin(req)
    .flatMap { principal -> req.bodyToMono(UpdateTaskRequest::class.java).map { it.copy(user = principal.id) } }
    .flatMap { ServerResponse.unprocessableEntity().build() }

  private fun loggedin(req: ServerRequest): Mono<User> =
    justOrEmpty(req.headers().firstHeader("Authorization")).mapNotNull { reader.loggedin(it) }
}
