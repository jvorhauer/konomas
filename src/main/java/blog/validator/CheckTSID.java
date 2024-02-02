package blog.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE })
@Retention(RUNTIME)
@Constraint(validatedBy = CheckTSIDValidator.class)
@Documented
@Repeatable(CheckTSID.List.class)
public @interface CheckTSID {
  String message() default "{jakarta.validation.constraints.NotBlank.message}";
  Class<?>[] groups() default { };
  Class<? extends Payload>[] payload() default { };

  @Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE })
  @Retention(RUNTIME)
  @Documented
  @interface List {
    CheckTSID[] value();
  }
}
