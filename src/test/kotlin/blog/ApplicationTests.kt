package blog

import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

class ApplicationTests {

    @Test
    fun runCatching() {
        monoPathVar("test").subscribe { println("test is $it") }
        monoPathVar("oink").subscribe { println("test is $it") }
    }

    private fun thrower(s: String?): String = if (s == "oink") {
        throw java.lang.IllegalArgumentException("s is null")
    } else {
        "Hello, $s"
    }

    private fun monoPathVar(s: String): Mono<String> =
        Mono.justOrEmpty(s).mapNotNull { runCatching { thrower(it) }.getOrNull() }
}
