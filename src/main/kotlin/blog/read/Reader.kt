package blog.read

import blog.model.Counts
import blog.model.Event
import blog.model.Note
import blog.model.NoteCreated
import blog.model.NoteDeleted
import blog.model.NoteUpdated
import blog.model.Task
import blog.model.TaskCreated
import blog.model.TaskDeleted
import blog.model.TaskUpdated
import blog.model.User
import blog.model.UserCreated
import blog.model.UserDeleted
import blog.model.UserDeletedByEmail
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class Reader(
  private val users: MutableMap<Long, User> = ConcurrentHashMap(),
  private val tasks: MutableMap<Long, Task> = ConcurrentHashMap(),
  private val notes: MutableMap<Long, Note> = ConcurrentHashMap(),
  private var serverReady: Boolean = false,
  private var recovered: Boolean = false,
) {
  private val logger = LoggerFactory.getLogger("Reader")

  fun findUser(id: Long): User? = users[id]
  fun findUserByEmail(email: String): User? = users.values.find { it.email == email }
  fun exists(email: String): Boolean = findUserByEmail(email) != null
  fun canAuthenticate(email: String, password: String): Boolean = users.values.find { it.email == email && it.password == password } != null
  fun canNotAuthenticate(email: String, password: String): Boolean = !canAuthenticate(email, password)
  fun allUsers(rows: Int = 10, start: Int = 0): List<User> = users.values.drop(start * rows).take(rows)

  inline fun <reified T> find(id: Long): T? {
    return when (T::class) {
      User::class -> findUser(id) as T?
      Note::class -> findNote(id) as T?
      Task::class -> findTask(id) as T?
      else -> throw IllegalArgumentException("Reader.find: Unsupported type: ${T::class}")
    }
  }

  fun findNote(id: Long): Note? = notes[id]
  fun findNotesForUser(id: Long): List<Note> = notes.values.filter { it.user == id }
  fun allNotes(rows: Int = 10, start: Int = 0): List<Note> = notes.values.drop(start * rows).take(rows)

  fun findTask(id: Long): Task? = tasks[id]
  fun notExistsTask(id: Long): Boolean = !tasks.containsKey(id)
  fun findTasksForUser(id: Long): List<Task> = tasks.values.filter { it.user == id }
  fun allTasks(rows: Int = 10, start: Int = 0): List<Task> = tasks.values.drop(start * rows).take(rows)

  fun setServerReady() { serverReady = true }
  fun setRecovered() { recovered = true }
  fun isReady(): Boolean = serverReady && recovered

  fun counts(): Counts = Counts(users.size, notes.size, tasks.size)

  fun processEvent(e: Event) {
    logger.info("processEvent: {}", e)
    when (e) {
      is UserCreated -> users[e.id] = e.toEntity()
      is UserDeleted -> users.remove(e.id)
      is UserDeletedByEmail -> users.filter { it.value.email == e.email }.map { it.key }.forEach { users.remove(it) }
      is NoteCreated -> notes[e.id] = e.toEntity()
      is NoteUpdated -> if (notes[e.id] != null) notes[e.id] = notes[e.id]!!.update(e)
      is NoteDeleted -> notes.remove(e.id)
      is TaskCreated -> tasks[e.id] = e.toEntity()
      is TaskUpdated -> if (tasks[e.id] != null) tasks[e.id] = tasks[e.id]!!.update(e)
      is TaskDeleted -> tasks.remove(e.id)
      else -> logger.warn("could not processEvent {}", e)
    }
  }
}
