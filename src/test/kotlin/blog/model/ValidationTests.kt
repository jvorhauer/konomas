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
    val cn = CreateNoteRequest(nextId().toString(), "Title", "Content")
    assertThat(Validator.validate(cn)).isEmpty()

    assertThat(cn.copy(title = "").validate()).hasSize(1)
    assertThat(cn.copy(body = "").validate()).hasSize(1)
    assertThat(cn.copy(title = " ", body = "  ").validate()).hasSize(2)
  }

  @Test
  fun `create comment request`() {
    val userId = nextId()
    val noteId = nextId()
    val ccr = CreateCommentRequest(userId.toString(), noteId.toString(), null, "tekst", 4)
    assertThat(Validator.validate(ccr)).isEmpty()
    assertThat(ccr.validate()).isEmpty()

    assertThat(ccr.copy(user = "Hello").validate()).hasSize(1)
    assertThat(ccr.copy(score = 6).validate()).hasSize(1)
    assertThat(ccr.copy(text = "  ").validate()).hasSize(1)
    assertThat(ccr.copy(owner = "Goodbye").validate()).hasSize(1)
    assertThat(ccr.copy(user = "1234567890ABC").validate()).hasSize(1)
    assertThat(ccr.copy(user = nextId().toString()).validate()).isEmpty()
  }
}
