package blog.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import blog.read.Reader
import io.hypersistence.tsid.TSID
import jakarta.validation.ConstraintViolation
import org.owasp.encoder.Encode
import java.time.LocalDate
import java.time.ZoneId

data class RegisterUserRequest(
  val email: String,
  val name: String,
  val password: String,
  val born: LocalDate,
) {
  fun validate(): Set<ConstraintViolation<RegisterUserRequest>> = Validator.validate(this)
  fun toCommand(replyTo: ActorRef<StatusReply<UserResponse>>): CreateUser = CreateUser(this, replyTo)
}

data class LoginRequest(val username: String, val password: String) {
  fun toCommand(replyTo: ActorRef<StatusReply<OAuthToken>>) = Login(username, password, replyTo)
}

// Commands

data class CreateUser(
  val id: TSID = nextId(),
  val email: String,
  val name: String,
  val password: String,
  val born: LocalDate,
  val replyTo: ActorRef<StatusReply<UserResponse>>
) : Command {
  constructor(rur: RegisterUserRequest, replyTo: ActorRef<StatusReply<UserResponse>>) : this(nextId(), rur.email, rur.name, rur.password, rur.born, replyTo)
  fun toEvent() = UserCreated(id, Encode.forHtml(email), Encode.forHtml(name), Hasher.hash(password), born)
}

data class Login(val username: String, val password: String, val replyTo: ActorRef<StatusReply<OAuthToken>>): Command {
  fun toEvent(user: User) = LoggedIn(Hasher.hash(nextId().encode(32)), user, username, password)
}

// Events

data class UserCreated(
  val id: TSID,
  val email: String,
  val name: String,
  val password: String,
  val born: LocalDate
) : Event {
  fun toEntity(): User = User(id, email, name, password, born)
}

data class LoggedIn(
  val token: String,
  val user: User,
  val username: String,
  val password: String
): Event {
  fun toSession() = Session(token, user)
}

// Entitites

data class User(
  override val id: TSID,
  val email: String,
  val name: String,
  val password: String,
  val born: LocalDate,
): Entity {
  fun toResponse(reader: Reader): UserResponse = UserResponse(
    id.toString(),
    email,
    name,
    DTF.format(born),
    DTF.format(id.instant.atZone(ZoneId.of("CET"))),
    reader.findNotesForUser(id).map(Note::toResponse),
    reader.findTasksForUser(id).map(Task::toResponse)
  )
}

data class Session(val token: String, val user: User, var started: Long = System.currentTimeMillis()): CborSerializable {
  fun toToken() = OAuthToken(token, user.id.toString())
}

// Responses

data class UserResponse(
  override val id: String,
  val email: String,
  val name: String,
  val born: String,
  val joined: String,
  val notes: List<NoteResponse>,
  val tasks: List<TaskResponse>
) : Response

data class OAuthToken(val access_token: String, override val id: String, val token_type: String = "bearer", val expires_in: Int = 7200) : Response
