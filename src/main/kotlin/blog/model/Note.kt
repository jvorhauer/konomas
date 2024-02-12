package blog.model

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import org.owasp.encoder.Encode
import java.time.LocalDateTime
import java.time.ZoneId

data class CreateNoteRequest(val title: String, val body: String) {
  fun toCommand(user: Long, replyTo: ActorRef<StatusReply<NoteResponse>>) = CreateNote(user, title, body, replyTo)
}

data class UpdateNoteRequest(val id: Long, val title: String?, val body: String?) {
  fun toCommand(user: Long, rt: ActorRef<StatusReply<NoteResponse>>) = UpdateNote(user, id, title, body, rt)
}

data class CreateNote(
  val user: Long,
  val title: String,
  val body: String,
  val replyTo: ActorRef<StatusReply<NoteResponse>>,
  val id: Long = nextId()
) : Command {
  fun toEvent() = NoteCreated(id, user, Encode.forHtml(title), Encode.forHtml(body))
}

data class UpdateNote(val user: Long, val id: Long, val title: String?, val body: String?, val replyTo: ActorRef<StatusReply<NoteResponse>>): Command {
  fun toEvent() = NoteUpdated(id, user, title, body)
}

data class DeleteNote(val id: Long, val rt: ActorRef<StatusReply<Done>>): Command {
  fun toEvent() = NoteDeleted(id)
}

data class NoteCreated(val id: Long, val user: Long, val title: String, val body: String) : Event {
  fun toEntity() = Note(id, user, title, slugify(title), body)
  fun toResponse() = this.toEntity().toResponse()
}

data class NoteUpdated(val id: Long, val user: Long, val title: String?, val body: String?): Event

data class NoteDeleted(val id: Long): Event

data class Note(
  override val id: Long,
  val user: Long,
  val title: String,
  val slug: String,
  val body: String
): Entity {
  constructor(id: Long, user: Long, title: String, body: String): this(id, user, title, slugify(title), body)
  fun update(nu: NoteUpdated): Note = this.copy(title = nu.title ?: this.title, body = nu.body ?: this.body)
  fun toResponse() = NoteResponse(id, user, DTF.format(LocalDateTime.ofInstant(id.toTSID().instant, ZoneId.of("CET"))), title, body)
}

data class NoteResponse(
  val id: Long,
  val user: Long,
  val created: String,
  val title: String,
  val body: String
)
