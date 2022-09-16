package blog

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import reactor.core.publisher.Mono

@SpringBootTest
class ApplicationTests {

    @Test
    fun contextLoads() {
    }

    @Test
    fun runCatching() {
        monoPathVar("test").subscribe { println("test is $it") }
        monoPathVar("oink").subscribe { println("test is $it") }
    }

    fun thrower(s: String?): String = if (s == "oink") {
        throw java.lang.IllegalArgumentException("s is null")
    } else {
        "Hello, $s"
    }

    fun monoPathVar(s: String): Mono<String> =
        Mono.justOrEmpty(s).mapNotNull { runCatching { thrower(it) }.getOrNull() }
}
