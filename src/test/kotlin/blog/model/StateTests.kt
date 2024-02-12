package blog.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class StateTests {

  private val pw = "Welkom123!".hashed()

  @Test
  fun `new user`() {
    val state = State()
    val user = User(nextId(), "test@tester.nl", "Tester", pw)
    val state2 = state.save(user)
    assertThat(state2.userCount()).isEqualTo(1)

    val found = state2.findUser(user.id)
    assertThat(found).isNotNull
    assertThat(found?.name).isEqualTo("Tester")

    val notFound = state2.findUser(nextId())
    assertThat(notFound).isNull()

    assertThat(state2.allUsers()).hasSize(1)
  }

  @Test
  fun `update user`() {
    val state = State()
    val user = User(nextId(), "test@tester.nl", "Tester", pw)
    val state2 = state.save(user)
    assertThat(state2.userCount()).isEqualTo(1)
  }

  @Test
  fun `new note`() {
    val state = State()
    val user = User(nextId(), "test@tester.nl", "Tester", pw)
    val note = Note(nextId(), user.id, "Test", "Testing, 1.. 2..")
    val state2 = state.save(user)
    val state3 = state2.save(note)
    assertThat(state3.userCount()).isEqualTo(1)
    assertThat(state3.noteCount()).isEqualTo(1)
    assertThat(state3.findNote(note.id)).isNotNull
    assertThat(state3.findNote(note.id)?.title).isEqualTo("Test")
    assertThat(state3.findNotesForUser(user.id)).hasSize(1)
  }

  @Test
  fun `update note`() {
    val state = State()
    val user = User(nextId(), "test@tester.nl", "Tester", pw)
    val note = Note(nextId(), user.id, "Test", "Testing, 1.. 2..")
    val state2 = state.save(user)
    val state3 = state2.save(note)
    assertThat(state3.userCount()).isEqualTo(1)
    assertThat(state3.noteCount()).isEqualTo(1)

    val update: Note = note.copy(title = "Title")
    val state4: State = state3.save(update)
    val updated: Note? = state4.findNote(update.id)
    assertThat(updated).isNotNull
    assertThat(updated?.title).isEqualTo("Title")

    val update2: Note = note.update(NoteUpdated(note.id, user.id, "Updated", null))
    val state5 = state4.save(update2)
    val updated2 = state5.findNote(update2.id)
    assertThat(updated2).isNotNull
    assertThat(updated2?.title).isEqualTo("Updated")
  }

  @Test
  fun `new task`() {
    val state = State()
    val user = User(nextId(), "test@tester.nl", "Tester", pw)
    val state2 = state.save(user)
    val task = Task(nextId(), user.id, "Test", "test", "Tasking, 1.. 2..", LocalDateTime.now().plusHours(1))
    val state3 = state2.save(task)
    assertThat(state3.taskCount()).isEqualTo(1)
    assertThat(state3.findTask(task.id)).isNotNull
    assertThat(state3.findTasksForUser(user.id)).hasSize(1)

    val state4 = state3.save(task.copy(title = "Title"))
    assertThat(state4.taskCount()).isEqualTo(1)
    assertThat(state3.findTask(task.id)).isNotNull
    assertThat(state3.findTasksForUser(user.id)).hasSize(1)
  }
}
