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

data class CreateNoteRequest(
  val user: UUID,
  val title: String,
  val body: String
) {
  init {
    validate(this) {
      validate(CreateNoteRequest::title).isNotBlank()
      validate(CreateNoteRequest::body).isNotBlank()
    }
  }

  fun toCommand(replyTo: ReplyTo) = CreateNote(user, title = title, body = body, replyTo = replyTo)
}

data class CreateNote(
  val user: UUID,
  val id: UUID = UUID.randomUUID(),
  val created: LocalDateTime = LocalDateTime.now(),
  val title: String,
  val body: String,
  val replyTo: ReplyTo
) : Command {
  fun toEvent() = NoteEvent(id, user, created, Encode.forHtml(title), Encode.forHtml(body))
}

data class NoteEvent(
  val id: UUID,
  val user: UUID,
  val created: LocalDateTime,
  val title: String,
  val body: String
) : Event {
  fun toEntity() = Note(id, user, created, title, body)
}

data class Note(
  override val id: UUID,
  val user: UUID,
  val created: LocalDateTime,
  val title: String,
  val body: String
): Entity {
  fun toResponse() = NoteResponse(id, user, created, title, body)
}

data class NoteResponse(
  override val id: UUID,
  val user: UUID,
  val created: LocalDateTime,
  val title: String,
  val body: String
) : Response
