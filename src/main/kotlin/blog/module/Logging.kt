package blog.module

import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import org.slf4j.event.Level

fun Application.logging() {
  install(CallLogging) {
    level = Level.INFO
    filter { it.request.path().startsWith("/api") }
  }
}
