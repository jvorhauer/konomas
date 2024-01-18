package blog.model

import blog.Command
import blog.Entity
import blog.Event
import blog.ReplyTo
import blog.Response
import org.owasp.encoder.Encode
import org.valiktor.functions.isNotBlank
import org.valiktor.validate
import java.time.LocalDateTime
import java.util.UUID

fun now() = LocalDateTime.now()

data class CreateNoteRequest(val user: UUID, val title: String, val body: String) {
  init {
    validate(this) {
      validate(CreateNoteRequest::title).isNotBlank()
      validate(CreateNoteRequest::body).isNotBlank()
    }
  }
  fun toCommand(replyTo: ReplyTo) = CreateNote(user, title = title, body = body, replyTo = replyTo)
}

data class UpdateNoteRequest(val user: UUID, val id: UUID, val title: String, val body: String) {
  init {
    validate(this) {
      validate(UpdateNoteRequest::title).isNotBlank()
      validate(UpdateNoteRequest::body).isNotBlank()
    }
  }
  fun toCommand(rt: ReplyTo) = UpdateNote(user, id, title, body, rt)
}

data class CreateNote(
  val user: UUID,
  val id: UUID = UUID.randomUUID(),
  val created: LocalDateTime = now(),
  val title: String,
  val body: String,
  val replyTo: ReplyTo
) : Command {
  fun toEvent() = NoteEvent(id, user, created, Encode.forHtml(title), Encode.forHtml(body))
}

data class UpdateNote(val user: UUID, val id: UUID, val title: String, val body: String, val rt: ReplyTo): Command {
  fun toEvent(note: Note) = NoteUpdated(id, user, note.created, title, body)
}

data class DeleteNote(val user: UUID, val id: UUID, val rt: ReplyTo): Command {
  fun toEvent() = NoteDeleted(id, user)
}

data class NoteEvent(val id: UUID, val user: UUID, val created: LocalDateTime, val title: String, val body: String) : Event {
  fun toEntity() = Note(id, user, created, title, body)
}

data class NoteUpdated(val id: UUID, val user: UUID, val created: LocalDateTime, val title: String, val body: String): Event {
  fun toEntity() = Note(id, user, created, title, body)
}

data class NoteDeleted(val id: UUID, val user: UUID): Event {
  fun toResponse() = NoteDeletedResponse(id)
}

data class Note(override val id: UUID, val user: UUID, val created: LocalDateTime, val title: String, val body: String): Entity {
  fun toResponse() = NoteResponse(id, created, title, body)
}

data class NoteResponse(
  override val id: UUID,
  val created: LocalDateTime,
  val title: String,
  val body: String
) : Response

data class NoteDeletedResponse(override val id: UUID): Response
