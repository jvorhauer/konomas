package blog.validator;

import org.hibernate.validator.cfg.ConstraintDef;

public class CheckTsidDef extends ConstraintDef<CheckTsidDef, CheckTSID> {
  public CheckTsidDef() {
    super(CheckTSID.class);
  }
}
