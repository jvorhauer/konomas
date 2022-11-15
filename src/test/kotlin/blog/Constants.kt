package blog

import blog.model.NoteEvent
import blog.model.UserEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.util.UUID

object Constants {
  const val email = "web@test.er"
  const val password = "welkom123"
  const val name = "Web Tester"
  val born: LocalDate = LocalDate.of(1999, Month.SEPTEMBER, 11)

  private fun rid(): UUID = UUID.randomUUID()
  private fun remail(name: String): String = "$name@test.er"
  fun ruser(name: String): UserEvent = UserEvent(rid(), name, remail(name), password, born)

  fun rnote(user: UUID, title: String): NoteEvent = NoteEvent(rid(), user, LocalDateTime.now(), title, title)
}
