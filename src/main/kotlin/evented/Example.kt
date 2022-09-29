package evented

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.Patterns.ask
import akka.persistence.AbstractPersistentActor
import akka.persistence.SnapshotOffer
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono
import java.time.Duration

const val snapshotInterval = 1000L

data class Cmd(val data: String)

data class Evt(val data: String)

data class ExampleState(var events: MutableList<String> = mutableListOf()) {
    fun update(event: String): MutableList<String> {
        events.add(event)
        return events
    }
    fun copy(): List<String> = events.toList()
    fun size(): Int = events.size
    override fun toString(): String = events.toString()
}

class ExampleActor(private var state: ExampleState = ExampleState()) : AbstractPersistentActor() {
    override fun createReceive(): Receive =
        receiveBuilder()
              .match(Cmd::class.java) { cmd ->
                  val data = cmd.data
                  val event = Evt("$data-${state.size()}")
                  persist(event) { evt ->
                      state.update(evt.data)
                      context.system.eventStream.publish(evt)
                      if (lastSequenceNr() % snapshotInterval == 0L && lastSequenceNr() != 0L) {
                          saveSnapshot(state.copy())
                      }
                  }
                  sender.tell(event.data, self)
              }
              .matchEquals("print") {
                  println(state)
                  sender.tell(state.toString(), self)
              }
              .build()

    override fun persistenceId(): String = "sample-id-1"

    override fun createReceiveRecover(): Receive =
        receiveBuilder()
              .match(Evt::class.java) {
                  state.update(it.data)
                  context.system.eventStream.publish(it)
              }
              .match(SnapshotOffer::class.java) { ss -> state = ss.snapshot() as ExampleState }
              .build()

    companion object {
        private fun props() = Props.create(ExampleActor::class.java)
        fun create(system: ActorSystem, name: String): ActorRef = system.actorOf(props(), name)
    }
}

class ExampleHandler(private val actor: ActorRef) {
    fun handle(req: ServerRequest): Mono<String> = monask(Cmd(req.pathVariable("cmd")))
    fun print(req: ServerRequest): Mono<String> = monask(req.queryParam("in").orElse("print"))
    private fun monask(msg: Any): Mono<String> = Mono.fromFuture(
        ask(actor, msg, Duration.ofMillis(100))
              .toCompletableFuture()
              .thenApply { it as String }
    )
    override fun toString(): String = "class ${this.javaClass.canonicalName} has actorRef: ${actor.path()}"
}

class ExampleEventListener: AbstractActor() {
    override fun createReceive(): Receive = receiveBuilder()
          .match(Evt::class.java) {
              println("exampleEventListener: received $it")
          }.build()

    companion object {
        private fun props() = Props.create(ExampleEventListener::class.java)
        fun create(system: ActorSystem, name: String): ActorRef {
            val actor = system.actorOf(props(), name)
            system.eventStream.subscribe(actor, Evt::class.java)
            return actor
        }
    }
}
