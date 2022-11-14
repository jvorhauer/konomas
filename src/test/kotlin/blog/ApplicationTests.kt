package blog

import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


// tests that server as general spikes or attempts to prove something...


class ApplicationTests {

  @Test
  fun `check hocon environment substitution`() {
    val e1 = System.getenv("ASTRA_DB_ID")
    assertThat(e1).isEqualTo("e93d3b60-4128-4094-8824-a37327f973c4")

    // One cannot disable the Kotlin String template expansion, so we need ${'$'} to fix this previously failing test:
    val config = ConfigFactory.parseString(
      """test {
      |  dbid = ${'$'}{ASTRA_DB_ID}
      |}""".trimMargin()).resolve()
    val e2 = config.getString("test.dbid")
    assertThat(e2).isEqualTo(e1)
  }
}
