package blog

import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.web.reactive.function.server.ServerRequest
import org.valiktor.ConstraintViolationException
import org.valiktor.i18n.mapToMessage
import org.valiktor.springframework.config.ValiktorConfiguration
import org.valiktor.springframework.http.ValiktorExceptionHandler
import org.valiktor.springframework.http.ValiktorResponse
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.justOrEmpty
import java.util.Locale

data class ValidationError(val errors: Map<String, String>)
private typealias Handler = ValiktorExceptionHandler<ValidationError>

class ValidationExceptionHandler(private val config: ValiktorConfiguration): Handler {
    override fun handle(exception: ConstraintViolationException, locale: Locale) =
        ValiktorResponse(
            statusCode = BAD_REQUEST,
            body = ValidationError(
                exception.constraintViolations
                      .mapToMessage(config.baseBundleName, locale)
                      .associate { it.property to it.message }
            )
        )
}

fun ServerRequest.monoPathVar(s: String): Mono<String> = justOrEmpty(s).mapNotNull { runCatching { this.pathVariable(it) }.getOrNull() }
