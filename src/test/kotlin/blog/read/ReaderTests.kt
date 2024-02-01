package blog.read

import blog.model.LoggedIn
import blog.model.NoteDeleted
import blog.model.NoteEvent
import blog.model.TaskCreated
import blog.model.UserRegistered
import blog.model.nextId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReaderTests {

  private val userId = nextId()

  @Test
  fun `new user`() {
    val reader = Reader()
    reader.processEvent(UserRegistered(userId, "test@tester.nl", "Tester", "Welkom123!", LocalDate.now().minusYears(42)))
    assertThat(reader.allUsers()).hasSize(1)
    assertThat(reader.findUserById(userId)).isNotNull

    reader.processEvent(UserRegistered(nextId(), "break@tester.nl", "Breaker", "Welkom124!", LocalDate.now().minusYears(42)))
    assertThat(reader.allUsers()).hasSize(2)
  }

  @Test
  fun `new note`() {
    val reader = Reader()
    val noteId = nextId()
    reader.processEvent(NoteEvent(noteId, userId, "title", "body"))
    assertThat(reader.allNotes()).hasSize(1)
    assertThat(reader.findNotesForUser(userId)).hasSize(1)

    reader.processEvent(NoteDeleted(noteId, userId))
    assertThat(reader.allNotes()).isEmpty()
  }

  @Test
  fun `new task`() {
    val reader = Reader()
    val taskId = nextId()
    reader.processEvent(TaskCreated(taskId, userId, "title", "body", LocalDateTime.now().plusHours(4)))
    assertThat(reader.allTasks()).hasSize(1)
    assertThat(reader.findTasksForUser(userId)).hasSize(1)
    assertThat(reader.findTaskById(taskId)).isNotNull
  }

  @Test
  fun login() {
    val reader = Reader()
    reader.processEvent(UserRegistered(userId, "test@tester.nl", "Tester", "Welkom123!", LocalDate.now().minusYears(42)))
    assertThat(reader.allUsers()).hasSize(1)
    assertThat(reader.findUserById(userId)).isNotNull

    val user = reader.findUserById(userId)
    assertThat(user).isNotNull
    reader.processEvent(LoggedIn("test", user!!, "test@tester.nl", "Welkom123!"))
    assertThat(reader.allSessions()).hasSize(1)
    val session = reader.allSessions().first()
    assertThat(session.user.id).isEqualTo(userId)
    val loggedin = reader.loggedin(session.token)
    assertThat(loggedin).isNotNull

    println("session.id: ${session.token}, access_token: ${session.toToken().access_token}")
  }

  @Test
  fun `all together now`() {
    val reader = Reader()
    reader.processEvent(UserRegistered(userId, "test@tester.nl", "Tester", "Welkom123!", LocalDate.now().minusYears(42)))
    assertThat(reader.allUsers()).hasSize(1)

    reader.processEvent(NoteEvent(nextId(), userId, "title", "body"))
    assertThat(reader.allNotes()).hasSize(1)
    assertThat(reader.findNotesForUser(userId)).hasSize(1)

    reader.processEvent(TaskCreated(nextId(), userId, "title", "body", LocalDateTime.now().plusHours(4)))
    assertThat(reader.allTasks()).hasSize(1)
    assertThat(reader.findTasksForUser(userId)).hasSize(1)
  }
}
