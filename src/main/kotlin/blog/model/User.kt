package blog.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import blog.read.Reader
import org.owasp.encoder.Encode
import java.time.ZoneId

data class RegisterUserRequest(
  val email: String,
  val name: String,
  val password: String,
) : Request {
  fun toCommand(replyTo: ActorRef<StatusReply<UserResponse>>): CreateUser = CreateUser(this, replyTo)
}

data class LoginRequest(val username: String, val password: String)
// Commands

data class CreateUser(
  val email: String,
  val name: String,
  val password: String,
  val replyTo: ActorRef<StatusReply<UserResponse>>,
  val id: Long = nextId(),
) : Command {
  constructor(rur: RegisterUserRequest, replyTo: ActorRef<StatusReply<UserResponse>>) : this(rur.email, rur.name, rur.password, replyTo)

  fun toEvent() = UserCreated(id, Encode.forHtml(email), Encode.forHtml(name), password.hashed())
}

// Events

data class UserCreated(
  val id: Long,
  val email: String,
  val name: String,
  val password: String,
) : Event {
  fun toEntity(): User = User(id, email, name, password)
  fun toResponse(reader: Reader) = this.toEntity().toResponse(reader)
}

data class UserDeleted(val id: Long) : Event
data class UserDeletedByEmail(val email: String) : Event

// Entitites

data class User(
  override val id: Long,
  val email: String,
  val name: String,
  val password: String,
  val gravatar: String = gravatarize(email)
) : Entity {
  fun toResponse(reader: Reader): UserResponse = UserResponse(
    id,
    email,
    name,
    DTF.format(id.toTSID().instant.atZone(ZoneId.of("CET"))),
    gravatar,
    reader.findNotesForUser(id).map(Note::toResponse),
    reader.findTasksForUser(id).map(Task::toResponse)
  )
}

// Responses

data class UserResponse(
  val id: Long,
  val email: String,
  val name: String,
  val joined: String,
  val gravatar: String,
  val notes: List<NoteResponse>,
  val tasks: List<TaskResponse>,
)
