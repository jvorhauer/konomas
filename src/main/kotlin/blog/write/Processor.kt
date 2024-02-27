package blog.write

import java.time.Duration

import akka.Done
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

import org.slf4j.LoggerFactory

import blog.model.Command
import blog.model.CreateNote
import blog.model.CreateTag
import blog.model.CreateTask
import blog.model.CreateUser
import blog.model.DeleteNote
import blog.model.DeleteTask
import blog.model.Event
import blog.model.NoteCreated
import blog.model.NoteDeleted
import blog.model.NoteUpdated
import blog.model.State
import blog.model.Tag
import blog.model.TagCreated
import blog.model.TaskCreated
import blog.model.TaskDeleted
import blog.model.TaskUpdated
import blog.model.UpdateNote
import blog.model.UpdateTask
import blog.model.UpdateUser
import blog.model.UserCreated
import blog.model.UserDeleted
import blog.model.UserUpdated
import blog.read.Reader

private val onFail = SupervisorStrategy.restartWithBackoff(Duration.ofMillis(200), Duration.ofSeconds(5), 0.1)

class Processor(pid: PersistenceId, private val reader: Reader) : EventSourcedBehavior<Command, Event, State>(pid, onFail) {
  private val logger = LoggerFactory.getLogger("Processor")
  override fun emptyState(): State = State()
  override fun retentionCriteria(): RetentionCriteria = RetentionCriteria.snapshotEvery(100, 5)

  override fun commandHandler(): CommandHandler<Command, Event, State> = newCommandHandlerBuilder()
    .forAnyState()
    .onCommand(CreateUser::class.java, this::onCreateUser)
    .onCommand(UpdateUser::class.java, this::onUpdateUser)
    .onCommand(CreateNote::class.java, this::onCreateNote)
    .onCommand(UpdateNote::class.java, this::onUpdateNote)
    .onCommand(DeleteNote::class.java, this::onDeleteNote)
    .onCommand(CreateTask::class.java, this::onCreateTask)
    .onCommand(UpdateTask::class.java, this::onUpdateTask)
    .onCommand(DeleteTask::class.java, this::onDeleteTask)
    .onCommand(CreateTag::class.java, this::onCreateTag)
    .build()

  private fun onCreateUser(state: State, cmd: CreateUser): Effect<Event, State> =
    if (state.findUserByEmail(cmd.email) != null) {
      Effect().none().thenReply(cmd.replyTo) { StatusReply.error("${cmd.email} already registered") }
    } else {
      cmd.toEvent.let { Effect().persist(it).thenReply(cmd.replyTo) { _ -> StatusReply.success(it.toEntity) } }
    }

  private fun onUpdateUser(state: State, cmd: UpdateUser): Effect<Event, State> =
    if (state.findUser(cmd.id) != null) {
      cmd.toEvent.let { Effect().persist(it).thenReply(cmd.replyTo) { st -> StatusReply.success(st.findUser(it.id))} }
    } else {
      Effect().none().thenReply(cmd.replyTo) { StatusReply.error("User with id ${cmd.id} not found") }
    }

  private fun onCreateNote(state: State, cmd: CreateNote): Effect<Event, State> =
    if (state.findUser(cmd.user) == null) {
      Effect().none().thenReply(cmd.replyTo) { StatusReply.error("User ${cmd.user} not found") }
    } else {
      cmd.toEvent.let { Effect().persist(it).thenReply(cmd.replyTo) { _ -> StatusReply.success(it.toResponse) } }
    }

  private fun onUpdateNote(state: State, cmd: UpdateNote): Effect<Event, State> =
    if (state.findNote(cmd.id) == null) {
      Effect().none().thenReply(cmd.replyTo) { StatusReply.error("Note with id ${cmd.id} not found for user with id ${cmd.user}") }
    } else {
      cmd.toEvent.let {
        Effect().persist(it).thenReply(cmd.replyTo) { st -> StatusReply.success(st.findNote(it.id)?.toResponse()) }
      }
    }

