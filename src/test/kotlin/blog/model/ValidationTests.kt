package blog.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ValidationTests {

  @Test
  fun `register user request`() {
    val rur = RegisterUserRequest("bla@test.nl", "Bla Bla Tester", "Welkom123!", LocalDate.now().minusYears(42))
    var errs = Validator.validate(rur)
    assertThat(errs).isEmpty()
    assertThat(rur.validate()).isEmpty()

    errs = Validator.validate(rur.copy(name = ""))
    assertThat(errs).hasSize(1)

    errs = Validator.validate(rur.copy(email = "hallo daar"))
    assertThat(errs).hasSize(1)
  }

  @Test
  fun `login request`() {
    val lr = LoginRequest("bla@test.nl", "Welkom123!")
    var errs = Validator.validator.validate(lr)
    assertThat(errs).isEmpty()

    errs = Validator.validate(lr.copy(username = "hallo daar"))
    assertThat(errs).hasSize(1)

    errs = Validator.validate(lr.copy(password = ""))
    assertThat(errs).hasSize(2)

    errs = Validator.validate(lr.copy(username = "", password = ""))
    assertThat(errs).hasSize(3)
  }

  @Test
  fun `create note request`() {
    val cn = CreateNoteRequest(nextId().toLong(), "Title", "Content")
    assertThat(Validator.validate(cn)).isEmpty()

    assertThat(cn.copy(title = "").validate()).hasSize(1)
    assertThat(cn.copy(body = "").validate()).hasSize(1)
    assertThat(cn.copy(title = " ", body = "  ").validate()).hasSize(2)

    val m = mapOf(1 to "Hallo")
    val n = m.plus(2 to "Bye")
    println("m: $m, n: $n")
  }

  @Test
  fun `simple array copies`() {
    val s1 = Simple()
    val s2 = s1.copy(list = s1.list.plus("Test"))
    val s3 = s2.copy(other = s2.other.plus(5))
    println("s3.list: ${s3.list}, s3.other: ${s3.other}")
  }
}

data class Simple(val list: List<String> = listOf(), val other: List<Int> = listOf())
