package blog.route

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.time.Duration

val timeout: Duration = Duration.ofSeconds(10)
fun user(call: ApplicationCall): Long? = call.principal<JWTPrincipal>()?.payload?.getClaim("uid")?.asLong()
