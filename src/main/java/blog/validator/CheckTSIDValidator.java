package blog.validator;

import io.hypersistence.tsid.TSID;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Instant;
import java.util.Objects;

public final class CheckTSIDValidator implements ConstraintValidator<CheckTSID, String> {

  @Override
  public void initialize(final CheckTSID constraintAnnotation) {
    ConstraintValidator.super.initialize(constraintAnnotation);
  }

  @Override
  public boolean isValid(final String value, final ConstraintValidatorContext context) {
    Objects.requireNonNull(context);
    return TSID.isValid(value) && TSID.from(value).getInstant().isBefore(Instant.now());
  }
}
