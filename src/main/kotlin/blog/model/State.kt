package blog.model

data class State(
  private val users: Map<Long, User>      = mapOf(),
  private val notes: Map<Long, Note>      = mapOf(),
  private val tasks: Map<Long, Task>      = mapOf(),
  private val tags : Map<Long, Tag>       = mapOf(),
  private val recovered: Boolean          = false
) {
  fun save(u: User)                 : State      = this.copy(users = this.users.minus(u.id).plus(u.id to u))
  fun findUser(id: Long)            : User?      = users[id]
  fun findUserByEmail(email: String): User?      = users.values.find { it.email == email }
  fun allUsers()                    : List<User> = users.values.toList()
  fun userCount()                   : Int        = users.size
  fun deleteUser(id: Long)          : State      = this.copy(users = this.users.minus(id))

  fun save(n: Note)             : State          = this.copy(notes = this.notes.minus(n.id).plus(n.id to n))
  fun findNote(id: Long)        : Note?          = notes[id]
  fun findNotesForUser(id: Long): List<Note>     = notes.values.filter { it.user == id }
  fun noteCount()               : Int            = notes.size
  fun deleteNote(id: Long)      : State          = this.copy(notes = this.notes.minus(id))

  fun save(t: Task)             : State          = this.copy(tasks = this.tasks.minus(t.id).plus(t.id to t))
  fun findTask(id: Long)        : Task?          = tasks[id]
  fun findTasksForUser(id: Long): List<Task>     = tasks.values.filter { it.user == id }
  fun taskCount()               : Int            = tasks.size
  fun deleteTask(id: Long)      : State          = this.copy(tasks = this.tasks.minus(id))

  fun setRecovered(): State = this.copy(recovered = true)
}
