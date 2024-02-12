package blog

import akka.actor.typed.ActorSystem
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
import blog.route.notesRoute
import blog.route.tasksRoute
import blog.route.usersRoute
import blog.write.Processor
import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.sentry.Sentry

const val pid: String = "27"

object Main {
  private val kfg: Konfig = ConfigFactory.load("application.conf").extract("jwt")

  private val behavior: Behavior<Void> = Behaviors.setup { ctx ->
    embeddedServer(Netty, port = 8080, watchPaths = listOf("classes")) {
      val reader = Reader()
      val processor = ctx.spawn(Processor.create(pid, reader), "konomas")
      val scheduler = ctx.system.scheduler()

      environment.monitor.subscribe(ServerReady) { reader.setServerReady() }

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
      options.release = "1.0.10"
      options.tracesSampleRate = 1.0
      options.isDebug = true
    }
    ActorSystem.create(behavior, "konomas")
  }
}

fun main() = Main.run()
