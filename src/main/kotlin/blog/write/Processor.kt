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
import blog.model.Command
import blog.model.CreateNote
import blog.model.CreateTask
import blog.model.DeleteNote
import blog.model.Event
import blog.model.LoggedIn
import blog.model.Login
import blog.model.NoteEvent
import blog.model.NoteUpdated
import blog.model.RegisterUser
import blog.model.State
import blog.model.TaskCreated
import blog.model.UpdateNote
import blog.model.UserRegistered
import blog.read.Reader
import java.time.Duration

private val onFail = SupervisorStrategy.restartWithBackoff(Duration.ofMillis(200), Duration.ofSeconds(5), 0.1)

class Processor(pid: PersistenceId, private val reader: Reader) : EventSourcedBehavior<Command, Event, State>(pid, onFail) {
  override fun emptyState(): State = State()
  override fun retentionCriteria(): RetentionCriteria = RetentionCriteria.snapshotEvery(100, 5)

  override fun commandHandler(): CommandHandler<Command, Event, State> = newCommandHandlerBuilder()
    .forAnyState()
    .onCommand(RegisterUser::class.java, this::onRegisterUser)
    .onCommand(CreateNote::class.java, this::onCreateNote)
    .onCommand(UpdateNote::class.java, this::onUpdateNote)
    .onCommand(DeleteNote::class.java, this::onDeleteNote)
    .onCommand(CreateTask::class.java, this::onCreateTask)
    .onCommand(Login::class.java, this::onLoginRequest)
    .build()

  private fun onRegisterUser(state: State, cmd: RegisterUser): Effect<Event, State> =
    if (state.findUserByEmail(cmd.email) != null) Effect().none().thenRun {
      cmd.replyTo.tell(StatusReply.error("${cmd.email} already registered"))
    }
    else {
      val event = cmd.toEvent()
      Effect().persist(event).thenRun { st: State ->
        reader.processEvent(event)
        cmd.replyTo.tell(StatusReply.success(event.toEntity().toResponse(reader)))
      }
    }

  private fun onLoginRequest(state: State, cmd: Login): Effect<Event, State> {
    val us = state.login(cmd.username, cmd.password)
    return if (us != null) {
      val event = cmd.toEvent(us.user)
      Effect().persist(event).thenRun { _: State ->
        reader.processEvent(event)
        cmd.replyTo.tell(StatusReply.success(event.toSession().toToken()))
      }
    } else {
      Effect().none().thenRun { cmd.replyTo.tell(StatusReply.error("username or password not correct")) }
    }
  }

  private fun onCreateNote(state: State, cmd: CreateNote): Effect<Event, State> =
    if (state.findUserById(cmd.user) == null) Effect().none().thenRun {
      cmd.replyTo.tell(StatusReply.error("User ${cmd.user} not found"))
    } else {
      val event = cmd.toEvent()
      Effect().persist(event).thenRun { _: State ->
        reader.processEvent(event)
        cmd.replyTo.tell(StatusReply.success(event.toEntity().toResponse()))
      }
    }

  private fun onUpdateNote(state: State, cmd: UpdateNote): Effect<Event, State> =
    state.findNoteById(cmd.id).let {
      if (it == null) Effect().none().thenRun {
        cmd.replyTo.tell(StatusReply.error("Note with id ${cmd.id} not found for user with id ${cmd.user}"))
      } else {
        val event = cmd.toEvent()
        Effect().persist(event).thenRun { st: State ->
          reader.processEvent(event)
          val note = st.findNoteById(event.id)
          cmd.replyTo.tell(StatusReply.success(note!!.toResponse()))
        }
      }
    }

  private fun onDeleteNote(state: State, cmd: DeleteNote): Effect<Event, State> =
    state.findNoteById(cmd.id).let {
      if (it == null) Effect().none().thenRun {
        cmd.rt.tell(StatusReply.error("Note with id ${cmd.id} not found for user with id ${cmd.user}"))
      } else {
        val event = cmd.toEvent()
        Effect().persist(event).thenRun { _: State ->
          reader.processEvent(event)
          cmd.rt.tell(StatusReply.success(event.toResponse()))
        }
      }
    }

  private fun onCreateTask(state: State, cmd: CreateTask): Effect<Event, State> =
    if (state.findUserById(cmd.user) == null) Effect().none().thenRun {
      cmd.replyTo.tell(StatusReply.error("User ${cmd.user} not found"))
    } else {
      val event = cmd.toEvent()
      Effect().persist(event).thenRun { _: State ->
        reader.processEvent(event)
        cmd.replyTo.tell(StatusReply.success(event.toEntity().toResponse()))
      }
    }


  override fun eventHandler(): EventHandler<State, Event> = newEventHandlerBuilder()
    .forAnyState()
    .onEvent(UserRegistered::class.java, ) { state, event -> state.save(event.toEntity()) }
    .onEvent(NoteEvent::class.java) { state, event -> state.save(event.toEntity()) }
    .onEvent(NoteUpdated::class.java) { state, event -> state.findNoteById(event.id)?.let { state.save(it.update(event)) } ?: state }
    .onEvent(TaskCreated::class.java) { state, event -> state.save(event.toEntity()) }
    .onEvent(LoggedIn::class.java) { state, event -> state.save(event.toSession()) }
    .build()


  override fun signalHandler(): SignalHandler<State> =
    newSignalHandlerBuilder()
      .onSignal(RecoveryCompleted::class.java, this::recovered)
      .build()

  private fun recovered(state: State, rc: RecoveryCompleted): RecoveryCompleted = rc.apply {
    println("recovery completed: ${state.allUsers().size}")
    state.hasRecovered()
  }

  companion object {
    fun create(pid: String, reader: Reader): Behavior<Command> = Processor(PersistenceId.of("konomas", pid), reader)
  }
}
