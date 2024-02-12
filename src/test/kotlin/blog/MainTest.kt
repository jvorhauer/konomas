package blog

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import blog.model.Konfig
import blog.module.authentication
import blog.module.content
import blog.route.info
import blog.module.logging
import blog.module.status
import blog.module.validation
import blog.read.Reader
import blog.route.loginRoute
import blog.route.tasksRoute
import blog.route.usersRoute
import blog.write.Processor
import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

object MainTest {

  var ready: Boolean = false

  private val testKit = TestKitJunitResource(
    """akka.persistence.journal.plugin = "akka.persistence.journal.inmem" 
       akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"  
       akka.persistence.snapshot-store.local.dir = "build/snapshot-${UUID.randomUUID()}"  
    """)

  private val kfg: Konfig = ConfigFactory.load("application.conf").extract("jwt")
  private fun behavior(): Behavior<Void> = Behaviors.setup { ctx ->
    embeddedServer(Netty, port = 8181, host = "localhost") {
      val reader = Reader()
      val processor = ctx.spawn(Processor.create(pid, reader), "processor")
      val scheduler = ctx.system.scheduler()

      environment.monitor.subscribe(ServerReady) {
        ready = true
        reader.setServerReady()
      }

      content()
      logging()
      authentication(kfg)
      status()
      validation(reader)

      routing {
        usersRoute(processor, reader, scheduler, kfg)
        loginRoute(reader, kfg)
        authenticate(kfg.realm) {
          get("/api/test") {
            val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized, "no principal")
            val userId: Long? = principal.payload.getClaim("uid").asLong()
            val expireAt = principal.expiresAt?.time?.minus(System.currentTimeMillis())
            var username = "???"
            if (userId != null) {
              username = reader.findUser(userId)?.name ?: "not found"
            }
            call.respond(hashMapOf("id" to userId, "expiresIn" to expireAt, "username" to username))
          }
        }
        info(reader)
        tasksRoute(processor, reader, scheduler, kfg)
      }
    }.start(wait = true)
    Behaviors.same()
  }

  fun run() {
    testKit.spawn(behavior(), "testkonomas")
  }

  fun stop() {
    testKit.system().terminate()
  }
}

class MainTests {
  private val config = ConfigFactory.load("application.conf")

  @Test
  fun testKonfig() {
    val kfg = config.extract<Konfig>("jwt")
    assertThat(kfg).isNotNull
    assertThat(kfg.realm).isEqualTo("konomauth")
    println("kfg: $kfg")
  }
}