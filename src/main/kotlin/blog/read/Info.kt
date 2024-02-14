package blog.read

import blog.model.TaskStatus
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.info(reader: Reader) {
  route("/info") {
    get("/ready") {
      if (reader.isReady()) {
        call.respond(HttpStatusCode.OK, "Ready")
      } else {
        call.respond(HttpStatusCode.ServiceUnavailable, "Not Ready")
      }
    }
    get("/alive") {
      if (reader.isReady()) {
        call.respond(HttpStatusCode.OK, "Alive")
      } else {
        call.respond(HttpStatusCode.ServiceUnavailable, "Dead")
      }
    }
    get("/counts") {
      call.respond(HttpStatusCode.OK, reader.counts())
    }
    get("/stati") {
      call.respond(HttpStatusCode.OK, mapOf("stati" to TaskStatus.entries))
    }
  }
}
