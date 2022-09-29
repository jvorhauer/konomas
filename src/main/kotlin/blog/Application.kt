package blog

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.Patterns.ask
import evented.ExampleActor
import evented.ExampleEventListener
import evented.ExampleHandler
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.support.beans
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono
import java.time.Duration

@SpringBootApplication
class Application

fun main() {
    runApplication<Application> {
        addInitializers(beans)
    }
}

val beans = beans {
    bean { ActorSystem.create("as-novi") }
    bean<UserRepo>()
    bean<TokenRepo>()
    bean<PostRepo>()
    bean<UserHandler>()
    bean<PostHandler>()
    bean<NoteEventListener>()
    bean { TheActorHandler(TheActor.create(ref(), "ze-big-boss")) }
    bean { ExampleHandler(ExampleActor.create(ref(), "persistor")) }
    bean { ExampleEventListener.create(ref(), "listener") }
    bean { routes(ref(), ref(), ref(), ref()) }
}

// Actually two routers: write and read side.

fun routes(
    userHandler: UserHandler,
    postHandler: PostHandler,
    theActorHandler: TheActorHandler,
    exampler: ExampleHandler
) =
    router {
        "/api".nest {
            POST("/register", userHandler::register)
            POST("/login", userHandler::login)
            GET("/users", userHandler::all)
            GET("/logout", userHandler::logout)
            GET("/user/{id}", userHandler::one)

            POST("/blogs", postHandler::save)
            GET("/blog/{id}", postHandler::one)
            GET("/blogs/{id}", postHandler::paged)

            GET("/act/{text}") {
                ok().body(BodyInserters.fromPublisher(theActorHandler.print(it), String::class.java))
            }
            GET("/act/direct/{text}") {
                ok().body(BodyInserters.fromPublisher(theActorHandler.direct(it), String::class.java))
            }

            GET("/es/print") {
                ok().body(BodyInserters.fromPublisher(exampler.print(it), String::class.java))
            }
            GET("/es/{cmd}") {
                ok().body(BodyInserters.fromPublisher(exampler.handle(it), String::class.java))
            }
        }
    }

val UNAUTH = ServerResponse.status(HttpStatus.UNAUTHORIZED).build()

data class TheContext(
    val msg: String
)

class TheActor : AbstractActor() {
    override fun createReceive(): Receive =
        receiveBuilder()
              .match(TheContext::class.java) {
                  println("${self.path()} received ${it.msg} on ${Thread.currentThread()}")
                  sender.tell("Thanks for ${it.msg}", self)
              }
              .match(String::class.java) {
                  println("${self.path()} received $it on ${Thread.currentThread()}")
                  sender.tell("Thanks for $it", self)
              }
              .build()

    companion object {
        private fun props(): Props = Props.create(TheActor::class.java)
        fun create(system: ActorSystem, name: String): ActorRef = system.actorOf(props(), name)
    }
}

class TheActorHandler(private val actor: ActorRef) {
    fun print(req: ServerRequest): Mono<String> =
        Mono.fromFuture(
            ask(actor, TheContext(req.pathVariable("text")), Duration.ofMillis(10))
                  .toCompletableFuture()
                  .thenApply { it as String }       // yep, untyped actors :-(
        )
    fun direct(req: ServerRequest): Mono<String> =
        Mono.fromFuture(
            ask(actor, req.pathVariable("text"), Duration.ofMillis(10))
                  .toCompletableFuture()
                  .thenApply { it as String }
        )
}
