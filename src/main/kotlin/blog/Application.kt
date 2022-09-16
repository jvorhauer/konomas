package blog

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

@SpringBootApplication
class Application

fun main() {
    runApplication<Application>()
}

@Configuration
class WebConfig(
    @Autowired private val userHandler: UserHandler,
    @Autowired private val postHandler: PostHandler
) {
    @Bean
    fun routes() = router {
        POST("/register", userHandler::register)
        POST("/login", userHandler::login)
        GET("/users", userHandler::all)
        GET("/logout", userHandler::logout)
        GET("/user/{id}", userHandler::one)

        POST("/blogs", postHandler::save)
    }
}

val UNAUTH = ServerResponse.status(HttpStatus.UNAUTHORIZED).build()