  private fun onDeleteNote(state: State, cmd: DeleteNote): Effect<Event, State> =
    if (state.findNote(cmd.id) == null) {
      Effect().none().thenReply(cmd.rt) { StatusReply.error("Note with id ${cmd.id} not found") }
    } else {
      Effect().persist(cmd.toEvent).thenReply(cmd.rt) { _ -> StatusReply.success(Done.done()) }
    }

  private fun onCreateTask(state: State, cmd: CreateTask): Effect<Event, State> =
    if (state.findUser(cmd.user) == null) {
      Effect().none().thenReply(cmd.replyTo) { StatusReply.error("User ${cmd.user} not found") }
    } else {
      cmd.toEvent.let {
        Effect().persist(it).thenReply(cmd.replyTo) { _ -> StatusReply.success(it.toResponse) }
      }
    }

  private fun onUpdateTask(state: State, cmd: UpdateTask): Effect<Event, State> =
    if (state.findTask(cmd.id) == null) {
      Effect().none().thenReply(cmd.replyTo) { StatusReply.error("Task with id ${cmd.id} not found for user with id ${cmd.user}") }
    } else {
      cmd.toEvent.let {
        Effect().persist(it).thenReply(cmd.replyTo) { st -> StatusReply.success(st.findTask(it.id)?.toResponse) }
      }
    }

  private fun onDeleteTask(state: State, cmd: DeleteTask): Effect<Event, State> =
    if (state.findTask(cmd.id) == null) {
      Effect().none().thenReply(cmd.replyTo) { StatusReply.error("Task ${cmd.id} not found") }
    } else {
      Effect().persist(cmd.toEvent).thenReply(cmd.replyTo) { StatusReply.success(Done.done()) }
    }

  private fun onCreateTag(state: State, cmd: CreateTag): Effect<Event, State> =
    if (state.find<Tag>(cmd.id) == null) {
      Effect().persist(cmd.toEvent).thenReply(cmd.replyTo) { _ -> StatusReply.success(cmd.toEvent.toEntity.toResponse()) }
    } else {
      Effect().none().thenReply(cmd.replyTo) { StatusReply.error("Tag ${cmd.label} could not be created") }
    }

  override fun eventHandler(): EventHandler<State, Event> = newEventHandlerBuilder()
    .forAnyState()
    .onEvent(UserCreated::class.java) { state, event -> state.save(event.toEntity).also { reader.processEvent(event) } }
    .onEvent(UserUpdated::class.java) { state, event -> (state.findUser(event.id)?.let { state.save(it.update(event)) } ?: state).also { reader.processEvent(event) }}
    .onEvent(UserDeleted::class.java) { state, event -> state.deleteUser(event.id).also { reader.processEvent(event) } }
    .onEvent(NoteCreated::class.java) { state, event -> state.save(event.toEntity).also { reader.processEvent(event) } }
    .onEvent(NoteUpdated::class.java) { state, event -> (state.findNote(event.id)?.let { state.save(it.update(event)) } ?: state).also { reader.processEvent(event) }}
    .onEvent(NoteDeleted::class.java) { state, event -> state.deleteNote(event.id).also { reader.processEvent(event) } }
    .onEvent(TaskCreated::class.java) { state, event -> state.save(event.toEntity).also { reader.processEvent(event) } }
    .onEvent(TaskUpdated::class.java) { state, event -> (state.findTask(event.id)?.let { state.save(it.update(event)) } ?: state).also { reader.processEvent(event) }}
    .onEvent(TaskDeleted::class.java) { state, event -> state.deleteTask(event.id).also { reader.processEvent(event) } }
    .onEvent(TagCreated::class.java) { state, event -> state.save(event.toEntity).also { reader.processEvent(event) } }
    .build()

  override fun signalHandler(): SignalHandler<State> = newSignalHandlerBuilder().onSignal(RecoveryCompleted::class.java, this::recovered).build()

  private fun recovered(state: State, rc: RecoveryCompleted): RecoveryCompleted = rc.apply {
    logger.info("recovery completed: users: {}, notes: {}, tasks: {}", state.userCount(), state.noteCount(), state.taskCount())
    state.setRecovered()
    reader.setRecovered()
  }

  companion object {
    fun create(pid: String, reader: Reader): Behavior<Command> = Processor(PersistenceId.of("konomas", pid), reader)
  }
}
