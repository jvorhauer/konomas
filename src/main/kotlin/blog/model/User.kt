package blog.model

import java.time.Instant
import java.time.ZonedDateTime
import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern.ask
import akka.pattern.StatusReply
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.hypersistence.tsid.TSID
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.future.await
import blog.config.Konfig
import blog.read.Reader

data class RegisterUserRequest(
  val email: String,
  val name: String,
  val password: String,
) : Request {
  fun toCommand(replyTo: ActorRef<StatusReply<User>>): CreateUser = CreateUser(this, replyTo)
}

data class UpdateUserRequest(
  val name: String?,
  val password: String?
): Request {
  fun toCommand(id: String, replyTo: ActorRef<StatusReply<User>>) = UpdateUser(id, name, password, replyTo)
}

data class LoginRequest(val username: String, val password: String): Request

// Commands

data class CreateUser(
  val email: String,
  val name: String,
  val password: String,
  val replyTo: ActorRef<StatusReply<User>>,
  val id: String = nextId(),
) : Command {
  constructor(rur: RegisterUserRequest, replyTo: ActorRef<StatusReply<User>>) : this(rur.email, rur.name, rur.password, replyTo)

  fun toEvent() = UserCreated(id, email, name.encode, password.hashed)
}

data class UpdateUser(
  val id: String,
  val name: String?,
  val password: String?,
  val replyTo: ActorRef<StatusReply<User>>,
): Command {
  fun toEvent() = UserUpdated(id, name, password?.hashed)
}

// Events

data class UserCreated(
  val id: String,
  val email: String,
  val name: String,
  val password: String,
) : Event {
  fun toEntity(): User = User(id, email, name, password)
}

data class UserUpdated(
  val id: String,
  val name: String?,
  val password: String?
): Event

data class UserDeleted(val id: String) : Event

// Entitites

data class User(
  override val id: String,
  val email: String,
  val name: String,
  val password: String,
  val gravatar: String = email.gravatar,
  val joined: ZonedDateTime = TSID.from(id).instant.atZone(CET),
  val updated: ZonedDateTime = znow()
) : Entity {
  fun toResponse(reader: Reader): UserResponse = UserResponse(
    id,
    email,
    name,
    DTF.format(joined),
    gravatar,
    reader.findNotesForUser(id).map(Note::toResponse),
    reader.findTasksForUser(id).map(Task::toResponse)
  )
  fun update(uu: UserUpdated): User = this.copy(
    name = uu.name ?: this.name, password = uu.password ?: this.password, updated = znow()
  )
  override fun hashCode(): Int = id.hashCode()
  override fun equals(other: Any?): Boolean = equals(this, other)
}

// Responses

data class UserResponse(
  val id: String,
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
      val user = reader.findUserByEmail(loginRequest.username) ?: return@post call.respondText("user not found", status = Unauthorized)
      val token = createJwtToken(user.id, kfg)
      call.respond(hashMapOf("token" to token))
    }
  }

fun createJwtToken(uid: String, kfg: Konfig): String = JWT.create()
  .withAudience(kfg.jwt.audience)
  .withClaim("uid", uid)
  .withExpiresAt(Instant.now().plusMillis(kfg.jwt.expiresIn))
  .withIssuer(kfg.jwt.issuer)
  .sign(Algorithm.HMAC256(kfg.jwt.secret))


fun Route.usersRoute(processor: ActorRef<Command>, reader: Reader, scheduler: Scheduler, kfg: Konfig): Route =
  route("/api/users") {
    get {
      val start = call.request.queryParameters["start"]?.toInt() ?: 0
      val rows = call.request.queryParameters["rows"]?.toInt() ?: 10
      call.respond(reader.allUsers(rows, start).map { it.toResponse(reader) })
    }
    authenticate(kfg.jwt.realm) {
      get("/tasks") {
        val userId = userIdFromJWT(call) ?: return@get call.respondText("Unauthorized", status = Unauthorized)
        call.respond(reader.findTasksForUser(userId).map { it.toResponse() })
      }
      get("/me") {
        val id = userIdFromJWT(call) ?: return@get call.respondText("Unauthorized", status = Unauthorized)
        val user = reader.findUser(id) ?: return@get call.respondText("user not found for $id", status = NotFound)
        call.respond(user.toResponse(reader))
      }
      get("/notes") {
        val userId = userIdFromJWT(call) ?: return@get call.respond(Unauthorized, "Unauthorized")
        call.respond(reader.findNotesForUser(userId).sortedBy { it.created }.map { it.toResponse() })
      }
      put {
        val userId = userIdFromJWT(call) ?: return@put call.respond(Unauthorized, "Unauthorized")
        val uur = call.receive<UpdateUserRequest>()
        ask(processor, { rt -> uur.toCommand(userId, rt) }, timeout, scheduler).await().let {
          if (it.isSuccess) {
            call.respond(it.value.toResponse(reader))
          } else {
            call.respondText(it.error.message ?: "unknown error", status = BadRequest)
          }
        }
      }
    }
    get("{id?}") {
      val id = call.parameters["id"] ?: return@get call.respondText("no user id specified", status = BadRequest)
      val user: User = reader.findUser(id) ?: return@get call.respondText("user not found for $id", status = NotFound)
      call.respond(user.toResponse(reader))
    }
    post {
      val cnu = call.receive<RegisterUserRequest>()
      ask(processor, { rt -> cnu.toCommand(rt) }, timeout, scheduler).await().let {
        if (it.isSuccess) {
          call.response.header("Location", "/api/users/${it.value.id}")
          call.respond(HttpStatusCode.Created, hashMapOf("token" to createJwtToken(it.value.id, kfg)))
        } else {
          call.respondText(it.error.message ?: "unknown error", status = BadRequest)
        }
      }
    }
  }
