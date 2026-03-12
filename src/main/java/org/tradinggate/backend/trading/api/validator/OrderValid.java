package org.tradinggate.backend.trading.api.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * [A-1] Trading API - 주문 비즈니스 로직 검증 어노테이션
 *
 * 역할:
 * - OrderCreateRequest 객체 전체 검증
 * - OrderValidator 빈을 통해 검증 위임
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OrderConstraintValidator.class)
public @interface OrderValid {

    String message() default "Invalid order request";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
