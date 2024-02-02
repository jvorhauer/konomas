package blog.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class StateTests {

  @Test
  fun `new user`() {
    val state = State()
    val user = User(nextId(), "test@tester.nl", "Tester", Hasher.hash("Welkom123!"), LocalDate.now().minusYears(42))
    val state2 = state.save(user)
    assertThat(state2.userCount()).isEqualTo(1)

    val found = state2.findUserById(user.id)
    assertThat(found).isNotNull
    assertThat(found?.name).isEqualTo("Tester")

    val notFound = state2.findUserById(nextId())
    assertThat(notFound).isNull()

    assertThat(state2.allUsers()).hasSize(1)
  }

  @Test
  fun `update user`() {
    val state = State()
    val user = User(nextId(), "test@tester.nl", "Tester", Hasher.hash("Welkom123!"), LocalDate.now().minusYears(42))
    val state2 = state.save(user)
    assertThat(state2.userCount()).isEqualTo(1)
  }

  @Test
  fun `new note`() {
    val state = State()
    val user = User(nextId(), "test@tester.nl", "Tester", Hasher.hash("Welkom123!"), LocalDate.now().minusYears(42))
    val note = Note(nextId(), user.id, "Test", "Testing, 1.. 2..")
    val state2 = state.save(user)
    val state3 = state2.save(note)
    assertThat(state3.userCount()).isEqualTo(1)
    assertThat(state3.noteCount()).isEqualTo(1)
    assertThat(state3.findNoteById(note.id)).isNotNull
    assertThat(state3.findNoteById(note.id)?.title).isEqualTo("Test")
    assertThat(state3.findNotesForUser(user.id)).hasSize(1)
  }

  @Test
  fun `update note`() {
    val state = State()
    val user = User(nextId(), "test@tester.nl", "Tester", Hasher.hash("Welkom123!"), LocalDate.now().minusYears(42))
    val note = Note(nextId(), user.id, "Test", "Testing, 1.. 2..")
    val state2 = state.save(user)
    val state3 = state2.save(note)
    assertThat(state3.userCount()).isEqualTo(1)
    assertThat(state3.noteCount()).isEqualTo(1)

    val update: Note = note.copy(title = "Title")
    val state4: State = state3.save(update)
    val updated: Note? = state4.findNoteById(update.id)
    assertThat(updated).isNotNull
    assertThat(updated?.title).isEqualTo("Title")

    val update2: Note = note.update(NoteUpdated(note.id, user.id, "Updated", null))
    val state5 = state4.save(update2)
    val updated2 = state5.findNoteById(update2.id)
    assertThat(updated2).isNotNull
    assertThat(updated2?.title).isEqualTo("Updated")
  }

  @Test
  fun `try login`() {
    val state = State()
    val user = User(nextId(), "test@tester.nl", "Tester", Hasher.hash("Welkom123!"), LocalDate.now().minusYears(42))
    val state2 = state.save(user)
    val session = state2.login("test@tester.nl", "Welkom123!")
    assertThat(session).isNotNull
    val state3 = state2.save(session!!)
    assertThat(state3.loggedin(session.token)).isNotNull

    val nosession = state2.login("someone@else.nl", "Welkom123!")
    assertThat(nosession).isNull()
  }

  @Test
  fun `new task`() {
    val state = State()
    val user = User(nextId(), "test@tester.nl", "Tester", Hasher.hash("Welkom123!"), LocalDate.now().minusYears(42))
    val state2 = state.save(user)
    val task = Task(nextId(), user.id, "Test", "test", "Tasking, 1.. 2..", LocalDateTime.now().plusHours(1))
    val state3 = state2.save(task)
    assertThat(state3.taskCount()).isEqualTo(1)

    assertThat(state3.findTaskById(task.id)).isNotNull
    assertThat(state3.findTasksForUser(user.id)).hasSize(1)
  }

  @Test
  fun `new comment and update`() {
    val state = State()
    val user = User(nextId(), "test@tester.nl", "Tester", Hasher.hash("Welkom123!"), LocalDate.now().minusYears(42))
    val state2 = state.save(user)
    val note = Note(nextId(), user.id, "Test", "Tasking, 1.. 2..")
    val state3 = state2.save(note)
    assertThat(state3.noteCount()).isEqualTo(1)
    val c = Comment(nextId(), user.id, note.id, null, "tekst", 5)
    val state4 = state3.save(c)
    assertThat(state4.findComment(c.id)).isNotNull
    assertThat(state4.findCommentsForUser(user.id)).isNotEmpty
    assertThat(state4.findCommentsForNote(note.id)).isNotEmpty
  }
}
