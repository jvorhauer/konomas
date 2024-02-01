package blog.model

import io.hypersistence.tsid.TSID

data class State(
  private val users: Map<TSID, User> = mapOf(),
  private val notes: Map<TSID, Note> = mapOf(),
  private val tasks: Map<TSID, Task> = mapOf(),
  private val sesss: Map<String, Session> = mapOf(),
  private val recovered: Boolean = false
) {
  fun save(u: User)                 : State      = this.copy(users = this.users.minus(u.id).plus(u.id to u))
  fun findUserById(id: TSID)        : User?      = users[id]
  fun findUserByEmail(email: String): User?      = users.values.find { it.email == email }
  fun allUsers()                    : List<User> = users.values.toList()
  fun userCount()                   : Int        = users.size
  fun delete(u: User)               : State      = this.copy(users = this.users.minus(u.id))

  fun save(n: Note)             : State          = this.copy(notes = this.notes.minus(n.id).plus(n.id to n))
  fun findNoteById(id: TSID)    : Note?          = notes[id]
  fun findNotesForUser(id: TSID): List<Note>     = notes.values.filter { it.user == id }
  fun noteCount()               : Int            = notes.size

  fun save(t: Task)             : State          = this.copy(tasks = this.tasks.minus(t.id).plus(t.id to t))
  fun findTaskById(id: TSID)    : Task?          = tasks[id]
  fun findTasksForUser(id: TSID): List<Task>     = tasks.values.filter { it.user == id }
  fun taskCount()               : Int            = tasks.size

  fun save(us: Session)     : State = this.copy(sesss = this.sesss.plus(us.token to us))
  fun login(username: String, password: String): Session? {
    val user = findUserByEmail(username)
    return if (user != null && user.password == Hasher.hash(password)) {
      Session(nextId().toString(), user)
    } else {
      null
    }
  }
  fun loggedin(session: String): User? = sesss[session]?.user

  fun hasRecovered(): State = this.copy(recovered = true)
}
