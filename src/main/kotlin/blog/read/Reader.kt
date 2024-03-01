package blog.read

import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import blog.model.Event
import blog.model.Note
import blog.model.NoteCreated
import blog.model.NoteDeleted
import blog.model.NoteUpdated
import blog.model.Tag
import blog.model.TagCreated
import blog.model.Task
import blog.model.TaskCreated
import blog.model.TaskDeleted
import blog.model.TaskUpdated
import blog.model.User
import blog.model.UserCreated
import blog.model.UserDeleted
import blog.model.UserUpdated

class Reader(
  private val users: MutableMap<String, User> = ConcurrentHashMap(9),
  private val tasks: MutableMap<String, Task> = ConcurrentHashMap(99),
  private val notes: MutableMap<String, Note> = ConcurrentHashMap(99),
  private val tags: MutableMap<String, Tag>   = ConcurrentHashMap(9),
  private var serverReady: Boolean = false,
  private var recovered: Boolean = false,
) {
  fun findUser(id: String): User? = users[id]
  fun findUserByEmail(email: String): User? = users.values.find { it.email == email }
  fun exists(email: String): Boolean = findUserByEmail(email) != null
  private fun canAuthenticate(email: String, password: String): Boolean = users.values.find { it.email == email && it.password == password } != null
  fun canNotAuthenticate(email: String, password: String): Boolean = !canAuthenticate(email, password)
  fun allUsers(rows: Int = 10, start: Int = 0): List<User> = users.values.drop(start * rows).take(rows)

  inline fun <reified T> find(id: String): T? = when (T::class) {
    User::class -> findUser(id) as T?
    Note::class -> findNote(id) as T?
    Task::class -> findTask(id) as T?
    Tag::class  -> findTag(id)  as T?
    else -> throw IllegalArgumentException("Reader.find: Unsupported type: ${T::class}")
  }

  fun findNote(id: String): Note? = notes[id]
  fun findNotesForUser(id: String): List<Note> = notes.values.filter { it.user == id }
  fun allNotes(rows: Int = 10, start: Int = 0): List<Note> = notes.values.drop(start * rows).take(rows)

  fun findTask(id: String): Task? = tasks[id]
  fun notExistsTask(id: String): Boolean = !tasks.containsKey(id)
  fun findTasksForUser(id: String): List<Task> = tasks.values.filter { it.user == id }
  fun allTasks(rows: Int = 10, start: Int = 0): List<Task> = tasks.values.drop(start * rows).take(rows)

  fun findTag(id: String): Tag? = tags[id]
  val allTags: List<Tag> get() = tags.values.toList()

  fun setServerReady() { serverReady = true }
  fun setRecovered() { recovered = true }
  val isReady: Boolean get() = serverReady && recovered

  fun counts(): Counts = Counts(users.size, notes.size, tasks.size, tags.size)

  fun processEvent(e: Event) {
    when (e) {
      is UserCreated -> users[e.id] = e.toEntity
      is UserUpdated -> if (users[e.id] != null) users[e.id] = users[e.id]!!.update(e)
      is UserDeleted -> users.remove(e.id)
      is NoteCreated -> notes[e.id] = e.toEntity
      is NoteUpdated -> if (notes[e.id] != null) notes[e.id] = notes[e.id]!!.update(e)
      is NoteDeleted -> notes.remove(e.id)
      is TaskCreated -> tasks[e.id] = e.toEntity
      is TaskUpdated -> if (tasks[e.id] != null) tasks[e.id] = tasks[e.id]!!.update(e)
      is TaskDeleted -> tasks.remove(e.id)
      is TagCreated  -> tags[e.id] = e.toEntity
      else -> logger.warn("could not processEvent {}", e)
    }
    logger.info("processEvent: $e")
  }

  companion object {
    private val logger = LoggerFactory.getLogger("Reader")
  }
}
