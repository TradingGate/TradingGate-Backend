package org.tradinggate.backend.trading.api.validator;

import jakarta.validation.Constraint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target; /**
 * [A-1] Trading API - clientOrderId 검증
 *
 * 역할:
 * - clientOrderId 형식/길이 검증
 * - Custom Validation Annotation
 *
 * TODO:
 * [ ] 형식 검증:
 *     - 영숫자 + 하이픈(-) + 언더스코어(_) 허용
 *     - 최소 5자, 최대 64자
 *     - 정규식: ^[a-zA-Z0-9_-]{5,64}$
 *
 * [ ] @ClientOrderIdValid 어노테이션 정의
 *
 * [ ] ConstraintValidator 구현
 *
 * [ ] 중복 체크는 IdempotencyService에서 처리
 *
 * 참고: PDF 1 (trading_order.client_order_id VARCHAR(64))
 */
//@Target({ElementType.FIELD, ElementType.PARAMETER})
//@Retention(RetentionPolicy.RUNTIME)
//@Constraint(validatedBy = ClientOrderIdValidator.class)
//public @interface ClientOrderIdValid {
//  // TODO: 어노테이션 정의
//}
