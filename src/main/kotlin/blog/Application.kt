package blog

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern.ask
import akka.actor.typed.javadsl.Behaviors
import blog.model.CreateNoteRequest
import blog.model.DeleteNote
import blog.model.Follow
import blog.model.LoginRequest
import blog.model.Note
import blog.model.RegisterUserRequest
import blog.model.UpdateNoteRequest
import blog.model.User
import blog.model.UserResponse
import blog.model.UserState
import blog.write.UserBehavior
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.support.beans
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import org.valiktor.springframework.config.ValiktorConfiguration
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.fromCompletionStage
import reactor.core.publisher.Mono.justOrEmpty
import java.net.URI
import java.time.Duration
import java.util.UUID

@SpringBootApplication
class Application

fun main() {
  ActorSystem.create(Server.create(UserState()), "system")
}

object Server {
  fun create(state: UserState): Behavior<Void> =
    Behaviors.setup { ctx ->
      val processor = ctx.spawn(UserBehavior.create(state, "1"), "user-behavior")
      runApplication<Application> {
        addInitializers(beans(processor, ctx.system, state))
      }
      Behaviors.same()
    }
}

fun beans(processor: ActorRef<Command>, system: ActorSystem<Void>, state: UserState) = beans {
  bean<ValiktorConfiguration>()
  bean { ValidationExceptionHandler(ref()) }
  bean { UserReader(state) }
  bean { ApiHandler(ref(), system.scheduler(), processor) }
  bean { routes(ref()) }
}

fun ServerRequest.monoPathVar(s: String): Mono<String> =
  justOrEmpty(s).mapNotNull { runCatching { this.pathVariable(it) }.getOrNull() }
fun ServerRequest.pathAsUUID(s: String): Mono<UUID> = this.monoPathVar(s).map { UUID.fromString(it) }

fun routes(handler: ApiHandler): RouterFunction<ServerResponse> =
  router {
    "/api".nest {
      POST("/user", handler::register)
      POST("/login", handler::login)
      POST("/follow", handler::follow)

      POST("/note", handler::createNote)
      GET("/note/{id}", handler::findNote)
      PUT("/note", handler::updateNote)
      DELETE("/note/{id}", handler::deleteNote)

      GET("/users") { _ -> handler.findAll() }
      GET("/user/id/{id}", handler::findById)
      GET("/user/email/{email}", handler::findByEmail)
    }
  }


class ApiHandler(private val reader: UserReader, private val scheduler: Scheduler, private val processor: ActorRef<Command>) {

  private val timeout = Duration.ofSeconds(1)
  private val badRequest = ServerResponse.badRequest().build()
  private val ok = ServerResponse.ok()
  private val notFound = ServerResponse.notFound().build()
  private val unauthorized = ServerResponse.status(401).build()
  private val forbidden = ServerResponse.status(403).bodyValue("User unknown or wrong password")
  private fun uri(s: String) = URI.create(s)

  fun register(req: ServerRequest): Mono<ServerResponse> =
    req.bodyToMono(RegisterUserRequest::class.java)
      .flatMap { fromCompletionStage(ask(processor, { rt -> it.toCommand(rt) }, timeout, scheduler)) }
      .flatMap {
        if (it.isSuccess) {
          ServerResponse.created(uri("/api/user/id/${it.value.id}")).bodyValue(it.value)
        } else {
          badRequest
        }
      }
      .switchIfEmpty(badRequest)

  fun login(req: ServerRequest): Mono<ServerResponse> =
    req.bodyToMono(LoginRequest::class.java)
      .flatMap { login -> reader.login(login.username, login.password) }
      .flatMap { ok.bodyValue(it) }
      .switchIfEmpty(forbidden)

  fun follow(req: ServerRequest): Mono<ServerResponse> =
    loggedin(req).
      flatMap { principal -> req.pathAsUUID("id")
        .flatMap { id -> reader.find(id) }
        .flatMap { fromCompletionStage(ask(processor, { rt -> Follow(it, principal, rt) }, timeout, scheduler)) }
        .flatMap { if (it.isSuccess) ok.build() else badRequest }
        .switchIfEmpty(notFound)
      }.switchIfEmpty(unauthorized)


  fun findById(req: ServerRequest): Mono<ServerResponse> =
    req.pathAsUUID("id")
      .flatMap { reader.find(it) }
      .flatMap { ServerResponse.ok().bodyValue(it) }
      .switchIfEmpty(notFound)

  fun findByEmail(req: ServerRequest): Mono<ServerResponse> =
    justOrEmpty(req.pathVariable("email"))
      .flatMap { reader.find(it) }
      .flatMap { ok.bodyValue(it) }
      .switchIfEmpty(notFound)

  fun findAll(): Mono<ServerResponse> = ok.bodyValue(reader.findAll())

  fun createNote(req: ServerRequest): Mono<ServerResponse> =
    loggedin(req)
      .flatMap { principal -> req.bodyToMono(CreateNoteRequest::class.java).map { it.copy(user = principal.id) } }
      .flatMap { fromCompletionStage(ask(processor, { rt -> it.toCommand(rt) }, timeout, scheduler)) }
      .flatMap {
        if (it.isSuccess) {
          ServerResponse.created(uri("/api/note/${it.value.id}")).bodyValue(it.value)
        } else {
          badRequest
        }
      }.switchIfEmpty(unauthorized)

  fun updateNote(req: ServerRequest): Mono<ServerResponse> =
    loggedin(req)
      .flatMap { principal -> req.bodyToMono(UpdateNoteRequest::class.java).map { it.copy(user = principal.id) } }
      .flatMap { fromCompletionStage(ask(processor, { rt -> it.toCommand(rt) }, timeout, scheduler)) }
      .flatMap { if (it.isSuccess) ok.bodyValue(it.value) else badRequest }
      .switchIfEmpty(unauthorized)

  fun deleteNote(req: ServerRequest): Mono<ServerResponse> =
    loggedin(req).zipWith(req.pathAsUUID("id").mapNotNull { reader.findNote(it) })
      .filter { tup -> tup?.t2?.user != null && tup.t2.user == tup.t1.id }
      .flatMap { tup -> fromCompletionStage(ask(processor, { DeleteNote(tup.t1.id, tup.t2.id, it) }, timeout, scheduler)) }
      .flatMap { if(it.isSuccess) ok.build() else badRequest }
      .switchIfEmpty(unauthorized)

  fun findNote(req: ServerRequest): Mono<ServerResponse> =
    req.pathAsUUID("id")
      .flatMap { justOrEmpty(reader.findNote(it)) }
      .flatMap { ok.bodyValue(it) }
      .switchIfEmpty(notFound)

  private fun loggedin(req: ServerRequest): Mono<User> =
    justOrEmpty(req.headers().firstHeader("X-Auth")).mapNotNull { reader.loggedin(it) }
}

class UserReader(private val state: UserState) {
  fun find(a: Any): Mono<User> = justOrEmpty(state.find(a))
  fun login(u: String, p: String): Mono<String> = justOrEmpty(state.login(u, p))
  fun loggedin(s: String): User? = state.loggedin(s)
  fun findAll(): List<UserResponse> = state.findAll().map { it.toResponse() }
  fun findNote(id: UUID): Note? = state.findNote(id)
}
