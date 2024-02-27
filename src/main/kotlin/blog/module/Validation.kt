package blog.module

import blog.model.CreateTaskRequest
import blog.model.Hasher
import blog.model.LoginRequest
import blog.model.RegisterUserRequest
import blog.model.UpdateTaskRequest
import blog.model.now
import blog.read.Reader
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*

val nameRegex: Regex = Regex("^[ A-Za-z'-]+$")
val emailRegex: Regex = Regex("^([\\w-]+(?:\\.[\\w-]+)*)@\\w[\\w.-]+\\.[a-zA-Z]+$")

fun Application.validation(reader: Reader) {
  install(RequestValidation) {
    validate<RegisterUserRequest> {
      when {
        !it.name.matches(nameRegex)   -> ValidationResult.Invalid("invalid name")
        !it.email.matches(emailRegex) -> ValidationResult.Invalid("invalid email address")
        reader.exists(it.email)       -> ValidationResult.Invalid("email address already in use")
        it.password.isBlank()         -> ValidationResult.Invalid("invalid password")
        it.password.length < 7        -> ValidationResult.Invalid("password too short")
        it.password.length > 128      -> ValidationResult.Invalid("password too long")
        else                          -> ValidationResult.Valid
      }
    }
    validate<LoginRequest> {
      when {
        !it.username.matches(emailRegex) -> ValidationResult.Invalid("invalid username")
        it.password.isBlank()            -> ValidationResult.Invalid("invalid password")
        reader.canNotAuthenticate(it.username, Hasher.hash(it.password)) -> ValidationResult.Invalid("username or password not correct")
        else                             -> ValidationResult.Valid
      }
    }
    validate<CreateTaskRequest> {
      when {
        it.title.isBlank()     -> ValidationResult.Invalid("title is blank")
        it.body.isBlank()      -> ValidationResult.Invalid("body is blank")
        it.due.isBefore(now)   -> ValidationResult.Invalid("due can not be in the passed")
        else                   -> ValidationResult.Valid
      }
    }
    validate<UpdateTaskRequest> {
      when {
        reader.notExistsTask(it.id)              -> ValidationResult.Invalid("task not found for ${it.id}")
        it.title != null && it.title.isBlank()   -> ValidationResult.Invalid("title is blank")
        it.body != null && it.body.isBlank()     -> ValidationResult.Invalid("body is blank")
        it.due != null && it.due.isBefore(now)   -> ValidationResult.Invalid("due can not be in the passed")
        else                                     -> ValidationResult.Valid
      }
    }
  }
}
