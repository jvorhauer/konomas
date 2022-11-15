package blog

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern.ask
import akka.actor.typed.javadsl.BehaviorBuilder
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.typed.Cluster
import akka.cluster.typed.Join
import akka.cluster.typed.Leave
import blog.model.CreateNoteRequest
import blog.model.LoginRequest
import blog.model.Note
import blog.model.RegisterUserRequest
import blog.model.UserResponse
import blog.model.UserState
import blog.write.UserBehavior
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.beans
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.fromFuture
import java.net.URI
import java.time.Duration
import java.util.UUID
import javax.annotation.PreDestroy

@SpringBootApplication
class Application

fun main() {
  ActorSystem.create(Server.create(UserState()), "system")
}

sealed interface Message
data class Started(val cfg: ConfigurableApplicationContext) : Message
object Shutdown : Message

object Server {
  fun create(state: UserState): Behavior<Message> =
    Behaviors.setup { ctx ->
      val processor = ctx.spawn(UserBehavior.create(state, "1"), "user-behavior")
      val cfg = runApplication<Application> {
        addInitializers(beans(processor, ctx.system, state))
      }
      running(Started(cfg))
    }

  private fun running(msg: Started): Behavior<Message> =
    BehaviorBuilder.create<Message>()
      .onMessage(Shutdown::class.java) {
        Behaviors.stopped()
      }
      .onSignal(PostStop::class.java) {
        msg.cfg.close()
        Behaviors.same()
      }
      .build()
}

fun beans(processor: ActorRef<Command>, system: ActorSystem<Void>, state: UserState) = beans {
  bean { Cluster.get(system) }
  bean { joiner(ref()) }
  bean<Leaver>()
  bean<ValidationExceptionHandler>()
  bean { UserReader(state) }
  bean { ApiHandler(ref(), system.scheduler(), processor) }
  bean { routes(ref()) }
}

fun joiner(cluster: Cluster) {
  cluster.manager().tell(Join.create(cluster.selfMember().address()))
}

class Leaver(private val cluster: Cluster) {
  @PreDestroy
  fun leave() {
    cluster.manager().tell(Leave.create(cluster.selfMember().address()))
  }
}

fun ServerRequest.pathAsUUID(s: String): Mono<UUID> = this.monoPathVar(s).map { UUID.fromString(it) }

fun routes(handler: ApiHandler): RouterFunction<ServerResponse> =
  router {
    "/api".nest {
      POST("/user", handler::register)
      POST("/login", handler::login)

      POST("/note/{id}", handler::note)
      GET("/note/{id}", handler::findNote)

      GET("/users") { _ -> handler.findAll() }
      GET("/user/id/{id}", handler::findById)
      GET("/user/email/{email}", handler::findByEmail)
    }
  }


class ApiHandler(private val reader: UserReader, private val scheduler: Scheduler, private val processor: ActorRef<Command>) {

  private val timeout = Duration.ofSeconds(1)

  fun register(req: ServerRequest): Mono<ServerResponse> =
    req.bodyToMono(RegisterUserRequest::class.java)
      .flatMap { fromFuture(ask(processor, { rt -> it.toCommand(rt) }, timeout, scheduler).toCompletableFuture()) }
      .flatMap {
        when {
          it.isError -> ServerResponse.badRequest().build()
          it.isSuccess -> ServerResponse.created(URI.create("/api/user/id/${it.value.id}")).bodyValue(it.value)
          else -> ServerResponse.unprocessableEntity().build()
        }
      }
      .switchIfEmpty(ServerResponse.badRequest().build())

  fun login(req: ServerRequest): Mono<ServerResponse> =
    req.bodyToMono(LoginRequest::class.java)
      .flatMap { login -> reader.login(login.username, login.password) }
      .flatMap {
        ServerResponse.ok().bodyValue(it)
      }.switchIfEmpty(ServerResponse.status(403).bodyValue("User unknown or wrong password"))

  fun findById(req: ServerRequest): Mono<ServerResponse> =
    req.pathAsUUID("id")
      .flatMap { id -> reader.find(id) }
      .flatMap { ur -> ServerResponse.ok().bodyValue(ur) }
      .switchIfEmpty(ServerResponse.notFound().build())

  fun findByEmail(req: ServerRequest): Mono<ServerResponse> =
    Mono.just(req.pathVariable("email"))
      .flatMap { email -> reader.find(email) }
      .flatMap { ur -> ServerResponse.ok().bodyValue(ur) }
      .switchIfEmpty(ServerResponse.notFound().build())

  fun findAll(): Mono<ServerResponse> = ServerResponse.ok().bodyValue(reader.findAll())

  fun note(req: ServerRequest): Mono<ServerResponse> =
    loggedin(req).flatMap { req.bodyToMono(CreateNoteRequest::class.java) }
      .flatMap { fromFuture(ask(processor, { rt -> it.toCommand(rt) }, timeout, scheduler).toCompletableFuture()) }
      .flatMap {
        when {
          it.isError -> ServerResponse.badRequest().build()
          it.isSuccess -> ServerResponse.created(URI.create("/api/note/${it.value.id}")).bodyValue(it.value)
          else -> ServerResponse.unprocessableEntity().build()
        }
      }.switchIfEmpty(ServerResponse.status(401).build())

  fun findNote(req: ServerRequest): Mono<ServerResponse> =
    req.pathAsUUID("id")
      .flatMap { id -> Mono.justOrEmpty(reader.findNote(id)) }
      .flatMap { ServerResponse.ok().bodyValue(it) }
      .switchIfEmpty(ServerResponse.notFound().build())

  private fun loggedin(req: ServerRequest): Mono<Boolean> =
    Mono.justOrEmpty(req.headers().firstHeader("X-Auth")).map { reader.loggedin(it) }
}

class UserReader(private val state: UserState) {
  fun find(a: Any): Mono<UserResponse> = Mono.justOrEmpty(state.find(a)?.toResponse())
  fun login(u: String, p: String): Mono<String> = Mono.justOrEmpty(state.login(u, p))
  fun loggedin(s: String): Boolean = state.loggedin(s)
  fun findAll(): List<UserResponse> = state.findAll().map { it.toResponse() }
  fun findNote(id: UUID): Note? = state.findNote(id)
}
