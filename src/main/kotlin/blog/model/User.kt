package blog.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern.ask
import akka.pattern.StatusReply
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.owasp.encoder.Encode
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.future.await
import blog.read.Reader

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

fun Route.loginRoute(reader: Reader, kfg: Konfig): Route =
  route("/api/login") {
    post {
      val loginRequest = call.receive<LoginRequest>()
      val user: User = reader.findUserByEmail(loginRequest.username) ?: return@post call.respond(HttpStatusCode.Unauthorized, "user not found")
      val token: String = JWT.create()
        .withAudience(kfg.jwt.audience)
        .withClaim("uid", user.id)
        .withExpiresAt(Instant.now().plusMillis(kfg.jwt.expiresIn))
        .withIssuer(kfg.jwt.issuer)
        .sign(Algorithm.HMAC256(kfg.jwt.secret))
      call.respond(hashMapOf("token" to token))
    }
  }

fun Route.usersRoute(processor: ActorRef<Command>, reader: Reader, scheduler: Scheduler, kfg: Konfig): Route =
  route("/api/users") {
    get {
      val start = call.request.queryParameters["start"]?.toInt() ?: 0
      val rows = call.request.queryParameters["rows"]?.toInt() ?: 10
      call.respond(reader.allUsers(rows, start).map { it.toResponse(reader) })
    }
    authenticate(kfg.jwt.realm) {
      get("/tasks") {
        val userId = user(call) ?: return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        call.respond(reader.findTasksForUser(userId).map { it.toResponse() })
      }
      get("/me") {
        val userId = user(call) ?: return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        val user: User = reader.find(userId) ?: return@get call.respond(HttpStatusCode.NotFound, "User not found")
        call.respond(user.toResponse(reader))
      }
      get("/notes") {
        val userId = user(call) ?: return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        call.respond(reader.findNotesForUser(userId).map { it.toResponse() })
      }
    }
    get("{id?}") {
      val id = call.parameters["id"] ?: return@get call.respondText("no user id specified", status = HttpStatusCode.BadRequest)
      val user: User = reader.find(id.toLong()) ?: return@get call.respondText("user not found for $id", status = HttpStatusCode.NotFound)
      call.respond(user.toResponse(reader))
    }
    post {
      val cnu = call.receive<RegisterUserRequest>()
      ask(processor, { rt -> cnu.toCommand(rt) }, Duration.ofMinutes(1), scheduler).await().let {
        if (it.isSuccess) {
          call.response.header("Location", "/api/users/${it.value.id}")
          call.respond(HttpStatusCode.Created, it.value)
        } else {
          call.respond(HttpStatusCode.BadRequest, it.error.localizedMessage)
        }
      }
    }
  }
