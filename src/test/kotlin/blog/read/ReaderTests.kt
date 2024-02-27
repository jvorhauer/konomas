package blog.read

import blog.model.NoteCreated
import blog.model.NoteDeleted
import blog.model.TaskCreated
import blog.model.User
import blog.model.UserCreated
import blog.model.nextId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import blog.model.Task

class ReaderTests {

  private val userId = nextId

  @Test
  fun `new user`() {
    val reader = Reader()
    reader.processEvent(UserCreated(userId, "test@tester.nl", "Tester", "Welkom123!"))
    assertThat(reader.allUsers()).hasSize(1)
    assertThat(reader.findUser(userId)).isNotNull

    reader.processEvent(UserCreated(nextId, "break@tester.nl", "Breaker", "Welkom124!"))
    assertThat(reader.allUsers()).hasSize(2)

    val user: User? = reader.find(userId)
    assertThat(user).isNotNull

    assertThat(reader.find(userId) as User?).isNotNull
  }

  @Test
  fun `new note`() {
    val reader = Reader()
    val noteId = nextId
    reader.processEvent(NoteCreated(noteId, userId, "title", "body"))
    assertThat(reader.allNotes()).hasSize(1)
    assertThat(reader.findNotesForUser(userId)).hasSize(1)

    reader.processEvent(NoteDeleted(noteId))
    assertThat(reader.allNotes()).isEmpty()
  }

  @Test
  fun `new task`() {
    val reader = Reader()
    val taskId = nextId
    reader.processEvent(TaskCreated(taskId, userId, "title", "body", LocalDateTime.now().plusHours(4)))
    assertThat(reader.allTasks()).hasSize(1)
    assertThat(reader.findTasksForUser(userId)).hasSize(1)
    assertThat(reader.find<Task>(taskId)).isNotNull
  }

  @Test
  fun `all together now`() {
    val reader = Reader()
    reader.processEvent(UserCreated(userId, "test@tester.nl", "Tester", "Welkom123!"))
    assertThat(reader.allUsers()).hasSize(1)

    reader.processEvent(NoteCreated(nextId, userId, "title", "body"))
    assertThat(reader.allNotes()).hasSize(1)
    assertThat(reader.findNotesForUser(userId)).hasSize(1)

    reader.processEvent(TaskCreated(nextId, userId, "title", "body", LocalDateTime.now().plusHours(4)))
    assertThat(reader.allTasks()).hasSize(1)
    assertThat(reader.findTasksForUser(userId)).hasSize(1)
  }
}
