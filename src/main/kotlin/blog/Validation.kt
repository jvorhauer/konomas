package blog

import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.valiktor.Constraint
import org.valiktor.ConstraintViolationException
import org.valiktor.Validator
import org.valiktor.i18n.mapToMessage
import org.valiktor.springframework.config.ValiktorConfiguration
import org.valiktor.springframework.http.ValiktorExceptionHandler
import org.valiktor.springframework.http.ValiktorResponse
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.Month
import java.util.Locale

object ValidLocalDate: Constraint
fun <E> Validator<E>.Property<String?>.isLocalDate(): Validator<E>.Property<String?> =
    this.validate(ValidLocalDate) {
        val ld = LocalDate.parse(it)
        it == null ||
              (ld.isBefore(LocalDate.of(2010, Month.JANUARY, 1)) &&
                    ld.isAfter(LocalDate.of(1900, Month.DECEMBER, 31)))
    }

data class ValidationError(
    val errors: Map<String, String>
)

private typealias Handler = ValiktorExceptionHandler<ValidationError>

@Component
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

fun ServerRequest.monoPathVar(s: String): Mono<String> =
    Mono.justOrEmpty(s).mapNotNull { runCatching { this.pathVariable(it) }.getOrNull() }

fun String.isEmail(): Boolean =
    this.matches(Regex("^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$"))
