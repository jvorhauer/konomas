package blog.model

data class State(
  private val users: Map<String, User>      = mapOf(),
  private val notes: Map<String, Note>      = mapOf(),
  private val tasks: Map<String, Task>      = mapOf(),
  private val tags : Map<String, Tag>       = mapOf(),
  private val recovered: Boolean          = false
) {
  fun save(u: User)                 : State      = this.copy(users = this.users.minus(u.id).plus(u.id to u))
  fun findUser(id: String)          : User?      = users[id]
  fun findUserByEmail(email: String): User?      = users.values.find { it.email == email }
  fun allUsers()                    : List<User> = users.values.toList()
  fun userCount()                   : Int        = users.size
  fun deleteUser(id: String)        : State      = this.copy(users = this.users.minus(id))

  fun save(n: Note)               : State          = this.copy(notes = this.notes.minus(n.id).plus(n.id to n))
  fun findNote(id: String)        : Note?          = notes[id]
  fun findNotesForUser(id: String): List<Note>     = notes.values.filter { it.user == id }
  fun noteCount()                 : Int            = notes.size
  fun deleteNote(id: String)      : State          = this.copy(notes = this.notes.minus(id))

  fun save(t: Task)               : State          = this.copy(tasks = this.tasks.minus(t.id).plus(t.id to t))
  fun findTask(id: String)        : Task?          = tasks[id]
  fun findTasksForUser(id: String): List<Task>     = tasks.values.filter { it.user == id }
  fun taskCount()                 : Int            = tasks.size
  fun deleteTask(id: String)      : State          = this.copy(tasks = this.tasks.minus(id))

  fun setRecovered(): State = this.copy(recovered = true)
}
