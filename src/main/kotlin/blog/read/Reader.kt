package blog.read

import blog.model.Event
import blog.model.LoggedIn
import blog.model.Note
import blog.model.NoteDeleted
import blog.model.NoteEvent
import blog.model.NoteUpdated
import blog.model.Task
import blog.model.TaskCreated
import blog.model.User
import blog.model.UserRegistered
import blog.model.UserResponse
import blog.model.Session
import io.hypersistence.tsid.TSID
import reactor.core.publisher.Mono

class Reader(
  private val users: MutableList<User> = mutableListOf(),
  private val notes: MutableList<Note> = mutableListOf(),
  private val tasks: MutableList<Task> = mutableListOf(),
  private val sessions: MutableList<Session> = mutableListOf()
) {
  fun findUserById(id: TSID): User? = users.find { it.id == id }
  fun findUserByEmail(email: String): Mono<User> = Mono.justOrEmpty(users.find { it.email == email })
  fun loggedin(s: String): User? {
    return sessions.find { it.token == s.replace("Bearer ", "") }?.user
  }
  fun allUsers(): List<UserResponse> = users.map { it.toResponse(this) }
  fun allSessions(): List<Session> = sessions

  fun findNote(id: TSID): Note? = notes.find { it.id == id }
  fun findNotesForUser(id: TSID): List<Note> = notes.filter { it.user == id }
  fun allNotes(): List<Note> = notes

  fun findTaskById(id: TSID): Task? = tasks.find { it.id == id }
  fun findTasksForUser(id: TSID): List<Task> = tasks.filter { it.user == id }
  fun allTasks(): List<Task> = tasks

  fun dump() {
    println("users: $users")
    println("notes: $notes")
    println("tasks: $tasks")
  }

  fun processEvent(e: Event) {
    when(e) {
      is UserRegistered -> users.add(e.toEntity())
      is NoteEvent -> notes.add(e.toEntity())
      is NoteUpdated -> notes.replaceAll { x -> if (x.id == e.id) x.copy(title = e.title ?: x.title, body = e.body ?: x.body) else x }
      is NoteDeleted -> notes.removeIf { it.id == e.id }
      is TaskCreated -> tasks.add(e.toEntity())
      is LoggedIn -> sessions.add(e.toSession())
      else -> println("could not processEvent $e")
    }
  }
}
