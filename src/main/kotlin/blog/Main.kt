package blog

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.sentry.Sentry
import blog.config.Konfig
import blog.model.loginRoute
import blog.model.notesRoute
import blog.model.tasksRoute
import blog.model.usersRoute
import blog.module.authentication
import blog.module.content
import blog.module.http
import blog.module.logging
import blog.module.status
import blog.module.validation
import blog.read.Reader
import blog.read.info
import blog.write.Processor

const val pid: String = "28"

object Main {
  private val kfg: Konfig = ConfigFactory.load("application.conf").extract("konomas")

  private val behavior: Behavior<Void> = Behaviors.setup { ctx ->
    embeddedServer(Netty, port = kfg.server.port, host = kfg.server.host) {
      val reader = Reader()
      val processor = ctx.spawn(Processor.create(pid, reader), "konomas")
      val scheduler = ctx.system.scheduler()

      environment.monitor.subscribe(ServerReady) { reader.setServerReady() }

      http()
      content()
      logging()
      authentication(kfg)
      status()
      validation(reader)

      routing {
        usersRoute(processor, reader, scheduler, kfg)
        loginRoute(reader, kfg)
        tasksRoute(processor, reader, scheduler, kfg)
        notesRoute(processor, reader, scheduler, kfg)
        info(reader)
      }
    }.start(wait = true)
    Behaviors.same()
  }

  fun run() {
    Sentry.init {options ->
      options.dsn = System.getenv("KONOMAS_SENTRY_DSN")
      options.environment = "test"
      options.release = "1.0.12"
      options.tracesSampleRate = 1.0
      options.isDebug = false
    }
    ActorSystem.create(behavior, "konomas")
  }
}

fun main() = Main.run()
