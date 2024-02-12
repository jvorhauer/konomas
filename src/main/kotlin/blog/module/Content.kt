package blog.module

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*

fun Application.content() {
  install(ContentNegotiation) {
    jackson {
      registerModule(JavaTimeModule())
      registerKotlinModule()
    }
  }
}
