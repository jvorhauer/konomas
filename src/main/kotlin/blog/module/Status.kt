package blog.module

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.status() {
  install(StatusPages) {
    exception<RequestValidationException> { call, cause ->
      call.respond(HttpStatusCode.BadRequest, cause.reasons.joinToString())
    }
  }
}
