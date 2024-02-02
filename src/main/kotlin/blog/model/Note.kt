package blog.model

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.hypersistence.tsid.TSID
import jakarta.validation.ConstraintViolation
import org.owasp.encoder.Encode
import java.time.LocalDateTime
import java.time.ZoneId

data class CreateNoteRequest(val user: String, val title: String, val body: String) {
  fun validate(): Set<ConstraintViolation<CreateNoteRequest>> = Validator.validate(this)
  fun toCommand(replyTo: ActorRef<StatusReply<NoteResponse>>) = CreateNote(user.toTSID(), title, body, replyTo)
}

data class UpdateNoteRequest(val user: String, val id: String, val title: String?, val body: String?) {
  fun validate(): Set<ConstraintViolation<UpdateNoteRequest>> = Validator.validate(this)
  fun toCommand(rt: ActorRef<StatusReply<NoteResponse>>) = UpdateNote(user.toTSID(), id.toTSID(), title, body, rt)
}

data class CreateNote(
  val user: TSID,
  val title: String,
  val body: String,
  val replyTo: ActorRef<StatusReply<NoteResponse>>,
  val id: TSID = nextId()
) : Command {
  fun toEvent() = NoteCreated(id, user, Encode.forHtml(title), Encode.forHtml(body))
}

data class UpdateNote(val user: TSID, val id: TSID, val title: String?, val body: String?, val replyTo: ActorRef<StatusReply<NoteResponse>>): Command {
  fun toEvent() = NoteUpdated(id, user, title, body)
}

data class DeleteNote(val user: TSID, val id: TSID, val rt: ActorRef<StatusReply<Done>>): Command {
  fun toEvent() = NoteDeleted(id, user)
}

data class NoteCreated(val id: TSID, val user: TSID, val title: String, val body: String) : Event {
  fun toEntity() = Note(id, user, title, slugify(title), body)
}

data class NoteUpdated(val id: TSID, val user: TSID, val title: String?, val body: String?): Event

data class NoteDeleted(val id: TSID, val user: TSID): Event

data class Note(
  override val id: TSID,
  val user: TSID,
  val title: String,
  val slug: String,
  val body: String
): Entity {
  constructor(id: TSID, user: TSID, title: String, body: String): this(id, user, title, slugify(title), body)
  fun update(nu: NoteUpdated): Note = this.copy(title = nu.title ?: this.title, body = nu.body ?: this.body)
  fun toResponse() = NoteResponse(id.toString(), LocalDateTime.ofInstant(id.instant, ZoneId.of("CET")), title, body)
}

data class NoteResponse(
  override val id: String,
  val created: LocalDateTime,
  val title: String,
  val body: String
) : Response
