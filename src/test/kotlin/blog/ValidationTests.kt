package blog

import blog.Constants.born
import blog.model.RegisterUserRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.valiktor.ConstraintViolationException
import org.valiktor.springframework.config.ValiktorConfiguration
import java.util.Locale

class ValidationTests {

  @Test
  fun `valdiation exception handler`() {
    val cfg = ValiktorConfiguration()
    val veh = ValidationExceptionHandler(cfg)
    try {
      RegisterUserRequest("testje", "", "tekort", born)
    } catch (e: ConstraintViolationException) {
      val resp = veh.handle(e, Locale.getDefault())
      assertThat(resp).isNotNull
      assertThat(resp.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
      assertThat(resp.body.errors).hasSize(3)
    }
  }
}
