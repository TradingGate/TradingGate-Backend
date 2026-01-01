package org.tradinggate.backend.global.aop.redisson;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.global.exception.CustomException;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.tradinggate.backend.global.exception.UserErrorCode.DUPLICATE_REQUEST;

@Log4j2
@Aspect
@Component
@RequiredArgsConstructor
public class RedissonLockAspect {

    private final RedissonClient redissonClient;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

  @Around("@annotation(redissonLock)")
  public Object around(ProceedingJoinPoint joinPoint, RedissonLock redissonLock) throws Throwable {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    String lockKey = parseKey(redissonLock.key(), method, joinPoint.getArgs());

    log.info("🔒 [REDISSON] Attempting lock: {}", lockKey);  // 추가

    RLock lock = redissonClient.getLock(lockKey);
    boolean acquired = false;

    try {
      acquired = lock.tryLock(redissonLock.waitTime(), redissonLock.leaseTime(), TimeUnit.SECONDS);
      log.info("🔒 [REDISSON] Lock acquired: {} = {}", lockKey, acquired);  // 추가

      if (!acquired) {
        log.warn("❌ [REDISSON] Failed to acquire lock: {}", lockKey);  // 추가
        throw new CustomException(DUPLICATE_REQUEST);
      }

      Object result = joinPoint.proceed();
      log.info("✅ [REDISSON] Method executed successfully");  // 추가
      return result;

    } finally {
      if (acquired && lock.isHeldByCurrentThread()) {
        lock.unlock();
        log.info("🔓 [REDISSON] Lock released: {}", lockKey);  // 추가
      }
    }
  }

    private String parseKey(String keyExpression, Method method, Object[] args) {
        EvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = nameDiscoverer.getParameterNames(method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        Expression expression = parser.parseExpression(keyExpression);
        ;
        return expression.getValue(context, String.class);
    }

}
