package blog.route

import blog.model.Konfig
import blog.model.LoginRequest
import blog.model.User
import blog.read.Reader
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

fun Route.loginRoute(reader: Reader, kfg: Konfig): Route =
  route("/api/login") {
    post {
      val loginRequest = call.receive<LoginRequest>()
      val user: User = reader.findUserByEmail(loginRequest.username) ?: return@post call.respond(HttpStatusCode.Unauthorized, "user not found")
      val token: String = JWT.create()
        .withAudience(kfg.audience)
        .withClaim("uid", user.id)
        .withExpiresAt(Instant.now().plusMillis(kfg.expiresIn))
        .withIssuer(kfg.issuer)
        .sign(Algorithm.HMAC256(kfg.secret))
      call.respond(hashMapOf("token" to token))
    }
  }
