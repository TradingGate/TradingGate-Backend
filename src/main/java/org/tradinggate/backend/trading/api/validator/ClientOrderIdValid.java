package org.tradinggate.backend.trading.api.validator;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ClientOrderIdValid.ClientOrderIdValidator.class)
public @interface ClientOrderIdValid {

    String message() default "Invalid clientOrderId format";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class ClientOrderIdValidator implements ConstraintValidator<ClientOrderIdValid, String> {

        // 영숫자 + 하이픈(-) + 언더스코어(_) 허용, 최소 5자, 최대 64자
        private static final String PATTERN = "^[a-zA-Z0-9_-]{5,64}$";

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true; // @NotBlank가 처리
            }
            return value.matches(PATTERN);
        }
    }
}
