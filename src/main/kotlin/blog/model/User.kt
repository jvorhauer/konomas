package blog.model

import blog.Command
import blog.DTF
import blog.Entity
import blog.Event
import blog.ReplyTo
import blog.Response
import org.owasp.encoder.Encode
import org.valiktor.functions.hasSize
import org.valiktor.functions.isEmail
import org.valiktor.functions.isLessThan
import org.valiktor.functions.isNotBlank
import org.valiktor.validate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


data class RegisterUserRequest(
  val email: String,
  val name: String,
  val password: String,
  val born: LocalDate,
) {
  init {
    validate(this) {
      validate(RegisterUserRequest::email).isEmail()
      validate(RegisterUserRequest::name).isNotBlank()
      validate(RegisterUserRequest::password).isNotBlank().hasSize(min = 7, max = 1024)
      validate(RegisterUserRequest::born).isLessThan(LocalDate.now().minusYears(6))
    }
  }

  fun toCommand(replyTo: ReplyTo): RegisterUser = RegisterUser(this, replyTo)
}

data class LoginRequest(
  val username: String,
  val password: String
) {
  init {
    validate(this) {
      validate(LoginRequest::username).isEmail()
      validate(LoginRequest::password).isNotBlank().hasSize(min = 7, max = 1024)
    }
  }
}

data class Follow(
  val leader: User,
  val follower: User,
  val rt: ReplyTo
) : Command {
  fun toEvent() = FollowedEvent(leader, follower)
}

data class UserSession(
  val id: String,
  val user: User,
  var started: Long = System.currentTimeMillis()
)

data class RegisterUser(
  val id: UUID = UUID.randomUUID(),
  val email: String,
  val name: String,
  val password: String,
  val born: LocalDate,
  val replyTo: ReplyTo
) : Command {
  constructor(rur: RegisterUserRequest, replyTo: ReplyTo) : this(
    UUID.randomUUID(),
    rur.email,
    rur.name,
    rur.password,
    rur.born,
    replyTo
  )

  fun toEvent() = UserEvent(id, Encode.forHtml(email), Encode.forHtml(name), hash(password), born)

  companion object {
    private val md = MessageDigest.getInstance("SHA-256")
    private fun toHex(ba: ByteArray) = ba.joinToString(separator = "") { String.format(Locale.US, "%02x", it) }
    fun hash(s: String): String = toHex(md.digest(s.toByteArray(StandardCharsets.UTF_8)))
  }
}

// User is Entity and Event as these two have the exact same fields.
data class UserEvent(
  val id: UUID,
  val email: String,
  val name: String,
  val password: String,
  val born: LocalDate,
  val joined: LocalDate = LocalDate.now()
) : Event {
  fun toEntity(): User = User(id, email, name, password, born, joined)
}

data class FollowedEvent(val leader: User, val follower: User)

data class User(
  override val id: UUID,
  val email: String,
  val name: String,
  val password: String,
  val born: LocalDate,
  val joined: LocalDate = LocalDate.now(),
  val notes: MutableList<Note> = mutableListOf()
): Entity {
  fun toResponse(): UserResponse = UserResponse(id, email, name, born.format(DTF), joined.format(DTF), notes.map { it.toResponse() })
}

data class UserResponse(
  override val id: UUID,
  val email: String,
  val name: String,
  val born: String,
  val joined: String,
  val notes: List<NoteResponse>
) : Response


data class UserState(private val users: MutableMap<UUID, User> = ConcurrentHashMap(), var recovered: Boolean = false) {
  private val sessions: MutableMap<String, UserSession> = mutableMapOf()
  private val notes: MutableMap<UUID, Note> = mutableMapOf()
  private val timeout = Duration.ofHours(4).toMillis()

  fun save(u: User): UserState = users.put(u.id, u).let { this }
  fun find(a: Any): User? = when (a) {
    is UUID -> users[a]
    is String -> users.values.find { it.email == a }
    else -> null
  }
  fun exists(a: Any): Boolean = find(a) != null
  fun notExists(id: UUID): Boolean = !exists(id)
  fun findAll(): List<User> = users.values.toList()

  fun login(username: String, password: String): String? {
    val user = find(username)
    return if (user != null && user.password == RegisterUser.hash(password)) {
      val sessionId = UUID.randomUUID().toString()
      sessions[sessionId] = UserSession(sessionId, user)
      sessionId
    } else {
      null
    }
  }
  fun loggedin(session: String): User? =
    if ((sessions[session]?.started ?: 0L) > (System.currentTimeMillis() - timeout)) {
      sessions[session]?.started = System.currentTimeMillis()   // each login resets counter, to avoid surprises
      sessions[session]?.user
    } else {
      null
    }

  fun addNote(note: Note): UserState = find(note.user)
    .apply { this?.notes?.add(note) }
    .also { if (it != null ) notes[note.id] = note }
    .let { this }

  fun updateNote(note: Note): UserState = find(note.user)
    .apply { if (this != null && this.notes.removeIf { it.id == note.id }) addNote(note) }
    .also { notes[note.id] = note }
    .let { this }

  fun deleteNote(note: Note): UserState = find(note.user)
    .apply { this?.notes?.removeIf { it.id == note.id } }
    .also { notes.remove(note.id) }
    .let { this }

  fun findNote(id: UUID): Note? = notes[id]
}
