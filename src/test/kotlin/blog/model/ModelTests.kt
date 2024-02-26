package blog.model

import java.time.LocalDateTime
import java.time.ZonedDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ModelTests {

  @Test
  fun hasher() {
    assertThat("ABC".hashed).isEqualTo("b5d4045c3f466fa91fe2cc6abe79232a1a57cdf104f7a26e716e0a1e2789df78")
    assertThat("".hashed).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
  }

  @Test
  fun gravatar() {
    assertThat("test@example.com".gravatar).isEqualTo("973dfe463ec85785f5f95af5ba3906eedb2d931c24e69824a89ea65dba4e813b")
  }

  @Test
  fun dateTimeFormatters() {
    val zdt = ZonedDateTime.of(2024, 2, 26, 9, 31, 10, 0, CET)
    assertThat(zdt.fmt).isEqualTo("2024-02-26 09:31:10")

    val ldt = LocalDateTime.of(2024, 2, 26, 9, 33, 15)
    assertThat(ldt.fmt).isEqualTo("2024-02-26 09:33:15")
  }

  @Test
  fun slugify() {
    val title = "Hello, World, it's my first slug test"
    assertThat(title.slug).isEqualTo("hello-world-its-my-first-slug-test")
  }

  @Test
  fun equals() {
    val n = Note(nextId(), nextId(), "Title", "Body")
    assertThat(equals(n, n)).isTrue
    assertThat(equals(n, null)).isFalse
    assertThat(equals(n, "not a note")).isFalse

    @Suppress("KotlinConstantConditions")
    assertThat(n == n).isTrue
    assertThat(n.equals(null)).isFalse
    assertThat(n.equals("not a note")).isFalse
    assertThat(n.equals(true)).isFalse

    val o = Note(n.id, nextId(), "Title O", "Body O")
    assertThat(equals(o, o)).isTrue
    assertThat(equals(n, o)).isTrue
    assertThat(equals(o, n)).isTrue

    assertThat(o == n).isTrue
    assertThat(n == o).isTrue
  }

  @Test
  fun nextIds() {
    val tsid = nextTSID()
    tsid.hashCode()

  }
}
