package blog.module

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.status() {
  install(StatusPages) {
    exception<RequestValidationException> { call, cause ->
      call.respondText(cause.reasons.joinToString(", "), status = BadRequest)
    }
    exception<BadRequestException> { call, _ ->
      call.respondText("invalid json in request body", status = BadRequest)
    }
  }
}
