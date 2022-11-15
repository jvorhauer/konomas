package blog

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AskPattern.ask
import akka.actor.typed.javadsl.BehaviorBuilder
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.typed.Cluster
import akka.cluster.typed.Join
import akka.cluster.typed.Leave
import blog.model.CreateNoteRequest
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

fun beans(processor: ActorRef<Command>, sys: ActorSystem<Void>, state: UserState) = beans {
  bean { Cluster.get(sys) }
  bean { joiner(ref()) }
  bean<Leaver>()
  bean<ValidationExceptionHandler>()
  bean { UserReader(state) }
  bean<UserHandler>()
  bean { routes(processor, sys, ref()) }
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

private val timeout = Duration.ofSeconds(1)

fun ServerRequest.pathAsUUID(s: String): Mono<UUID> = this.monoPathVar(s).map { UUID.fromString(it) }

fun routes(processor: ActorRef<Command>, sys: ActorSystem<Void>, handler: UserHandler): RouterFunction<ServerResponse> =
  router {
    "/api".nest {
      POST("/user") { req -> register(req, processor, sys) }
      DELETE("/users") { _ -> wipe(processor, sys) }

      POST("/note/{id}") { req -> note(req, processor, sys) }

      GET("/users") { _ -> handler.findAll() }
      GET("/user/id/{id}", handler::findById)
      GET("/user/email/{email}", handler::findByEmail)
    }
  }

fun register(req: ServerRequest, prc: ActorRef<Command>, sys: ActorSystem<Void>): Mono<ServerResponse> =
  req.bodyToMono(RegisterUserRequest::class.java)
    .flatMap { fromFuture(ask(prc, { rt -> it.toCommand(rt) }, timeout, sys.scheduler()).toCompletableFuture()) }
    .flatMap {
      when {
        it.isError -> ServerResponse.badRequest().build()
        it.isSuccess -> ServerResponse.created(URI.create("/api/read/user/id/${it.value.id}")).bodyValue(it.value)
        else -> ServerResponse.unprocessableEntity().build()
      }
    }
    .switchIfEmpty(ServerResponse.badRequest().build())

fun wipe(prc: ActorRef<Command>, sys: ActorSystem<Void>): Mono<ServerResponse> =
  fromFuture(ask(prc, { rt -> DeleteAll(rt) }, timeout, sys.scheduler()).toCompletableFuture())
    .flatMap { ServerResponse.ok().build() }

fun note(req: ServerRequest, prc: ActorRef<Command>, sys: ActorSystem<Void>): Mono<ServerResponse> =
  req.bodyToMono(CreateNoteRequest::class.java)
    .flatMap { fromFuture(ask(prc, { rt -> it.toCommand(rt) }, timeout, sys.scheduler()).toCompletableFuture()) }
    .flatMap {
      when {
        it.isError -> ServerResponse.badRequest().build()
        it.isSuccess -> ServerResponse.ok().bodyValue(it.value)
        else -> ServerResponse.unprocessableEntity().build()
      }
    }.switchIfEmpty(ServerResponse.badRequest().build())


class UserHandler(private val reader: UserReader) {
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
}

class UserReader(private val state: UserState) {
  fun find(a: Any): Mono<UserResponse> = Mono.justOrEmpty(state.find(a)?.toResponse())
  fun findAll(): List<UserResponse> = state.findAll().map { it.toResponse() }
}
