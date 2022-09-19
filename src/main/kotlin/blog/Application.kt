package blog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.support.beans
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

@SpringBootApplication
class Application

fun main() {
    runApplication<Application>() {
        addInitializers(beans)

    }
}

val beans = beans {
    bean<UserRepo>()
    bean<TokenRepo>()
    bean<PostRepo>()
    bean<UserHandler>()
    bean<PostHandler>()
    bean<NoteEventListener>()
    bean { routes(ref(), ref()) }
}

fun routes(userHandler: UserHandler, postHandler: PostHandler) = router {
    "/api".nest {
        POST("/register", userHandler::register)
        POST("/login", userHandler::login)
        GET("/users", userHandler::all)
        GET("/logout", userHandler::logout)
        GET("/user/{id}", userHandler::one)

        POST("/blogs", postHandler::save)
        GET("/blog/{id}", postHandler::one)
    }
}

val UNAUTH = ServerResponse.status(HttpStatus.UNAUTHORIZED).build()
