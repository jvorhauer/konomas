package blog.write

import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.RecoveryCompleted
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.Effect
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import akka.persistence.typed.javadsl.RetentionCriteria
import akka.persistence.typed.javadsl.SignalHandler
import blog.Command
import blog.Event
import blog.model.CreateNote
import blog.model.DeleteNote
import blog.model.NoteEvent
import blog.model.RegisterUser
import blog.model.UpdateNote
import blog.model.NoteUpdated
import blog.model.UserEvent
import blog.model.UserState
import java.time.Duration

private val onFail = SupervisorStrategy.restartWithBackoff(Duration.ofMillis(200), Duration.ofSeconds(5), 0.1)

class UserBehavior(private val state: UserState, pid: PersistenceId) : EventSourcedBehavior<Command, Event, UserState>(pid, onFail) {
  override fun emptyState(): UserState = state
  override fun retentionCriteria(): RetentionCriteria = RetentionCriteria.snapshotEvery(100, 5)

  override fun commandHandler(): CommandHandler<Command, Event, UserState> = newCommandHandlerBuilder()
    .forAnyState()
    .onCommand(RegisterUser::class.java, this::onRegisterUser)
    .onCommand(CreateNote::class.java, this::onCreateNote)
    .onCommand(UpdateNote::class.java, this::onUpdateNote)
    .onCommand(DeleteNote::class.java, this::onDeleteNote)
    .build()

  private fun onRegisterUser(state: UserState, cmd: RegisterUser): Effect<Event, UserState> =
    if (state.exists(cmd.email)) Effect().none().thenRun {
      cmd.replyTo.tell(StatusReply.error("${cmd.email} already registered"))
    }
    else {
      val event = cmd.toEvent()
      Effect().persist(event).thenRun { _: UserState ->
        cmd.replyTo.tell(StatusReply.success(event.toEntity().toResponse()))
      }
    }

  private fun onCreateNote(state: UserState, cmd: CreateNote): Effect<Event, UserState> =
    if (state.notExists(cmd.user)) Effect().none().thenRun {
      cmd.replyTo.tell(StatusReply.error("User ${cmd.user} not found"))
    }
    else {
      val event = cmd.toEvent()
      Effect().persist(event).thenRun { _: UserState ->
        cmd.replyTo.tell(StatusReply.success(event.toEntity().toResponse()))
      }
    }

  private fun onUpdateNote(state: UserState, cmd: UpdateNote): Effect<Event, UserState> =
    state.find(cmd.user)?.notes?.find { it.id == cmd.id }.let {
      if (it == null) Effect().none().thenRun {
        cmd.rt.tell(StatusReply.error("Note with id ${cmd.id} not found for user with id ${cmd.user}"))
      } else {
        val event = cmd.toEvent(it)
        Effect().persist(event).thenRun { _: UserState ->
          cmd.rt.tell(StatusReply.success(event.toEntity().toResponse()))
        }
      }
    }

  private fun onDeleteNote(state: UserState, cmd: DeleteNote): Effect<Event, UserState> =
    state.find(cmd.user)?.notes?.find { it.id == cmd.id }.let {
      if (it == null) Effect().none().thenRun {
        cmd.rt.tell(StatusReply.error("Note with id ${cmd.id} not found for user with id ${cmd.user}"))
      } else {
        val event = cmd.toEvent()
        Effect().persist(event).thenRun { _: UserState ->
          cmd.rt.tell(StatusReply.success(event.toResponse()))
        }
      }
    }

  override fun eventHandler(): EventHandler<UserState, Event> = newEventHandlerBuilder()
    .forAnyState()
    .onEvent(UserEvent::class.java) { event -> state.save(event.toEntity()) }
    .onEvent(NoteEvent::class.java) { event -> state.addNote(event.toEntity()) }
    .onEvent(NoteUpdated::class.java) { event -> state.updateNote(event.toEntity()) }
    .build()

  override fun signalHandler(): SignalHandler<UserState> =
    newSignalHandlerBuilder()
      .onSignal(RecoveryCompleted::class.java, this::recovered)
      .build()

  private fun recovered(state: UserState, rc: RecoveryCompleted): RecoveryCompleted = rc.apply { state.recovered = true }

  companion object {
    fun create(state: UserState, pid: String): Behavior<Command> = UserBehavior(state, PersistenceId.of("user-behavior", pid))
  }
}
