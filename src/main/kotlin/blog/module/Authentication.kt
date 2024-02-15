package blog.module

import blog.model.Konfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.authentication(kfg: Konfig) {
  install(Authentication) {
    jwt(kfg.jwt.realm) {
      verifier(
        JWT.require(Algorithm.HMAC256(kfg.jwt.secret)).withAudience(kfg.jwt.audience).withIssuer(kfg.jwt.issuer).build()
      )
      validate { credential ->
        if (credential.payload.getClaim("uid").asLong() != null) {
          JWTPrincipal(credential.payload)
        } else {
          null
        }
      }
      challenge {defaultSchema, realm ->
        call.respond(HttpStatusCode.Unauthorized, "token invalid or expired ($defaultSchema, $realm}")
      }
    }
  }
}
