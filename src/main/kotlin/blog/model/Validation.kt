package blog.model

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.hibernate.validator.HibernateValidator
import org.hibernate.validator.cfg.ConstraintMapping
import org.hibernate.validator.cfg.defs.EmailDef
import org.hibernate.validator.cfg.defs.FutureDef
import org.hibernate.validator.cfg.defs.NotBlankDef
import org.hibernate.validator.cfg.defs.PastDef
import org.hibernate.validator.cfg.defs.SizeDef

object Validator {
  private val hconfig = Validation.byProvider(HibernateValidator::class.java).configure()
  private val email = EmailDef().regexp("^([\\w-]+(?:\\.[\\w-]+)*)@\\w[\\w.-]+\\.[a-zA-Z]+$")
  private val constraintMapping: ConstraintMapping = hconfig.createConstraintMapping()
  init {
    constraintMapping
      .type(RegisterUserRequest::class.java)
        .field("email").constraint(email)
        .field("name").constraint(NotBlankDef())
        .field("password").constraint(NotBlankDef()).constraint(SizeDef().min(7).max(64))
        .field("born").constraint(PastDef())
      .type(LoginRequest::class.java)
        .field("username").constraint(email)
        .field("password").constraint(NotBlankDef()).constraint(SizeDef().min(7).max(64))
      .type(CreateNoteRequest::class.java)
        .field("title").constraint(NotBlankDef())
        .field("body").constraint(NotBlankDef())
      .type(UpdateNoteRequest::class.java)
        .field("title").constraint(NotBlankDef())
        .field("body").constraint(NotBlankDef())
      .type(CreateTaskRequest::class.java)
        .field("title").constraint(NotBlankDef())
        .field("body").constraint(NotBlankDef())
        .field("due").constraint(FutureDef())
  }

  val validator: Validator = hconfig.addMapping(constraintMapping).buildValidatorFactory().validator
  fun <T> validate(t: T): Set<ConstraintViolation<T>> = validator.validate(t)
}