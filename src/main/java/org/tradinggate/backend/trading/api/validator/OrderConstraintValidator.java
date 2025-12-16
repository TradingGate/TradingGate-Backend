package org.tradinggate.backend.trading.api.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.global.exception.CustomException;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;

@Component
@RequiredArgsConstructor
public class OrderConstraintValidator implements ConstraintValidator<OrderValid, OrderCreateRequest> {

    private final OrderValidator orderValidator;

    @Override
    public boolean isValid(OrderCreateRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        try {
            // OrderValidator 로직 재사용
            orderValidator.validate(request);
            return true;
        } catch (CustomException e) {
            // CustomException 발생 시, ConstraintValidatorContext에 메시지 설정하고 false 반환
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(e.getMessage()) // 또는 e.getErrorCode().getMessage()
                    .addConstraintViolation();
            return false;
        } catch (Exception e) {
            // 기타 예외 처리
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Order validation failed: " + e.getMessage())
                    .addConstraintViolation();
            return false;
        }
    }
}
